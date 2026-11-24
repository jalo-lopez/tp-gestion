package com.gestion.cursos;

import com.gestion.cursos.dto.request.CompletarTareaRequest;
import com.gestion.cursos.dto.request.IniciarCursoRequest;
import com.gestion.cursos.dto.response.EstadoCursoResponse;
import com.gestion.cursos.dto.response.ProcesoIniciadoResponse;
import com.gestion.cursos.dto.response.TareaResponse;
import com.gestion.cursos.service.CursoProcessService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class GestionCursosApplicationTests {

    @Autowired
    private CursoProcessService cursoProcessService;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    @org.junit.jupiter.api.BeforeEach
    void limpiarInstanciasActivas() {
        runtimeService.createProcessInstanceQuery().list()
                .forEach(pi -> {
                    try {
                        runtimeService.deleteProcessInstance(pi.getId(), "limpieza de test");
                    } catch (Exception ignored) {}
                });
    }

    @Test
    @DisplayName("Debe cargar el contexto de Spring Boot exitosamente")
    void contextLoads() {
        assertThat(cursoProcessService).isNotNull();
    }

    @Test
    @DisplayName("Escenario 1: Flujo completo con aceptación, preparación en paralelo, compensación y cierre")
    void testEscenario1FlujoCompleto() {
        // 1. Iniciar Solicitud
        IniciarCursoRequest request = new IniciarCursoRequest(
                "CURSO-ESC-1",
                "Arquitectura BPMN 2.0 y Microservicios",
                "Ingeniería",
                "empresa@corp.com",
                25,
                "Capacitación empresarial intensiva"
        );
        ProcesoIniciadoResponse inicio = cursoProcessService.iniciarProceso(request);
        String pid = inicio.getProcessInstanceId();

        // 2. Tarea: 'Registrar y Elaborar propuesta' (Jefe Admin: admin_laura)
        List<TareaResponse> tareasLaura = cursoProcessService.obtenerTareasPorUsuario("admin_laura");
        TareaResponse tareaPropuesta = tareasLaura.stream()
                .filter(t -> t.getProcessInstanceId().equals(pid) && "Registrar y Elaborar propuesta".equals(t.getTaskName()))
                .findFirst().orElseThrow();

        CompletarTareaRequest reqPropuesta = new CompletarTareaRequest();
        reqPropuesta.setAprobado(true);
        reqPropuesta.setVariablesAdicionales(Map.of(
                "presupuesto", "$ 1.200.000",
                "fechasPosibles", "2026-12-01 al 2026-12-15",
                "programa", "Módulos 1, 2 y 3"
        ));
        cursoProcessService.completarTarea(tareaPropuesta.getTaskId(), reqPropuesta);

        // 3. Simular Aceptación de la Empresa (Sección 4.2.2)
        List<String> habilitadas = cursoProcessService.obtenerComunicacionesHabilitadas(pid);
        assertThat(habilitadas).contains("ACEPTAR_PROPUESTA", "RECHAZAR_PROPUESTA", "SOLICITAR_MODIFICACIONES");

        String fechaAcordada = LocalDateTime.now().plusSeconds(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        cursoProcessService.aceptarPropuesta(pid, fechaAcordada);

        // 4. Preparación en Paralelo:
        // Rama 1: Enviar Nómina
        List<String> habilitadasPrep = cursoProcessService.obtenerComunicacionesHabilitadas(pid);
        assertThat(habilitadasPrep).contains("ENVIAR_NOMINA", "CANCELAR_PREPARACION");
        cursoProcessService.enviarNomina(pid, List.of("Alumno 1", "Alumno 2"));

        // Rama 1 Tarea: 'Confeccionar lista definitiva' (Asignada por balanceo a admin_martin o admin_laura)
        List<Task> tareasLista = taskService.createTaskQuery().processInstanceId(pid).taskName("Confeccionar lista definitiva").list();
        assertThat(tareasLista).hasSize(1);
        Task tareaLista = tareasLista.getFirst();
        assertThat(tareaLista.getAssignee()).isIn("admin_laura", "admin_martin");
        taskService.complete(tareaLista.getId(), Map.of("listaDefinitivaConfeccionada", true));

        // Rama 2 Tarea: 'Preparar materiales' (Grupo Administración)
        List<Task> tareasMat = taskService.createTaskQuery().processInstanceId(pid).taskName("Preparar materiales").list();
        assertThat(tareasMat).hasSize(1);
        Task tareaMat = tareasMat.getFirst();
        cursoProcessService.reclamarTarea(tareaMat.getId(), "admin_martin");
        CompletarTareaRequest reqMat = new CompletarTareaRequest();
        reqMat.setVariablesAdicionales(Map.of("materialesPreparados", true));
        cursoProcessService.completarTarea(tareaMat.getId(), reqMat);

        // Rama 3 Tarea: 'Evaluar Recursos' (Grupo Administración)
        List<Task> tareasRec = taskService.createTaskQuery().processInstanceId(pid).taskName("Evaluar Recursos").list();
        assertThat(tareasRec).hasSize(1);
        Task tareaRec = tareasRec.getFirst();
        cursoProcessService.reclamarTarea(tareaRec.getId(), "admin_laura");
        CompletarTareaRequest reqRec = new CompletarTareaRequest();
        reqRec.setVariablesAdicionales(Map.of(
                "requiereAulaFisica", true,
                "requiereAulaVirtual", false,
                "requiereEquipamiento", false
        ));
        cursoProcessService.completarTarea(tareaRec.getId(), reqRec);

        for (org.flowable.engine.runtime.Execution ex : runtimeService.createExecutionQuery().processInstanceId(pid).list()) {
            System.out.println(">>> ACTIVE EXECUTION: id=" + ex.getId() + ", activityId=" + ex.getActivityId());
        }
        for (Task t : taskService.createTaskQuery().processInstanceId(pid).list()) {
            System.out.println(">>> ACTIVE TASK: id=" + t.getId() + ", name=" + t.getName());
        }

        // 5. Fase Docente:
        // Tarea: 'Definir Docente que desarrolla el curso' (Jefe docente: docente_carlos)
        List<Task> tareasDefDoc = taskService.createTaskQuery().processInstanceId(pid).taskName("Definir Docente que desarrolla el curso").list();
        assertThat(tareasDefDoc).hasSize(1);
        Task tareaDefDoc = tareasDefDoc.getFirst();
        assertThat(tareaDefDoc.getAssignee()).isEqualTo("docente_carlos");

        CompletarTareaRequest reqDefDoc = new CompletarTareaRequest();
        reqDefDoc.setVariablesAdicionales(Map.of(
                "docenteAsignado", "docente_ana",
                "fechaInicioCurso", LocalDateTime.now().minusMinutes(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        ));
        cursoProcessService.completarTarea(tareaDefDoc.getId(), reqDefDoc);

        // Avanzar el temporizador de fecha programada
        cursoProcessService.avanzarTemporizador(pid);

        // Tarea: 'Desarrollar curso' (Asignada dinámicamente a docente_ana)
        List<Task> tareasDes = taskService.createTaskQuery().processInstanceId(pid).taskName("Desarrollar curso").list();
        assertThat(tareasDes).hasSize(1);
        Task tareaDes = tareasDes.getFirst();
        assertThat(tareaDes.getAssignee()).isEqualTo("docente_ana");
        taskService.complete(tareaDes.getId());

        // Tarea: 'Elaborar informe de resultados' (Jefe docente: docente_carlos)
        List<Task> tareasInf = taskService.createTaskQuery().processInstanceId(pid).taskName("Elaborar informe de resultados").list();
        assertThat(tareasInf).hasSize(1);
        Task tareaInf = tareasInf.getFirst();
        taskService.complete(tareaInf.getId(), Map.of("informeResultados", "Curso completado con 100% de aprobación"));

        // Tarea: 'Enviar informe a empresa y Archivar expediente.' (Jefe admin: admin_laura)
        List<Task> tareasEnv = taskService.createTaskQuery().processInstanceId(pid).taskName("Enviar informe a empresa y Archivar expediente.").list();
        assertThat(tareasEnv).hasSize(1);
        Task tareaEnv = tareasEnv.getFirst();
        assertThat(tareaEnv.getAssignee()).isEqualTo("admin_laura");
        taskService.complete(tareaEnv.getId(), Map.of("resultado", "finalizado"));

        // 6. Verificar Estado Final
        EstadoCursoResponse estadoFinal = cursoProcessService.consultarEstadoCurso(pid);
        assertThat(estadoFinal.getEstado()).isEqualTo("finalizado");
    }

    @Test
    @DisplayName("Escenario 3: Rechazo de la propuesta por la empresa")
    void testEscenario3RechazoPropuesta() {
        IniciarCursoRequest request = new IniciarCursoRequest(
                "CURSO-ESC-3", "Seguridad Informática", "Sistemas", "seguridad@corp.com", 15, "Intro a Pentesting"
        );
        ProcesoIniciadoResponse inicio = cursoProcessService.iniciarProceso(request);
        String pid = inicio.getProcessInstanceId();

        // Completar propuesta inicial
        Task tareaPropuesta = taskService.createTaskQuery().processInstanceId(pid).taskName("Registrar y Elaborar propuesta").singleResult();
        taskService.complete(tareaPropuesta.getId());

        // Empresa rechaza
        cursoProcessService.rechazarPropuesta(pid, "Presupuesto no aprobado por el directorio");

        // Jefe admin archiva solicitud rechazada
        Task tareaArchivar = taskService.createTaskQuery().processInstanceId(pid).taskName("Archivar solicitud").singleResult();
        assertThat(tareaArchivar).isNotNull();
        assertThat(tareaArchivar.getAssignee()).isEqualTo("admin_laura");
        taskService.complete(tareaArchivar.getId(), Map.of("resultado", "rechazado"));

        EstadoCursoResponse estado = cursoProcessService.consultarEstadoCurso(pid);
        assertThat(estado.getEstado()).isEqualTo("rechazado");
    }

    @Test
    @DisplayName("Escenario 6: Cancelación durante la preparación con compensaciones automáticas")
    void testEscenario6CancelacionDurantePreparacion() {
        IniciarCursoRequest request = new IniciarCursoRequest(
                "CURSO-ESC-6", "Ciencia de Datos", "IA", "data@corp.com", 20, "Machine Learning con Python"
        );
        ProcesoIniciadoResponse inicio = cursoProcessService.iniciarProceso(request);
        String pid = inicio.getProcessInstanceId();

        // Completar propuesta y aceptar
        Task tareaPropuesta = taskService.createTaskQuery().processInstanceId(pid).taskName("Registrar y Elaborar propuesta").singleResult();
        taskService.complete(tareaPropuesta.getId());
        cursoProcessService.aceptarPropuesta(pid, "2026-12-10T09:00:00");

        // Durante preparación, evaluar recursos y reservar aula
        Task tareaRec = taskService.createTaskQuery().processInstanceId(pid).taskName("Evaluar Recursos").singleResult();
        taskService.complete(tareaRec.getId(), Map.of("requiereAulaFisica", true, "requiereAulaVirtual", false, "requiereEquipamiento", false));

        // Empresa cancela voluntariamente
        cursoProcessService.cancelarPreparacion(pid, "Cancelación corporativa imprevista");

        // Actividad User de pausa requerida por la cátedra para verificar orden de compensación
        Task tareaPausa = taskService.createTaskQuery().processInstanceId(pid).taskName("Dejar una actividad User para que pare").singleResult();
        assertThat(tareaPausa).isNotNull();
        taskService.complete(tareaPausa.getId());

        // El proceso debe quedar concluido como cancelado
        EstadoCursoResponse estado = cursoProcessService.consultarEstadoCurso(pid);
        assertThat(estado.getEstado()).isEqualTo("cancelado");
    }

    @Test
    @DisplayName("Escenario 7: Error forzado por falta de aula física")
    void testEscenario7FaltaAulaFisica() {
        IniciarCursoRequest request = new IniciarCursoRequest(
                "CURSO-ESC-7", "DevOps & Kubernetes", "Cloud", "devops@corp.com", 18, "CI/CD con Flowable"
        );
        request.getVariablesAdicionales().put("forzarErrorAula", true);

        ProcesoIniciadoResponse inicio = cursoProcessService.iniciarProceso(request);
        String pid = inicio.getProcessInstanceId();

        Task tareaPropuesta = taskService.createTaskQuery().processInstanceId(pid).taskName("Registrar y Elaborar propuesta").singleResult();
        taskService.complete(tareaPropuesta.getId());
        cursoProcessService.aceptarPropuesta(pid, "2026-12-05T09:00:00");

        Task tareaRec = taskService.createTaskQuery().processInstanceId(pid).taskName("Evaluar Recursos").singleResult();

        // Al intentar reservar aula con forzarErrorAula=true debe fallar
        // Flowable envuelve la excepción: FlowableException -> ELException -> RuntimeException
        assertThatThrownBy(() -> {
            taskService.complete(tareaRec.getId(), Map.of(
                    "requiereAulaFisica", true,
                    "requiereAulaVirtual", false,
                    "requiereEquipamiento", false,
                    "forzarErrorAula", true
            ));
        }).hasStackTraceContaining("ERROR_AULA_NO_DISPONIBLE");
    }
}
