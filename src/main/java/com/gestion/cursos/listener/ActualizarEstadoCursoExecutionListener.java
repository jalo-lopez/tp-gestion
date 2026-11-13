package com.gestion.cursos.listener;

import com.gestion.cursos.service.CursoExternalService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ExecutionListener que se dispara al finalizar el proceso de gestión de cursos.
 * Su responsabilidad es leer el resultado del flujo de trabajo y reflejar el estado final
 * en la base de datos externa (PostgreSQL/Supabase).
 *
 * Configuración en BPMN (habitualmente en el End Event):
 * {@code <flowable:executionListener event="end" delegateExpression="${actualizarEstadoCursoExecutionListener}" />}
 */
@Component("actualizarEstadoCursoExecutionListener")
public class ActualizarEstadoCursoExecutionListener implements ExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(ActualizarEstadoCursoExecutionListener.class);

    private final CursoExternalService cursoExternalService;

    // Inyección de dependencias por constructor
    public ActualizarEstadoCursoExecutionListener(CursoExternalService cursoExternalService) {
        this.cursoExternalService = cursoExternalService;
    }

    @Override
    public void notify(DelegateExecution execution) {
        log.info("Ejecutando ActualizarEstadoCursoExecutionListener para la instancia de proceso [ID: {}]",
                execution.getProcessInstanceId());

        // 1. EXTRAER VARIABLES DEL CONTEXTO DEL PROCESO
        String cursoId = (String) execution.getVariable("cursoId");
        String resultado = (String) execution.getVariable("resultado");
        Boolean aprobado = (Boolean) execution.getVariable("aprobado");
        String observaciones = (String) execution.getVariable("observaciones");

        // 2. DETERMINAR EL ESTADO FINAL (Sección 3.4: finalizado, cancelado, rechazado, cerrado por falta de respuesta, no viable)
        String estadoFinal;
        if (resultado != null && !resultado.isBlank()) {
            estadoFinal = resultado.toLowerCase().trim();
        } else if (Boolean.TRUE.equals(aprobado)) {
            estadoFinal = "finalizado";
        } else if (Boolean.FALSE.equals(aprobado)) {
            estadoFinal = "rechazado";
        } else {
            estadoFinal = "cancelado";
        }

        // =========================================================================
        // 3. INVOCAR EL SERVICIO DE PERSISTENCIA EXTERNA
        // =========================================================================
        try {
            cursoExternalService.actualizarEstadoCurso(
                    cursoId != null ? cursoId : execution.getProcessInstanceBusinessKey(),
                    estadoFinal,
                    observaciones != null ? observaciones : "Sin observaciones adicionales"
            );
            log.info("Estado del curso [{}] actualizado a [{}] en la base de datos externa.", cursoId, estadoFinal);
        } catch (Exception e) {
            log.error("Error al actualizar el estado del curso en la base de datos externa: {}", e.getMessage(), e);
            // Dependiendo de los requerimientos, aquí se puede propagar la excepción para disparar
            // un rollback transaccional o manejar una compensación / reintento.
            throw new RuntimeException("Fallo en sincronización con base de datos externa al finalizar el proceso", e);
        }
    }
}
