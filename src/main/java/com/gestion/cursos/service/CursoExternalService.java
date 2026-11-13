package com.gestion.cursos.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Servicio encargado de interactuar con la base de datos externa (PostgreSQL/Supabase)
 * para mantener sincronizado el estado del curso y las inscripciones fuera del motor BPM.
 */
@Service
public class CursoExternalService {

    private static final Logger log = LoggerFactory.getLogger(CursoExternalService.class);

    // TODO: Inyectar aquí JdbcTemplate, Spring Data JPA Repository o WebClient para Supabase REST/PostgREST
    // private final JdbcTemplate jdbcTemplate;

    /**
     * Actualiza el estado del curso en la base de datos externa (Supabase / PostgreSQL).
     * Compatible con la tabla 'cursos' (campos: estado, informe_resultados, process_instance_id).
     *
     * @param cursoId Identificador del curso en el sistema de gestión
     * @param nuevoEstado Estado final (pendiente, en_preparacion, finalizado, cancelado, rechazado)
     * @param motivo Observaciones o informe final de resultados
     */
    public void actualizarEstadoCurso(String cursoId, String nuevoEstado, String motivo) {
        log.info(">>> Sincronizando con Base de Datos Externa (Supabase/PostgreSQL) <<<");
        log.info("Actualizando Curso ID [{}]: Nuevo Estado = '{}', Motivo/Informe = '{}'", cursoId, nuevoEstado, motivo);

        // =========================================================================
        // TODO: AQUI VA LA LOGICA ESPECIFICA DE ACCESO A BASE DE DATOS EXTERNA
        // Ejemplo de consulta SQL usando JdbcTemplate con la tabla 'cursos':
        //
        // String sql = """
        //     UPDATE cursos
        //     SET estado = ?,
        //         informe_resultados = ?,
        //         updated_at = CURRENT_TIMESTAMP
        //     WHERE id = ? OR process_instance_id = ?
        // """;
        // jdbcTemplate.update(sql, nuevoEstado.toLowerCase(), motivo, parseLongOrNull(cursoId), cursoId);
        // =========================================================================
    }

    /**
     * Vincula la instancia de Flowable con el registro del curso en Supabase.
     */
    public void vincularProcessInstance(Long cursoId, String processInstanceId) {
        log.info("Vinculando Curso [{}] con ProcessInstanceId [{}] en Supabase", cursoId, processInstanceId);
        // String sql = "UPDATE cursos SET process_instance_id = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        // jdbcTemplate.update(sql, processInstanceId, cursoId);
    }
}
