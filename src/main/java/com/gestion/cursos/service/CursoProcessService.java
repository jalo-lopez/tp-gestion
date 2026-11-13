package com.gestion.cursos.service;

import com.gestion.cursos.dto.request.CompletarTareaRequest;
import com.gestion.cursos.dto.request.IniciarCursoRequest;
import com.gestion.cursos.dto.response.EstadoCursoResponse;
import com.gestion.cursos.dto.response.ProcesoIniciadoResponse;
import com.gestion.cursos.dto.response.TareaResponse;
import org.flowable.engine.HistoryService;
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

    public CursoProcessService(RuntimeService runtimeService, TaskService taskService,
                               HistoryService historyService, ManagementService managementService) {
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.historyService = historyService;
        this.managementService = managementService;
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
        }

        // Si se completa 'Definir Docente que desarrolla el curso', asegurar variable de docente
        if ("Definir Docente que desarrolla el curso".equals(task.getName())) {
            String docente = (String) variables.getOrDefault("docenteAsignado", "docente_carlos");
            variables.put("docenteAsignado", docente);
            // Si no se definió fecha de inicio previa, fijar una por defecto para no bloquear el timer
            if (runtimeService.getVariable(task.getExecutionId(), "fechaInicioCurso") == null) {
                variables.put("fechaInicioCurso", LocalDateTime.now().plusSeconds(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            }
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
            List<org.flowable.eventsubscription.service.impl.persistence.entity.EventSubscriptionEntity> subs =
                    runtimeService.createEventSubscriptionQuery()
                            .executionId(ex.getId())
                            .eventType("message")
                            .list()
                            .stream()
                            .map(s -> (org.flowable.eventsubscription.service.impl.persistence.entity.EventSubscriptionEntity) s)
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
        vars.put("fechaInicioCurso", fechaAcordada != null ? fechaAcordada : LocalDateTime.now().plusDays(10).toString());
        vars.put("decisionEmpresa", "ACEPTADA");
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
        log.info(">>> ServiceTask: Enviar confirmación de curso a la empresa para [{}] <<<",
                execution.getProcessInstanceBusinessKey());
        execution.setVariable("confirmacionEnviada", true);
    }

    // =========================================================================
    // 4. CONSULTAS HISTÓRICAS Y MÉTRICAS (Sección 4.3)
    // =========================================================================

    /**
     * Métrica 1: Tabla de cantidad y porcentaje de instancias finalizadas por resultado
     * (finalizado, cancelado, rechazado, no viable, cerrado por falta de respuesta).
     */
    @Transactional(readOnly = true)
    public Map<String, Object> obtenerMetricasResultados() {
        List<HistoricProcessInstance> terminadas = historyService.createHistoricProcessInstanceQuery()
                .processDefinitionKey(PROCESS_KEY)
                .finished()
                .includeProcessVariables()
                .list();

        long total = terminadas.size();
        Map<String, Long> conteo = new LinkedHashMap<>();
        conteo.put("finalizado", 0L);
        conteo.put("cancelado", 0L);
        conteo.put("rechazado", 0L);
        conteo.put("no viable", 0L);
        conteo.put("cerrado por falta de respuesta", 0L);

        for (HistoricProcessInstance pi : terminadas) {
            String res = (String) pi.getProcessVariables().get("resultado");
            if (res == null) res = "finalizado";
            res = res.toLowerCase().trim();
            conteo.put(res, conteo.getOrDefault(res, 0L) + 1);
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
        respuesta.put("totalInstancias", total);
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
    // 5. ESTADO EN TIEMPO REAL
    // =========================================================================

    @Transactional(readOnly = true)
    public EstadoCursoResponse consultarEstadoCurso(String identificador) {
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
            String nombreCurso = vars.getOrDefault("nombreCurso", "Capacitación Empresarial").toString();

            return new EstadoCursoResponse(
                    active.getBusinessKey(),
                    active.getId(),
                    nombreCurso,
                    "en_preparacion",
                    tareaActual,
                    active.getStartTime(),
                    null,
                    vars
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

            String res = (String) historicVars.getOrDefault("resultado", "finalizado");
            String nombreCurso = historicVars.getOrDefault("nombreCurso", "Capacitación Empresarial").toString();

            return new EstadoCursoResponse(
                    historic.getBusinessKey(),
                    historic.getId(),
                    nombreCurso,
                    res,
                    "Proceso Concluido: " + res,
                    historic.getStartTime(),
                    historic.getEndTime(),
                    historicVars
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
