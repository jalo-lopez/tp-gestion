package com.gestion.cursos.listener;

import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ExecutionListener que se ejecuta inmediatamente antes de la Decision Task (DMN)
 * de Viabilidad (Sección 3.1).
 *
 * Requisito Sección 3.1:
 * "Su entrada será la cantidad de otras instancias del proceso de organización de cursos
 * que todavía no finalizaron, incluyendo las que están en acuerdo o preparación.
 * Se excluirá del conteo la instancia que evalúa su propia viabilidad.
 * Se considerarán las distintas versiones desplegadas del mismo proceso.
 * La cantidad se obtendrá del motor y no deberá ser ingresada manualmente."
 */
@Component("prepararViabilidadExecutionListener")
public class PrepararViabilidadExecutionListener implements ExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(PrepararViabilidadExecutionListener.class);

    private final RuntimeService runtimeService;

    public PrepararViabilidadExecutionListener(RuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    @Override
    public void notify(DelegateExecution execution) {
        String processDefinitionKey = execution.getProcessDefinitionId().split(":")[0];
        String currentProcessInstanceId = execution.getProcessInstanceId();

        // Consulta en Flowable: todas las instancias activas del proceso en todas las versiones desplegadas
        long totalActivas = runtimeService.createProcessInstanceQuery()
                .processDefinitionKey(processDefinitionKey)
                .active()
                .count();

        // Excluir la instancia actual que está evaluando su propia viabilidad
        int otrasInstancias = (int) Math.max(0, totalActivas - 1);

        log.info("Evaluación de viabilidad para proceso [{}]: Total activas en motor = {}, Otras instancias = {}",
                currentProcessInstanceId, totalActivas, otrasInstancias);

        // Guardar la variable para que sea consumida por la Decision Task DMN
        execution.setVariable("cantInstanciasActivas", otrasInstancias);
        
        // Inicializar contador de recordatorios si no existe
        if (execution.getVariable("recordatoriosEnviados") == null) {
            execution.setVariable("recordatoriosEnviados", 0);
        }
        
        // Duración por defecto del temporizador de espera de respuesta (5 días = P5D)
        // Permite sobrescribirse para pruebas automatizadas (ej: PT5S)
        if (execution.getVariable("timerDurationPropuesta") == null) {
            execution.setVariable("timerDurationPropuesta", "P5D");
        }

        execution.setVariable("requiereAulaFisica", false);
        execution.setVariable("requiereAulaVirtual", false);
        execution.setVariable("requiereEquipamiento", false);
    }
}
