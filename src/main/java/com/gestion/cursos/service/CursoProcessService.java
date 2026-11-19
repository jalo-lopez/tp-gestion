package com.gestion.cursos.service;

import com.gestion.cursos.dto.request.CompletarTareaRequest;
import com.gestion.cursos.dto.request.IniciarCursoRequest;
import com.gestion.cursos.dto.response.EstadoCursoResponse;
import com.gestion.cursos.dto.response.ProcesoIniciadoResponse;
import com.gestion.cursos.dto.response.TareaResponse;
import org.flowable.engine.HistoryService;
import org.flowable.engine.IdentityService;
import org.flowable.engine.ManagementService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.eventsubscription.service.impl.persistence.entity.EventSubscriptionEntity;
import org.flowable.job.api.Job;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio de negocio que encapsula la interacción completa con el motor Flowable BPMN.
 * Implementa las operaciones del ciclo de vida del proceso de cursos, la simulación de
 * comunicaciones empresariales (Sección 4.2.2), las invocaciones automáticas de Service Tasks
 * y las consultas métricas e históricas (Sección 4.3).
 */
@Service
public class CursoProcessService {

    private static final Logger log = LoggerFactory.getLogger(CursoProcessService.class);

    public static final String PROCESS_KEY = "procesoGestionCursos";

    // Toggle en memoria para pruebas del Escenario 7 (Simular falta de aula física)
    private volatile boolean forzarFaltaAulaGlobal = false;

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;
    private final ManagementService managementService;
    private final CursoExternalService cursoExternalService;
    private final IdentityService identityService;

    public CursoProcessService(RuntimeService runtimeService, TaskService taskService,
                               HistoryService historyService, ManagementService managementService,
                               CursoExternalService cursoExternalService,
                               IdentityService identityService) {
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.historyService = historyService;
        this.managementService = managementService;
        this.cursoExternalService = cursoExternalService;
        this.identityService = identityService;
    }

    public CursoExternalService getCursoExternalService() {
        return cursoExternalService;
    }

    public IdentityService getIdentityService() {
        return identityService;
    }

    /**
     * Avanza o dispara inmediatamente cualquier temporizador activo (Timer Job)
     * de la instancia de proceso, facilitando las pruebas automatizadas y demostraciones.
     */
    @Transactional
    public boolean avanzarTemporizador(String processInstanceId) {
        Job timerJob = managementService.createTimerJobQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        if (timerJob != null) {
            log.info("Avanzando manualmente timer job [{}] para instancia [{}]", timerJob.getId(), processInstanceId);
            Job executable = managementService.moveTimerToExecutableJob(timerJob.getId());
            managementService.executeJob(executable.getId());
            return true;
        }
        return false;
    }

    public void setForzarFaltaAulaGlobal(boolean forzar) {
        this.forzarFaltaAulaGlobal = forzar;
        log.warn("Toggle forzarFaltaAulaGlobal configurado a: {}", forzar);
    }

    public boolean isForzarFaltaAulaGlobal() {
        return this.forzarFaltaAulaGlobal;
    }

    // =========================================================================
    // 1. GESTIÓN DE INSTANCIAS Y TAREAS
    // =========================================================================

    @Transactional
    public ProcesoIniciadoResponse iniciarProceso(IniciarCursoRequest request) {
        String businessKey = request.getCursoId();
        Map<String, Object> variables = request.toProcessVariables();

        // Valores iniciales por defecto requeridos por el modelo
        variables.putIfAbsent("recordatoriosEnviados", 0);
        variables.putIfAbsent("timerDurationPropuesta", "P5D");
        variables.putIfAbsent("aprobado", true);
        variables.putIfAbsent("requiereAulaFisica", false);
        variables.putIfAbsent("requiereAulaVirtual", false);
        variables.putIfAbsent("requiereEquipamiento", false);

        log.info("Iniciando proceso '{}' con BusinessKey: {}", PROCESS_KEY, businessKey);

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                PROCESS_KEY,
                businessKey,
                variables
        );

        log.info("Instancia creada con ID: [{}]", processInstance.getId());

        // Persistir en la base de datos institucional propia (Sección 3.2 y 4.1.1)
        cursoExternalService.registrarSolicitudCurso(request, processInstance.getId());

        return new ProcesoIniciadoResponse(
                processInstance.getId(),
                businessKey,
                processInstance.getProcessDefinitionKey(),
                "EN_PROCESO",
                LocalDateTime.now()
        );
    }

    @Transactional(readOnly = true)
    public List<ProcesoIniciadoResponse> obtenerInstanciasActivas() {
        List<ProcessInstance> instances = runtimeService.createProcessInstanceQuery()
                .processDefinitionKey(PROCESS_KEY)
                .active()
                .orderByStartTime().desc()
                .list();

        return instances.stream()
                .map(pi -> new ProcesoIniciadoResponse(
                        pi.getId(),
                        pi.getBusinessKey(),
                        pi.getProcessDefinitionKey(),
                        "EN_PROCESO",
                        LocalDateTime.now()
                ))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TareaResponse> obtenerTareasPorUsuario(String usuario) {
        log.info("Consultando tareas asignadas o candidatas para el usuario: '{}'", usuario);

        // 1. Tareas asignadas directamente al usuario
        List<Task> tareasDirectas = taskService.createTaskQuery()
                .taskAssignee(usuario)
                .active()
                .orderByTaskCreateTime().desc()
                .list();

        // 2. Tareas disponibles para los grupos del usuario
        List<Task> tareasCandidatas = taskService.createTaskQuery()
                .taskCandidateUser(usuario)
                .active()
                .orderByTaskCreateTime().desc()
                .list();

        Set<String> idsProcesados = new HashSet<>();
        List<TareaResponse> resultado = new ArrayList<>();

        for (Task t : tareasDirectas) {
            idsProcesados.add(t.getId());
            resultado.add(mapearATareaResponse(t));
        }

        for (Task t : tareasCandidatas) {
            if (!idsProcesados.contains(t.getId())) {
                idsProcesados.add(t.getId());
                resultado.add(mapearATareaResponse(t));
            }
        }

        return resultado;
    }

    @Transactional(readOnly = true)
    public List<TareaResponse> obtenerTareasPorInstancia(String processInstanceId) {
        List<Task> tasks = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .active()
                .orderByTaskCreateTime().desc()
                .list();

        return tasks.stream()
                .map(this::mapearATareaResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void completarTarea(String taskId, CompletarTareaRequest request) {
        log.info("Completando tarea con ID: {}", taskId);

        Task task = taskService.createTaskQuery()
                .taskId(taskId)
                .active()
                .singleResult();

        if (task == null) {
            throw new IllegalArgumentException("No se encontró una tarea activa con el ID: " + taskId);
        }

        Map<String, Object> variables = request.toVariables();

        // Si se completa 'Registrar y Elaborar propuesta' o 'Revisar propuesta', reiniciar contador de recordatorios
        if ("Registrar y Elaborar propuesta".equals(task.getName()) || "Revisar propuesta".equals(task.getName())) {
            variables.put("recordatoriosEnviados", 0);
            cursoExternalService.guardarPropuesta(
                    task.getProcessInstanceId(),
                    (String) variables.get("programa"),
                    (String) variables.get("presupuesto"),
                    (String) variables.get("fechasPosibles")
            );
        }

        // Si se completa 'Definir Docente que desarrolla el curso', asegurar variable de docente
        if ("Definir Docente que desarrolla el curso".equals(task.getName())) {
            String docente = (String) variables.getOrDefault("docenteAsignado", "docente_carlos");
            variables.put("docenteAsignado", docente);
            // Si no se definió fecha de inicio previa, fijar una por defecto para no bloquear el timer
            if (runtimeService.getVariable(task.getExecutionId(), "fechaInicioCurso") == null) {
                variables.put("fechaInicioCurso", LocalDateTime.now().plusSeconds(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            }
            cursoExternalService.actualizarEstadoCurso(task.getProcessInstanceId(), "confirmado y pendiente de inicio", null);
        }

        // Si se completa 'Desarrollar curso'
        if ("Desarrollar curso".equals(task.getName())) {
            cursoExternalService.actualizarEstadoCurso(task.getProcessInstanceId(), "en desarrollo", null);
        }

        // Si se completa 'Elaborar informe de resultados'
        if ("Elaborar informe de resultados".equals(task.getName())) {
            String informe = (String) variables.get("informeResultados");
            if (informe == null && request.getObservaciones() != null) {
                informe = request.getObservaciones();
            }
            cursoExternalService.guardarInformeResultados(task.getProcessInstanceId(), informe);
        }

        // Si se completa 'Evaluar Recursos', asegurar que los 3 booleanos estén definidos
        if ("Evaluar Recursos".equals(task.getName())) {
            variables.putIfAbsent("requiereAulaFisica", false);
            variables.putIfAbsent("requiereAulaVirtual", false);
            variables.putIfAbsent("requiereEquipamiento", false);
        }

        // Si se completa 'Enviar informe a empresa y Archivar expediente.', fijar resultado normal
        if ("Enviar informe a empresa y Archivar expediente.".equals(task.getName())) {
            variables.put("resultado", "finalizado");
            cursoExternalService.actualizarEstadoCurso(task.getProcessInstanceId(), "finalizado", "Expediente archivado");
        }

        taskService.complete(taskId, variables);
        log.info("Tarea [{}] completada exitosamente.", taskId);
    }

    @Transactional
    public void reclamarTarea(String taskId, String usuario) {
        log.info("Reclamando tarea [{}] por usuario [{}]", taskId, usuario);
        Task task = taskService.createTaskQuery().taskId(taskId).active().singleResult();
        if (task == null) {
            throw new IllegalArgumentException("No se encontró una tarea activa con el ID: " + taskId);
        }
        taskService.claim(taskId, usuario);
    }

    // =========================================================================
    // 2. SIMULACIÓN DE COMUNICACIONES DE LA EMPRESA (Sección 4.2.2)
    // =========================================================================

    /**
     * Consulta qué comunicaciones empresariales están habilitadas actualmente
     * según las suscripciones de mensajes activas en Flowable.
     */
    @Transactional(readOnly = true)
    public List<String> obtenerComunicacionesHabilitadas(String processInstanceId) {
        List<Execution> executions = runtimeService.createExecutionQuery()
                .processInstanceId(processInstanceId)
                .list();

        List<String> habilitadas = new ArrayList<>();
        for (Execution ex : executions) {
            List<EventSubscriptionEntity> subs =
                    runtimeService.createEventSubscriptionQuery()
                            .executionId(ex.getId())
                            .eventType("message")
                            .list()
                            .stream()
                            .map(s -> (EventSubscriptionEntity) s)
                            .toList();

            for (EventSubscriptionEntity sub : subs) {
                switch (sub.getEventName()) {
                    case "msgAceptacion" -> habilitadas.add("ACEPTAR_PROPUESTA");
                    case "msgRechazo" -> habilitadas.add("RECHAZAR_PROPUESTA");
                    case "msgModificaciones" -> habilitadas.add("SOLICITAR_MODIFICACIONES");
                    case "msgRecibirNomina" -> habilitadas.add("ENVIAR_NOMINA");
                    case "msgRecibirCancelacion" -> habilitadas.add("CANCELAR_PREPARACION");
                }
            }
        }
        return habilitadas.stream().distinct().collect(Collectors.toList());
    }

    @Transactional
    public void aceptarPropuesta(String processInstanceId, String fechaAcordada) {
        log.info("Empresa acepta propuesta para instancia [{}] con fecha acordada [{}]", processInstanceId, fechaAcordada);
        Execution exec = buscarEjecucionEsperandoMensaje(processInstanceId, "msgAceptacion");
        Map<String, Object> vars = new HashMap<>();
        String fecha = fechaAcordada != null ? fechaAcordada : LocalDateTime.now().plusDays(10).toString();
        vars.put("fechaInicioCurso", fecha);
        vars.put("decisionEmpresa", "ACEPTADA");
        cursoExternalService.registrarAceptacion(processInstanceId, fecha);
        runtimeService.messageEventReceived("msgAceptacion", exec.getId(), vars);
    }

    @Transactional
    public void rechazarPropuesta(String processInstanceId, String motivo) {
        log.info("Empresa rechaza propuesta para instancia [{}] con motivo: {}", processInstanceId, motivo);
        Execution exec = buscarEjecucionEsperandoMensaje(processInstanceId, "msgRechazo");
        Map<String, Object> vars = new HashMap<>();
        vars.put("motivoRechazo", motivo != null ? motivo : "Rechazado por decisión comercial");
        vars.put("resultado", "rechazado");
        vars.put("decisionEmpresa", "RECHAZADA");
        cursoExternalService.actualizarEstadoCurso(processInstanceId, "rechazado", motivo);
        runtimeService.messageEventReceived("msgRechazo", exec.getId(), vars);
    }

    @Transactional
    public void solicitarModificaciones(String processInstanceId, String observaciones) {
        log.info("Empresa solicita modificaciones para instancia [{}]: {}", processInstanceId, observaciones);
        Execution exec = buscarEjecucionEsperandoMensaje(processInstanceId, "msgModificaciones");
        Map<String, Object> vars = new HashMap<>();
        vars.put("observacionesModificaciones", observaciones != null ? observaciones : "Ajuste de presupuesto y contenidos");
        vars.put("decisionEmpresa", "MODIFICACIONES");
        vars.put("recordatoriosEnviados", 0);
        runtimeService.messageEventReceived("msgModificaciones", exec.getId(), vars);
    }

    @Transactional
    public void enviarNomina(String processInstanceId, List<String> nomina) {
        log.info("Empresa envía nómina de [{}] participantes para instancia [{}]", nomina != null ? nomina.size() : 0, processInstanceId);
        Execution exec = buscarEjecucionEsperandoMensaje(processInstanceId, "msgRecibirNomina");
        Map<String, Object> vars = new HashMap<>();
        vars.put("nominaParticipantes", nomina != null ? nomina : List.of("Juan Pérez", "María García", "Lucas Romano"));
        vars.put("nominaEnviada", true);
        runtimeService.messageEventReceived("msgRecibirNomina", exec.getId(), vars);
    }

    @Transactional
    public void cancelarPreparacion(String processInstanceId, String motivo) {
        log.info("Empresa solicita cancelación durante preparación para instancia [{}]: {}", processInstanceId, motivo);
        Execution exec = buscarEjecucionEsperandoMensaje(processInstanceId, "msgRecibirCancelacion");
        Map<String, Object> vars = new HashMap<>();
        vars.put("motivoCancelacion", motivo != null ? motivo : "Cancelación solicitada por la empresa.");
        vars.put("resultado", "cancelado");
        cursoExternalService.actualizarEstadoCurso(processInstanceId, "cancelado", motivo);
        runtimeService.messageEventReceived("msgRecibirCancelacion", exec.getId(), vars);
    }

    private Execution buscarEjecucionEsperandoMensaje(String processInstanceId, String messageName) {
        Execution execution = runtimeService.createExecutionQuery()
                .processInstanceId(processInstanceId)
                .messageEventSubscriptionName(messageName)
                .singleResult();

        if (execution == null) {
            throw new IllegalStateException("La instancia [" + processInstanceId + "] no se encuentra en un estado que admita la acción '" + messageName + "'.");
        }
        return execution;
    }

    // =========================================================================
    // 3. MÉTODOS DE SERVICE TASKS DEL PROCESO BPMN
    // =========================================================================

    public void notificarNoViable(DelegateExecution execution) {
        log.info(">>> ServiceTask: Notificar Decisión (No Viable) para curso [{}] <<<", execution.getProcessInstanceBusinessKey());
        execution.setVariable("resultado", "no viable");
        execution.setVariable("motivoNoViable", "Límite institucional de 5 cursos concurrentes alcanzado.");
    }

    public void enviarPropuesta(DelegateExecution execution) {
        log.info(">>> ServiceTask: Enviar propuesta a la empresa solicitante para curso [{}] <<<", execution.getProcessInstanceBusinessKey());
        execution.setVariable("propuestaEnviada", true);
        execution.setVariable("recordatoriosEnviados", 0);
    }

    public void enviarRecordatorio(DelegateExecution execution) {
        Integer recordatorios = (Integer) execution.getVariable("recordatoriosEnviados");
        if (recordatorios == null) recordatorios = 0;
        recordatorios++;
        execution.setVariable("recordatoriosEnviados", recordatorios);
        log.info(">>> ServiceTask: Recordatorio #{} enviado a la empresa para el curso [{}] <<<",
                recordatorios, execution.getProcessInstanceBusinessKey());
    }

    public void habilitarAccesoEmpresa(DelegateExecution execution) {
        String email = (String) execution.getVariable("emailSolicitante");
        log.info(">>> ServiceTask: Habilitar acceso de la empresa ({}) para curso [{}] <<<", email, execution.getProcessInstanceBusinessKey());
        execution.setVariable("accesoEmpresaHabilitado", true);
        cursoExternalService.habilitarAccesoEmpresa(email, execution.getProcessInstanceId());
        asegurarUsuarioFlowableEmpresa(email, (String) execution.getVariable("nombreEmpresa"));
    }

    public void asegurarUsuarioFlowableEmpresa(String email, String nombreEmpresa) {
        if (email == null || email.isBlank()) return;
        try {
            org.flowable.idm.api.User user = identityService.createUserQuery().userId(email).singleResult();
            if (user == null) {
                user = identityService.newUser(email);
                user.setFirstName(nombreEmpresa != null && !nombreEmpresa.isBlank() ? nombreEmpresa : "Empresa");
                user.setLastName("");
                user.setEmail(email);
                user.setPassword("empresa123");
                identityService.saveUser(user);
                log.info("Usuario de Flowable creado para empresa: {}", email);
            }
            org.flowable.idm.api.Group group = identityService.createGroupQuery().groupId("Empresas").groupMember(email).singleResult();
            if (group == null) {
                identityService.createMembership(email, "Empresas");
            }
        } catch (Exception e) {
            log.warn("No se pudo registrar empresa en Flowable IdentityService: {}", e.getMessage());
        }
    }

    public void reservarAula(DelegateExecution execution) {
        String cursoId = execution.getProcessInstanceBusinessKey();
        log.info(">>> ServiceTask: Conectando con Web Service externo para reservar aula física para curso [{}] <<<", cursoId);

        // Verificación de forzado de error (Escenario 7)
        Boolean forzarLocal = (Boolean) execution.getVariable("forzarErrorAula");
        if (Boolean.TRUE.equals(forzarLocal) || this.forzarFaltaAulaGlobal) {
            log.error("Fallo simulado de Web Service: ¡No hay aulas físicas disponibles para el curso [{}]!", cursoId);
            throw new RuntimeException("ERROR_AULA_NO_DISPONIBLE: No existen aulas físicas disponibles para la fecha requerida.");
        }

        execution.setVariable("aulaFisicaReservada", true);
        execution.setVariable("idAulaFisica", "AULA-MAGNA-BPM");
        log.info("Aula física [AULA-MAGNA-BPM] reservada exitosamente vía Web Service.");
    }

    public void liberarAula(DelegateExecution execution) {
        log.info(">>> Compensación: Invocando Web Service externo para LIBERAR aula física para curso [{}] <<<",
                execution.getProcessInstanceBusinessKey());
        execution.setVariable("aulaFisicaReservada", false);
    }

    public void liberarAulaVirtual(DelegateExecution execution) {
        log.info(">>> Compensación: Desactivando aula virtual para curso [{}] <<<", execution.getProcessInstanceBusinessKey());
        execution.setVariable("aulaVirtualReservada", false);
    }

    public void notificarCierre(DelegateExecution execution) {
        log.info(">>> ServiceTask: Notificar cierre a la empresa por cancelación del curso [{}] <<<",
                execution.getProcessInstanceBusinessKey());
        execution.setVariable("resultado", "cancelado");
    }

    public void enviarConfirmacion(DelegateExecution execution) {
        log.info(">>> ServiceTask: Preparación concluida. Enviar confirmación a la empresa solicitante [{}] <<<",
                execution.getProcessInstanceBusinessKey());
        execution.setVariable("confirmacionEnviada", true);
    }

    // 4. Login de usuarios con Flowable:

    public Map<String, Object> autenticarUsuario(String username, String password) {
        if (username == null || password == null) {
            throw new IllegalArgumentException("Usuario y contraseña requeridos");
        }
        String cleanUser = username.trim();

        // 1. Intentar autenticar con Flowable
        org.flowable.idm.api.User user = identityService.createUserQuery().userId(cleanUser).singleResult();
        if (user == null) {
            // Intentar buscar por email en Flowable
            user = identityService.createUserQuery().userEmail(cleanUser).singleResult();
        }

        boolean flowableAuth = false;
        if (user != null) {
            flowableAuth = identityService.checkPassword(user.getId(), password);
        }

        // 2. Si no se encontró en Flowable,
        // verificar en la tabla empresas de la BD
        Map<String, Object> empresaBd = null;
        try {
            String sql = "SELECT * FROM empresas WHERE (LOWER(email) = LOWER(?) OR LOWER(nombre) = LOWER(?)) AND password = ?";
            List<Map<String, Object>> rows = cursoExternalService.getJdbcTemplate().queryForList(sql, cleanUser, cleanUser, password);
            if (!rows.isEmpty()) {
                empresaBd = cursoExternalService.normalizarClavesMinusculas(rows.get(0));
            }
        } catch (Exception e) {
            log.debug("Consulta de autenticación contra empresas: {}", e.getMessage());
        }

        if (!flowableAuth && empresaBd == null) {
            throw new SecurityException("Credenciales inválidas en Flowable / Institución");
        }

        // Si autenticó en BD pero no estaba en Flowable, sincronizarlo con Flowable
        if (empresaBd != null && user == null) {
            String email = (String) empresaBd.get("email");
            String nombre = (String) empresaBd.get("nombre");
            asegurarUsuarioFlowableEmpresa(email, nombre);
            user = identityService.createUserQuery().userId(email).singleResult();
        }

        Map<String, Object> perfil = new LinkedHashMap<>();
        if (user != null) {
            perfil.put("userId", user.getId());
            perfil.put("nombre", (user.getFirstName() != null ? user.getFirstName() : "") +
                    (user.getLastName() != null && !user.getLastName().isBlank() ? " " + user.getLastName() : ""));
            perfil.put("email", user.getEmail());

            List<org.flowable.idm.api.Group> groups = identityService.createGroupQuery().groupMember(user.getId()).list();
            List<String> groupIds = groups.stream().map(org.flowable.idm.api.Group::getId).toList();
            perfil.put("grupos", groupIds);

            if (groupIds.contains("Empresas") || user.getId().contains("@") || empresaBd != null) {
                perfil.put("tipo", "EMPRESA");
            } else if (groupIds.contains("Administración")) {
                perfil.put("tipo", "ADMINISTRACION");
            } else if (groupIds.contains("Docentes")) {
                perfil.put("tipo", "DOCENTES");
            } else {
                perfil.put("tipo", "INSTITUCIONAL");
            }
        } else if (empresaBd != null) {
            perfil.put("userId", empresaBd.get("email"));
            perfil.put("nombre", empresaBd.get("nombre"));
            perfil.put("email", empresaBd.get("email"));
            perfil.put("tipo", "EMPRESA");
            perfil.put("grupos", List.of("Empresas"));
        }

        if (empresaBd != null) {
            perfil.put("empresaId", empresaBd.get("id"));
            perfil.put("empresaNombre", empresaBd.get("nombre"));
            perfil.put("contacto", empresaBd.get("contacto"));
        } else if ("EMPRESA".equals(perfil.get("tipo"))) {
            Map<String, Object> emp = cursoExternalService.obtenerEmpresaPorEmail((String) perfil.get("email"));
            if (emp != null) {
                perfil.put("empresaId", emp.get("id"));
                perfil.put("empresaNombre", emp.get("nombre"));
                perfil.put("contacto", emp.get("contacto"));
            }
        }

        log.info("Usuario [{}] autenticado exitosamente como [{}]", cleanUser, perfil.get("tipo"));
        return perfil;
    }

    // =========================================================================
    // 5. DETALLE DE LAS 3 RAMAS PARALELAS (Sección 1.2 y 4.1.2)
    // =========================================================================

    @Transactional(readOnly = true)
    public Map<String, Object> obtenerDetalleRamasPreparacion(String identificador) {
        // Puede venir por cursoId o por processInstanceId
        ProcessInstance active = runtimeService.createProcessInstanceQuery()
                .processInstanceBusinessKey(identificador).singleResult();
        if (active == null) {
            active = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(identificador).singleResult();
        }

        String processInstanceId = active != null ? active.getId() : null;
        HistoricProcessInstance historic = null;
        if (processInstanceId == null) {
            historic = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceBusinessKey(identificador).singleResult();
            if (historic == null) {
                historic = historyService.createHistoricProcessInstanceQuery()
                        .processInstanceId(identificador).singleResult();
            }
            if (historic != null) {
                processInstanceId = historic.getId();
            }
        }

        if (processInstanceId == null) {
            throw new IllegalArgumentException("No se encontró ningún curso o proceso con el identificador: " + identificador);
        }

        // Obtener variables de runtime o de histórico
        Map<String, Object> vars = new HashMap<>();
        if (active != null) {
            vars.putAll(runtimeService.getVariables(processInstanceId));
        } else {
            historyService.createHistoricVariableInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .list()
                    .forEach(v -> vars.put(v.getVariableName(), v.getValue()));
        }

        // Tareas activas
        List<Task> tareasActivas = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .active()
                .list();

        // Subscripciones de eventos activas (mensajes)
        List<String> mensajesEsperados = active != null ? runtimeService.createEventSubscriptionQuery()
                .processInstanceId(processInstanceId)
                .eventType("message")
                .list()
                .stream()
                .map(sub -> (EventSubscriptionEntity) sub)
                .map(EventSubscriptionEntity::getEventName)
                .toList() : Collections.emptyList();

        // Actividades históricas finalizadas
        Set<String> actividadesFinalizadas = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .finished()
                .list()
                .stream()
                .map(HistoricActivityInstance::getActivityId)
                .collect(Collectors.toSet());

        // Comprobar estado del subproceso "subProcessPrepararCurso"
        boolean subprocesoIniciado = actividadesFinalizadas.contains("startSubProcess")
                || actividadesFinalizadas.contains("forkPreparacion")
                || tareasActivas.stream().anyMatch(t -> List.of("Preparar materiales", "Evaluar Recursos", "Confeccionar lista definitiva", "Solicitar Aula Virtual", "Contratar equipamiento").contains(t.getName()))
                || mensajesEsperados.contains("msgRecibirNomina");

        boolean subprocesoFinalizado = actividadesFinalizadas.contains("subProcessPrepararCurso")
                || actividadesFinalizadas.contains("joinPreparacion")
                || actividadesFinalizadas.contains("endSubProcess")
                || actividadesFinalizadas.contains("enviarConfirmacionTask")
                || actividadesFinalizadas.contains("definirDocenteTask")
                || actividadesFinalizadas.contains("desarrollarCursoTask");

        String resultado = (String) vars.get("resultado");
        boolean cancelado = "cancelado".equalsIgnoreCase(resultado) || actividadesFinalizadas.contains("throwCompensacionCancelacion");

        // -------------------------------------------------------------
        // RAMA 1: INSCRIPCIONES
        // -------------------------------------------------------------
        Map<String, Object> ramaInscripciones = new LinkedHashMap<>();
        ramaInscripciones.put("nombre", "Inscripciones y Nómina");
        boolean listaDefinitivaLista = actividadesFinalizadas.contains("confeccionarListaDefinitivaTask");
        Task taskLista = tareasActivas.stream().filter(t -> "confeccionarListaDefinitivaTask".equals(t.getTaskDefinitionKey()) || "Confeccionar lista definitiva".equals(t.getName())).findFirst().orElse(null);
        boolean esperandoNomina = mensajesEsperados.contains("msgRecibirNomina");
        boolean nominaEnviada = Boolean.TRUE.equals(vars.get("nominaEnviada")) || (!esperandoNomina && (taskLista != null || listaDefinitivaLista));

        ramaInscripciones.put("nominaRecibida", nominaEnviada);
        Object nominaObj = vars.get("nominaParticipantes");
        if (nominaObj instanceof List<?> list) {
            ramaInscripciones.put("totalParticipantes", list.size());
            ramaInscripciones.put("participantes", list);
        } else {
            ramaInscripciones.put("totalParticipantes", vars.getOrDefault("cupoMaximo", 20));
        }

        if (listaDefinitivaLista || subprocesoFinalizado) {
            ramaInscripciones.put("estado", "COMPLETADA");
            ramaInscripciones.put("actividadActual", "Lista definitiva confeccionada");
            ramaInscripciones.put("detalle", "Nómina de participantes procesada y lista definitiva confeccionada.");
        } else if (taskLista != null) {
            ramaInscripciones.put("estado", "EN_EJECUCION");
            ramaInscripciones.put("actividadActual", "Confeccionar lista definitiva");
            ramaInscripciones.put("asignado", taskLista.getAssignee() != null ? taskLista.getAssignee() : "Administración (Asignado por balanceo)");
            ramaInscripciones.put("detalle", "Nómina recibida. Tarea asignada a: " + (taskLista.getAssignee() != null ? taskLista.getAssignee() : "Administración"));
        } else if (esperandoNomina) {
            ramaInscripciones.put("estado", "PENDIENTE");
            ramaInscripciones.put("actividadActual", "Esperando nómina de participantes");
            ramaInscripciones.put("detalle", "Pendiente de que la empresa envíe la nómina de empleados a capacitar.");
        } else if (subprocesoIniciado) {
            ramaInscripciones.put("estado", "EN_EJECUCION");
            ramaInscripciones.put("actividadActual", "En proceso de inscripción");
            ramaInscripciones.put("detalle", "Recepción de nómina y confección en curso.");
        } else {
            ramaInscripciones.put("estado", "NO_INICIADA");
            ramaInscripciones.put("actividadActual", "No iniciada");
            ramaInscripciones.put("detalle", "Esta rama se activará cuando la propuesta sea acordada.");
        }

        // -------------------------------------------------------------
        // RAMA 2: MATERIALES
        // -------------------------------------------------------------
        Map<String, Object> ramaMateriales = new LinkedHashMap<>();
        ramaMateriales.put("nombre", "Materiales Didácticos");
        boolean materialesListos = actividadesFinalizadas.contains("prepararMaterialesTask") || Boolean.TRUE.equals(vars.get("materialesPreparados"));
        Task taskMateriales = tareasActivas.stream().filter(t -> "prepararMaterialesTask".equals(t.getTaskDefinitionKey()) || "Preparar materiales".equals(t.getName())).findFirst().orElse(null);

        if (materialesListos || subprocesoFinalizado) {
            ramaMateriales.put("estado", "COMPLETADA");
            ramaMateriales.put("actividadActual", "Materiales didácticos preparados");
            ramaMateriales.put("responsable", vars.getOrDefault("responsableMateriales", "Equipo de Administración"));
            ramaMateriales.put("observaciones", vars.getOrDefault("observacionesMateriales", "Diapositivas y material impreso listos"));
            ramaMateriales.put("detalle", "Los materiales didácticos han sido elaborados y preparados satisfactoriamente.");
        } else if (taskMateriales != null) {
            ramaMateriales.put("estado", "EN_EJECUCION");
            ramaMateriales.put("actividadActual", "Preparar materiales");
            ramaMateriales.put("grupoCandidato", "Administración");
            ramaMateriales.put("asignado", taskMateriales.getAssignee() != null ? taskMateriales.getAssignee() : "Pendiente de reclamo por Administración");
            ramaMateriales.put("detalle", "Tarea abierta para el grupo Administración (App Auxiliar Java o Portal Web).");
        } else if (subprocesoIniciado) {
            ramaMateriales.put("estado", "PENDIENTE");
            ramaMateriales.put("actividadActual", "Pendiente de preparación");
            ramaMateriales.put("detalle", "En cola de preparación de materiales.");
        } else {
            ramaMateriales.put("estado", "NO_INICIADA");
            ramaMateriales.put("actividadActual", "No iniciada");
            ramaMateriales.put("detalle", "Esta rama se activará cuando la propuesta sea acordada.");
        }

        // -------------------------------------------------------------
        // RAMA 3: RECURSOS
        // -------------------------------------------------------------
        Map<String, Object> ramaRecursos = new LinkedHashMap<>();
        ramaRecursos.put("nombre", "Recursos y Espacios");
        boolean evaluacionCompletada = actividadesFinalizadas.contains("evaluarRecursosTask");
        Task taskEvaluar = tareasActivas.stream().filter(t -> "evaluarRecursosTask".equals(t.getTaskDefinitionKey()) || "Evaluar Recursos".equals(t.getName())).findFirst().orElse(null);

        boolean reqFisica = Boolean.TRUE.equals(vars.get("requiereAulaFisica"));
        boolean reqVirtual = Boolean.TRUE.equals(vars.get("requiereAulaVirtual"));
        boolean reqEquip = Boolean.TRUE.equals(vars.get("requiereEquipamiento"));

        Map<String, Object> subRecursos = new LinkedHashMap<>();

        // Aula Física
        Map<String, Object> subFisica = new LinkedHashMap<>();
        subFisica.put("requerida", reqFisica);
        if (!reqFisica) {
            subFisica.put("estado", "NO_REQUERIDA");
            subFisica.put("detalle", "Modalidad 100% online o sin espacio presencial");
        } else if (Boolean.TRUE.equals(vars.get("aulaFisicaReservada")) || actividadesFinalizadas.contains("reservarAulaTask") || subprocesoFinalizado) {
            subFisica.put("estado", "RESERVADA");
            subFisica.put("detalle", "Aula física confirmada: " + vars.getOrDefault("idAulaFisica", "AULA-MAGNA-BPM"));
        } else {
            subFisica.put("estado", "EN_GESTION");
            subFisica.put("detalle", "Conectando con Web Service para reserva de aula");
        }
        subRecursos.put("aulaFisica", subFisica);

        // Aula Virtual
        Map<String, Object> subVirtual = new LinkedHashMap<>();
        subVirtual.put("requerida", reqVirtual);
        boolean taskVirtualActive = tareasActivas.stream().anyMatch(t -> "solicitarAulaVirtualTask".equals(t.getTaskDefinitionKey()) || "Solicitar Aula Virtual".equals(t.getName()));
        boolean virtualLista = actividadesFinalizadas.contains("solicitarAulaVirtualTask") || Boolean.TRUE.equals(vars.get("aulaVirtualReservada"));
        if (!reqVirtual) {
            subVirtual.put("estado", "NO_REQUERIDA");
            subVirtual.put("detalle", "No requiere aula virtual");
        } else if (virtualLista || subprocesoFinalizado) {
            subVirtual.put("estado", "HABILITADA");
            subVirtual.put("detalle", "Aula virtual habilitada en plataforma de aprendizaje");
        } else if (taskVirtualActive) {
            subVirtual.put("estado", "EN_GESTION");
            subVirtual.put("detalle", "Tarea 'Solicitar Aula Virtual' pendiente en Administración");
        } else {
            subVirtual.put("estado", "PENDIENTE");
            subVirtual.put("detalle", "Pendiente de habilitación");
        }
        subRecursos.put("aulaVirtual", subVirtual);

        // Equipamiento
        Map<String, Object> subEquip = new LinkedHashMap<>();
        subEquip.put("requerido", reqEquip);
        boolean taskEquipActive = tareasActivas.stream().anyMatch(t -> "contratarEquipamientoTask".equals(t.getTaskDefinitionKey()) || "Contratar equipamiento".equals(t.getName()));
        boolean equipListo = actividadesFinalizadas.contains("contratarEquipamientoTask") || Boolean.TRUE.equals(vars.get("equipamientoContratado"));
        if (!reqEquip) {
            subEquip.put("estado", "NO_REQUERIDO");
            subEquip.put("detalle", "No requiere equipamiento especial");
        } else if (equipListo || subprocesoFinalizado) {
            subEquip.put("estado", "CONTRATADO");
            subEquip.put("detalle", "Equipamiento especial contratado y asignado");
        } else if (taskEquipActive) {
            subEquip.put("estado", "EN_GESTION");
            subEquip.put("detalle", "Tarea 'Contratar equipamiento' pendiente en Administración");
        } else {
            subEquip.put("estado", "PENDIENTE");
            subEquip.put("detalle", "Pendiente de contratación");
        }
        subRecursos.put("equipamiento", subEquip);

        ramaRecursos.put("recursos", subRecursos);

        if (subprocesoFinalizado || (evaluacionCompletada && (!reqFisica || "RESERVADA".equals(subFisica.get("estado")))
                && (!reqVirtual || "HABILITADA".equals(subVirtual.get("estado")))
                && (!reqEquip || "CONTRATADO".equals(subEquip.get("estado"))))) {
            ramaRecursos.put("estado", "COMPLETADA");
            ramaRecursos.put("actividadActual", "Recursos asignados y confirmados");
            ramaRecursos.put("detalle", "Todos los recursos seleccionados han sido gestionados exitosamente.");
        } else if (taskEvaluar != null) {
            ramaRecursos.put("estado", "EN_EVALUACION");
            ramaRecursos.put("actividadActual", "Evaluar Recursos");
            ramaRecursos.put("grupoCandidato", "Administración");
            ramaRecursos.put("detalle", "El equipo de administración debe evaluar las necesidades (aula física, virtual o equipamiento).");
        } else if (evaluacionCompletada) {
            ramaRecursos.put("estado", "EN_GESTION");
            ramaRecursos.put("actividadActual", "Gestión de recursos seleccionados");
            ramaRecursos.put("detalle", "Completando las solicitudes de los recursos seleccionados.");
        } else if (subprocesoIniciado) {
            ramaRecursos.put("estado", "PENDIENTE");
            ramaRecursos.put("actividadActual", "Pendiente de evaluación");
            ramaRecursos.put("detalle", "Pendiente de inicio de evaluación de recursos.");
        } else {
            ramaRecursos.put("estado", "NO_INICIADA");
            ramaRecursos.put("actividadActual", "No iniciada");
            ramaRecursos.put("detalle", "Esta rama se activará cuando la propuesta sea acordada.");
        }

        // Resumen
        Map<String, Object> resumen = new LinkedHashMap<>();
        resumen.put("processInstanceId", processInstanceId);
        resumen.put("cursoId", vars.getOrDefault("cursoId", identificador));
        resumen.put("nombreCurso", vars.getOrDefault("nombreCurso", "Capacitación Empresarial"));
        resumen.put("subprocesoIniciado", subprocesoIniciado);
        resumen.put("subprocesoFinalizado", subprocesoFinalizado);
        resumen.put("cancelado", cancelado);
        resumen.put("ramaInscripciones", ramaInscripciones);
        resumen.put("ramaMateriales", ramaMateriales);
        resumen.put("ramaRecursos", ramaRecursos);

        return resumen;
    }

    // =========================================================================
    // 6. MÉTRICAS HISTÓRICAS (Sección 4.3)
    // =========================================================================

    /**
     * Métrica 1: Cantidad y porcentaje de instancias finalizadas por cada resultado posible
     * (finalizado normalmente, cancelado, rechazado, cerrado por falta de respuesta, no viable).
     */
    @Transactional(readOnly = true)
    public Map<String, Object> obtenerMetricasResultados() {
        List<HistoricProcessInstance> instancias = historyService.createHistoricProcessInstanceQuery()
                .processDefinitionKey(PROCESS_KEY)
                .finished()
                .includeProcessVariables()
                .list();

        long total = instancias.size();
        Map<String, Long> conteo = new LinkedHashMap<>();
        conteo.put("finalizado", 0L);
        conteo.put("cancelado", 0L);
        conteo.put("rechazado", 0L);
        conteo.put("cerrado por falta de respuesta", 0L);
        conteo.put("no viable", 0L);

        for (HistoricProcessInstance pi : instancias) {
            Map<String, Object> vars = pi.getProcessVariables();
            String res = (String) vars.get("resultado");
            if (res != null) {
                String key = res.toLowerCase().trim();
                conteo.put(key, conteo.getOrDefault(key, 0L) + 1);
            } else {
                conteo.put("finalizado", conteo.get("finalizado") + 1);
            }
        }

        List<Map<String, Object>> filas = new ArrayList<>();
        for (Map.Entry<String, Long> entry : conteo.entrySet()) {
            Map<String, Object> fila = new HashMap<>();
            fila.put("resultado", entry.getKey());
            fila.put("cantidad", entry.getValue());
            double pct = total > 0 ? (entry.getValue() * 100.0) / total : 0.0;
            fila.put("porcentaje", Math.round(pct * 100.0) / 100.0);
            filas.add(fila);
        }

        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("totalInstanciasFinalizadas", total);
        respuesta.put("distribucion", filas);
        return respuesta;
    }

    /**
     * Métrica 2: Duración promedio de las 3 ramas paralelas de preparación
     * (inscripciones, materiales, recursos) excluyendo las canceladas.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> obtenerMetricasDuracionRamas() {
        // Obtenemos instancias finalizadas normalmente que NO fueron canceladas
        List<HistoricProcessInstance> instanciasValidas = historyService.createHistoricProcessInstanceQuery()
                .processDefinitionKey(PROCESS_KEY)
                .finished()
                .includeProcessVariables()
                .list()
                .stream()
                .filter(pi -> {
                    String res = (String) pi.getProcessVariables().get("resultado");
                    return res == null || !"cancelado".equalsIgnoreCase(res.trim());
                })
                .toList();

        long sumInscripciones = 0, countInscripciones = 0;
        long sumMateriales = 0, countMateriales = 0;
        long sumRecursos = 0, countRecursos = 0;

        for (HistoricProcessInstance pi : instanciasValidas) {
            List<HistoricActivityInstance> acts = historyService.createHistoricActivityInstanceQuery()
                    .processInstanceId(pi.getId())
                    .finished()
                    .list();

            for (HistoricActivityInstance a : acts) {
                Long dur = a.getDurationInMillis();
                if (dur == null) continue;

                if ("confeccionarListaDefinitivaTask".equals(a.getActivityId())) {
                    sumInscripciones += dur;
                    countInscripciones++;
                } else if ("prepararMaterialesTask".equals(a.getActivityId())) {
                    sumMateriales += dur;
                    countMateriales++;
                } else if ("evaluarRecursosTask".equals(a.getActivityId())
                        || "reservarAulaTask".equals(a.getActivityId())
                        || "solicitarAulaVirtualTask".equals(a.getActivityId())
                        || "contratarEquipamientoTask".equals(a.getActivityId())) {
                    sumRecursos += dur;
                    countRecursos++;
                }
            }
        }

        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("ramaInscripcionesPromedioSegundos", countInscripciones > 0 ? (sumInscripciones / countInscripciones) / 1000.0 : 0.0);
        respuesta.put("ramaMaterialesPromedioSegundos", countMateriales > 0 ? (sumMateriales / countMateriales) / 1000.0 : 0.0);
        respuesta.put("ramaRecursosPromedioSegundos", countRecursos > 0 ? (sumRecursos / countRecursos) / 1000.0 : 0.0);
        respuesta.put("instanciasAnalizadas", instanciasValidas.size());
        return respuesta;
    }

    /**
     * Métrica 3: Tabla de cantidad de tareas de usuario completadas por cada integrante
     * de administración y docentes.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> obtenerMetricasTareasPorUsuario() {
        List<HistoricTaskInstance> tareasCompletadas = historyService.createHistoricTaskInstanceQuery()
                .processDefinitionKey(PROCESS_KEY)
                .finished()
                .list();

        Map<String, Long> tareasPorUsuario = new LinkedHashMap<>();
        // Personal oficial (Sección 2):
        tareasPorUsuario.put("admin_laura", 0L);
        tareasPorUsuario.put("admin_martin", 0L);
        tareasPorUsuario.put("docente_carlos", 0L);
        tareasPorUsuario.put("docente_ana", 0L);
        tareasPorUsuario.put("docente_pedro", 0L);

        for (HistoricTaskInstance t : tareasCompletadas) {
            String assignee = t.getAssignee();
            if (assignee != null) {
                tareasPorUsuario.put(assignee, tareasPorUsuario.getOrDefault(assignee, 0L) + 1);
            }
        }

        List<Map<String, Object>> filas = new ArrayList<>();
        for (Map.Entry<String, Long> entry : tareasPorUsuario.entrySet()) {
            Map<String, Object> fila = new HashMap<>();
            fila.put("usuario", entry.getKey());
            fila.put("grupo", entry.getKey().startsWith("admin") ? "Administración" : "Docentes");
            fila.put("tareasCompletadas", entry.getValue());
            filas.add(fila);
        }

        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("totalTareasCompletadas", tareasCompletadas.size());
        respuesta.put("usuarios", filas);
        return respuesta;
    }

    // =========================================================================
    // 7. ESTADO EN TIEMPO REAL (Sección 4.1.2)
    // =========================================================================

    @Transactional(readOnly = true)
    public EstadoCursoResponse consultarEstadoCurso(String identificador) {
        // Consultar estado en BD institucional si existe
        Map<String, Object> bdCurso = cursoExternalService.obtenerCurso(identificador);
        String estadoBd = bdCurso != null ? (String) bdCurso.get("estado") : null;

        // 1. Instancia activa
        ProcessInstance active = runtimeService.createProcessInstanceQuery()
                .processInstanceBusinessKey(identificador)
                .singleResult();
        if (active == null) {
            active = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(identificador)
                    .singleResult();
        }

        if (active != null) {
            Map<String, Object> vars = runtimeService.getVariables(active.getId());
            List<Task> activeTasks = taskService.createTaskQuery()
                    .processInstanceId(active.getId())
                    .active()
                    .list();

            String tareaActual = activeTasks.isEmpty() ? "En espera de evento o servicio" :
                    activeTasks.stream().map(Task::getName).collect(Collectors.joining(", "));
            String nombreCurso = vars.getOrDefault("nombreCurso",
                    (bdCurso != null && bdCurso.get("nombre") != null) ? bdCurso.get("nombre") : "Capacitación Empresarial").toString();

            // Determinar estado de acuerdo a BD o al proceso actual
            String estadoCalculado = estadoBd != null ? estadoBd : "en_preparacion";

            Map<String, Object> detalleRamas = null;
            try {
                detalleRamas = obtenerDetalleRamasPreparacion(active.getId());
            } catch (Exception e) {
                log.debug("No se pudo calcular ramas para [{}]: {}", active.getId(), e.getMessage());
            }

            return new EstadoCursoResponse(
                    active.getBusinessKey(),
                    active.getId(),
                    nombreCurso,
                    estadoCalculado,
                    tareaActual,
                    active.getStartTime(),
                    null,
                    vars,
                    detalleRamas
            );
        }

        // 2. Historial de proceso finalizado
        HistoricProcessInstance historic = historyService.createHistoricProcessInstanceQuery()
                .processInstanceBusinessKey(identificador)
                .singleResult();
        if (historic == null) {
            historic = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(identificador)
                    .singleResult();
        }

        if (historic != null) {
            Map<String, Object> historicVars = new HashMap<>();
            historyService.createHistoricVariableInstanceQuery()
                    .processInstanceId(historic.getId())
                    .list()
                    .forEach(v -> historicVars.put(v.getVariableName(), v.getValue()));

            String res = (String) historicVars.getOrDefault("resultado", (estadoBd != null ? estadoBd : "finalizado"));
            String nombreCurso = historicVars.getOrDefault("nombreCurso",
                    (bdCurso != null && bdCurso.get("nombre") != null) ? bdCurso.get("nombre") : "Capacitación Empresarial").toString();

            Map<String, Object> detalleRamas = null;
            try {
                detalleRamas = obtenerDetalleRamasPreparacion(historic.getId());
            } catch (Exception e) {
                log.debug("No se pudo calcular ramas para historico [{}]: {}", historic.getId(), e.getMessage());
            }

            return new EstadoCursoResponse(
                    historic.getBusinessKey(),
                    historic.getId(),
                    nombreCurso,
                    res,
                    "Proceso Concluido: " + res,
                    historic.getStartTime(),
                    historic.getEndTime(),
                    historicVars,
                    detalleRamas
            );
        }

        // Si solo existe en BD
        if (bdCurso != null) {
            Map<String, Object> variables = new HashMap<>(bdCurso);
            return new EstadoCursoResponse(
                    (String) bdCurso.get("curso_id"),
                    (String) bdCurso.get("process_instance_id"),
                    (String) bdCurso.get("nombre"),
                    (String) bdCurso.get("estado"),
                    "Registrado en base de datos",
                    null,
                    null,
                    variables,
                    null
            );
        }

        throw new IllegalArgumentException("No se encontró ningún curso o proceso con el identificador: " + identificador);
    }

    private TareaResponse mapearATareaResponse(Task task) {
        Map<String, Object> variables = taskService.getVariables(task.getId());
        return new TareaResponse(
                task.getId(),
                task.getName(),
                task.getAssignee(),
                task.getProcessInstanceId(),
                task.getCreateTime(),
                task.getDueDate(),
                variables
        );
    }
}
