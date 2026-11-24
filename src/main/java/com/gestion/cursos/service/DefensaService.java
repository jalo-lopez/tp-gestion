package com.gestion.cursos.service;

import com.gestion.cursos.dto.request.CompletarTareaRequest;
import com.gestion.cursos.dto.request.IniciarCursoRequest;
import com.gestion.cursos.dto.response.ProcesoIniciadoResponse;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Servicio de soporte para la Defensa y Demostración en vivo ante los evaluadores.
 * Prepara de manera instantánea el estado del motor y de la base de datos institucional
 * para exhibir los 11 escenarios de la consigna con agilidad y sin demoras.
 */
@Service
public class DefensaService {

    private static final Logger log = LoggerFactory.getLogger(DefensaService.class);

    private final CursoProcessService cursoProcessService;
    private final RuntimeService runtimeService;
    private final TaskService taskService;

    public DefensaService(CursoProcessService cursoProcessService,
                          RuntimeService runtimeService,
                          TaskService taskService) {
        this.cursoProcessService = cursoProcessService;
        this.runtimeService = runtimeService;
        this.taskService = taskService;
    }

    /**
     * Carga todos los escenarios requeridos para la presentación:
     * Al crearse 5 instancias activas estratégicamente configuradas, se cubre:
     * - Escenario 2: Instancia lista esperando respuesta para simular SOLICITUD DE MODIFICACIONES.
     * - Escenario 3: Instancia lista esperando respuesta para simular RECHAZO DE PROPUESTA.
     * - Escenario 4: Instancia lista para simular TIMEOUT Y RECORDATORIOS.
     * - Escenario 6: Instancia en preparación con recursos reservados lista para CANCELACIÓN Y COMPENSACIÓN.
     * - Escenario 8: Instancia lista para balanceo de carga en nómina.
     * - Escenario 5: Al existir exactamente 5 instancias activas en el motor, CUALQUIER nueva solicitud
     *                iniciada en la defensa disparará la regla de negocio DMN (< 5 viable, >= 5 no viable)
     *                demostrando el rechazo automático por límite de capacidad.
     */
    @Transactional
    public Map<String, Object> cargarEscenariosDefensa() {
        log.info(">>> Iniciando precarga de escenarios para la defensa de la cátedra <<<");

        // 1. Verificar si ya existen instancias creadas para evitar duplicaciones
        long activasExistentes = runtimeService.createProcessInstanceQuery()
                .processDefinitionKey(CursoProcessService.PROCESS_KEY)
                .processInstanceBusinessKey("CURSO-ESC-6-CANCEL")
                .count();

        if (activasExistentes > 0) {
            log.info("Los escenarios de la defensa ya fueron precargados anteriormente.");
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("estado", "YA_CARGADOS");
            res.put("mensaje", "Los escenarios de la defensa ya se encontraban activos en el sistema.");
            res.put("totalInstanciasActivas", runtimeService.createProcessInstanceQuery().processDefinitionKey(CursoProcessService.PROCESS_KEY).active().count());
            return res;
        }

        List<Map<String, Object>> detalle = new ArrayList<>();

        try {
            // Instancia 1 (Escenario 6): En preparación con recursos reservados (Aula física + virtual)
            // Lista para simular Cancelación durante preparación -> Compensaciones automáticas -> Pausa User -> XOR -> Fin Cancelado
            ProcesoIniciadoResponse pCancel = cursoProcessService.iniciarProceso(new IniciarCursoRequest(
                    "CURSO-ESC-6-CANCEL", "Ingeniería de Datos y Pipelines ETL", "Datos",
                    "contacto@techsolutions.com", 22, "Apache Spark, Kafka y Airflow en la nube"
            ));
            completarPropuestaYEnviar(pCancel.getProcessInstanceId(), "$ 1.100.000", "2027-10-01 al 2027-10-20", "Arquitectura de Datos y Streaming");
            cursoProcessService.aceptarPropuesta(pCancel.getProcessInstanceId(), "2027-10-05T09:00:00");
            evaluarRecursos(pCancel.getProcessInstanceId(), true, true, false);
            detalle.add(Map.of("cursoId", "CURSO-ESC-6-CANCEL", "escenario", "Escenario 6 (Cancelación y Compensación)", "pid", pCancel.getProcessInstanceId()));

            // Instancia 2 (Escenario 2): Propuesta elaborada y enviada a la empresa
            // Lista para simular "Solicitud de modificaciones" desde el simulador o API
            ProcesoIniciadoResponse pModif = cursoProcessService.iniciarProceso(new IniciarCursoRequest(
                    "CURSO-ESC-2-MODIF", "Desarrollo Frontend Moderno con React", "Web",
                    "contacto@techsolutions.com", 25, "React 19, TypeScript y TailwindCSS"
            ));
            completarPropuestaYEnviar(pModif.getProcessInstanceId(), "$ 750.000", "2027-07-01 al 2027-07-20", "Componentes, Estado y Testing");
            detalle.add(Map.of("cursoId", "CURSO-ESC-2-MODIF", "escenario", "Escenario 2 (Modificaciones)", "pid", pModif.getProcessInstanceId()));

            // Instancia 3 (Escenario 3): Propuesta elaborada y enviada
            // Lista para simular "Rechazo de propuesta" y posterior archivado por Laura
            ProcesoIniciadoResponse pRechazo = cursoProcessService.iniciarProceso(new IniciarCursoRequest(
                    "CURSO-ESC-3-RECHAZO", "Blockchain y Smart Contracts", "Fintech",
                    "mlopez@innovalog.com", 15, "Solidity y contratos inteligentes en Ethereum"
            ));
            completarPropuestaYEnviar(pRechazo.getProcessInstanceId(), "$ 1.300.000", "2027-08-01 al 2027-08-15", "Seguridad y DApps");
            detalle.add(Map.of("cursoId", "CURSO-ESC-3-RECHAZO", "escenario", "Escenario 3 (Rechazo)", "pid", pRechazo.getProcessInstanceId()));

            // Instancia 4 (Escenario 4): Propuesta enviada
            // Lista para simular timeouts y envío de recordatorios (avanzarTemporizador)
            ProcesoIniciadoResponse pTimeout = cursoProcessService.iniciarProceso(new IniciarCursoRequest(
                    "CURSO-ESC-4-TIMEOUT", "Automatización Robótica de Procesos (RPA)", "Automatización",
                    "contacto@techsolutions.com", 20, "RPA corporativo con UiPath y bots"
            ));
            completarPropuestaYEnviar(pTimeout.getProcessInstanceId(), "$ 650.000", "2027-09-01 al 2027-09-15", "Workflows y Conectores");
            detalle.add(Map.of("cursoId", "CURSO-ESC-4-TIMEOUT", "escenario", "Escenario 4 (Falta de respuesta)", "pid", pTimeout.getProcessInstanceId()));

            // Instancia 5 (Escenario 8 y 11): En preparación, propuesta aceptada
            // Lista para enviar nómina (balanceo de carga) y preparar materiales en la App Swing
            ProcesoIniciadoResponse p5 = cursoProcessService.iniciarProceso(new IniciarCursoRequest(
                    "CURSO-ACTIVO-5", "Gestión de Procesos BPMN con Flowable", "Ingeniería de Software",
                    "contacto@techsolutions.com", 25, "Taller intensivo de BPMN 2.0 y motores de workflow"
            ));
            completarPropuestaYEnviar(p5.getProcessInstanceId(), "$ 1.200.000", "2027-03-01 al 2027-03-15", "Módulos 1 a 4");
            cursoProcessService.aceptarPropuesta(p5.getProcessInstanceId(), "2027-03-05T09:00:00");
            detalle.add(Map.of("cursoId", "CURSO-ACTIVO-5", "escenario", "Escenario 8/11 (Balanceo y Materiales Swing)", "pid", p5.getProcessInstanceId()));

        } catch (Exception e) {
            log.error("Error al precargar los escenarios de defensa: {}", e.getMessage(), e);
            throw new RuntimeException("Error en la precarga de defensa: " + e.getMessage(), e);
        }

        long totalActivas = runtimeService.createProcessInstanceQuery()
                .processDefinitionKey(CursoProcessService.PROCESS_KEY)
                .active()
                .count();

        log.info(">>> Precarga de escenarios finalizada con éxito. Instancias activas totales: {} <<<", totalActivas);

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("estado", "EXITO");
        respuesta.put("mensaje", "Todos los escenarios para la defensa fueron generados exitosamente. Hay 5 instancias activas para la regla DMN.");
        respuesta.put("totalInstanciasActivas", totalActivas);
        respuesta.put("cursosGenerados", detalle);
        return respuesta;
    }

    // Métodos auxiliares para orquestar la preparación rápida

    private void completarPropuestaYEnviar(String pid, String presupuesto, String fechas, String programa) {
        Task tarea = taskService.createTaskQuery()
                .processInstanceId(pid)
                .taskName("Registrar y Elaborar propuesta")
                .singleResult();

        if (tarea != null) {
            CompletarTareaRequest req = new CompletarTareaRequest();
            req.setAprobado(true);
            req.setVariablesAdicionales(Map.of(
                    "presupuesto", presupuesto,
                    "fechasPosibles", fechas,
                    "programa", programa
            ));
            cursoProcessService.completarTarea(tarea.getId(), req);
        }
    }

    private void evaluarRecursos(String pid, boolean fisica, boolean virtual, boolean equip) {
        Task tarea = taskService.createTaskQuery()
                .processInstanceId(pid)
                .taskName("Evaluar Recursos")
                .singleResult();

        if (tarea != null) {
            cursoProcessService.reclamarTarea(tarea.getId(), "admin_laura");
            CompletarTareaRequest req = new CompletarTareaRequest();
            req.setVariablesAdicionales(Map.of(
                    "requiereAulaFisica", fisica,
                    "requiereAulaVirtual", virtual,
                    "requiereEquipamiento", equip
            ));
            cursoProcessService.completarTarea(tarea.getId(), req);
        }
    }
}
