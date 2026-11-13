package com.gestion.cursos.listener;

import org.flowable.engine.IdentityService;
import org.flowable.engine.TaskService;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.idm.api.User;
import org.flowable.task.service.delegate.DelegateTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * TaskListener encargado de balancear la carga de trabajo entre los miembros
 * del equipo administrativo para 'Confeccionar lista definitiva' (Sección 3.4).
 *
 * Se ejecuta en el evento "create" de la User Task:
 * {@code <flowable:taskListener event="create" delegateExpression="${asignacionBalanceoTaskListener}" />}
 */
@Component("asignacionBalanceoTaskListener")
public class AsignacionBalanceoTaskListener implements TaskListener {

    private static final Logger log = LoggerFactory.getLogger(AsignacionBalanceoTaskListener.class);

    private final TaskService taskService;
    private final IdentityService identityService;

    // Inyección de dependencias por constructor
    public AsignacionBalanceoTaskListener(TaskService taskService, IdentityService identityService) {
        this.taskService = taskService;
        this.identityService = identityService;
    }

    @Override
    public void notify(DelegateTask delegateTask) {
        log.info("Ejecutando AsignacionBalanceoTaskListener para la tarea [ID: {}, Nombre: {}]",
                delegateTask.getId(), delegateTask.getName());

        // 1. Obtener los miembros del grupo Administración desde Flowable IdentityService
        List<User> usuariosGrupo = identityService.createUserQuery().memberOfGroup("Administración").list();
        List<String> administrativosCandidatos;
        if (usuariosGrupo != null && !usuariosGrupo.isEmpty()) {
            administrativosCandidatos = usuariosGrupo.stream().map(User::getId).toList();
        } else {
            administrativosCandidatos = List.of("admin_laura", "admin_martin");
        }

        // 2. LÓGICA DE BALANCEO DE CARGA (LEAST LOADED ASSIGNMENT)
        // Buscamos al administrativo que actualmente tenga la menor cantidad de tareas pendientes activas.
        String administrativoMenosCargado = administrativosCandidatos.stream()
                .min(Comparator.comparingLong(adminId -> {
                    long conteoTareas = taskService.createTaskQuery()
                            .taskAssignee(adminId)
                            .active()
                            .count();
                    log.debug("Usuario {} tiene {} tareas pendientes", adminId, conteoTareas);
                    return conteoTareas;
                }))
                .orElse(administrativosCandidatos.getFirst());

        // 3. ASIGNAR LA TAREA AL USUARIO SELECCIONADO
        delegateTask.setAssignee(administrativoMenosCargado);

        log.info("Tarea [ID: {}] asignada exitosamente al administrativo [{}] por balanceo de carga.",
                delegateTask.getId(), administrativoMenosCargado);
    }
}
