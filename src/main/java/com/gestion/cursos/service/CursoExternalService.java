package com.gestion.cursos.service;

import com.gestion.cursos.dto.request.IniciarCursoRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Servicio encargado de interactuar con la base de datos propia de la institución
 * (PostgreSQL / Supabase / H2) para almacenar y sincronizar empresas y cursos.
 * Cumple con: Sección 3.2, 3.4, 4.1.1 y 4.1.2 del Enunciado.
 */
@Service
public class CursoExternalService {

    private static final Logger log = LoggerFactory.getLogger(CursoExternalService.class);

    private final JdbcTemplate jdbcTemplate;

    public CursoExternalService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }

    /**
     * Registra la solicitud del curso en la base de datos de la institución (Sección 3.2 y 4.1.1).
     * Reutiliza la empresa si ya existe por email o crea una nueva.
     */
    public void registrarSolicitudCurso(IniciarCursoRequest request, String processInstanceId) {
        String email = request.getEmailSolicitante();
        log.info("Persistiendo solicitud en la BD institucional para curso [{}] de [{}]", request.getCursoId(), email);

        try {
            Long empresaId = obtenerOCrearEmpresa(
                    request.getNombreEmpresa() != null ? request.getNombreEmpresa() : "Empresa " + email,
                    request.getContacto() != null ? request.getContacto() : "Contacto",
                    email
            );

            String sql = """
                INSERT INTO cursos (
                    curso_id, empresa_id, process_instance_id, nombre, descripcion,
                    categoria, cupo_estimado, fechas_preferidas, estado
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'pendiente')
            """;

            jdbcTemplate.update(sql,
                    request.getCursoId(),
                    empresaId,
                    processInstanceId,
                    request.getNombreCurso(),
                    request.getDescripcion(),
                    request.getCategoria(),
                    request.getCupoMaximo(),
                    request.getFechasPreferidas() != null ? request.getFechasPreferidas() : "A convenir"
            );

            log.info("Curso [{}] registrado con éxito en la BD institucional (Empresa ID: {})", request.getCursoId(), empresaId);
        } catch (Exception e) {
            log.error("Error al registrar curso en la BD institucional: {}", e.getMessage(), e);
        }
    }

    /**
     * Actualiza el estado del curso en la base de datos institucional (Sección 3.4).
     */
    public void actualizarEstadoCurso(String identificador, String nuevoEstado, String motivo) {
        log.info("Actualizando estado en BD institucional para [{}]: nuevo estado = '{}'", identificador, nuevoEstado);
        try {
            String sql = """
                UPDATE cursos
                SET estado = ?,
                    informe_resultados = CASE WHEN ? IS NOT NULL AND ? <> '' THEN ? ELSE informe_resultados END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE curso_id = ? OR process_instance_id = ?
            """;
            int rows = jdbcTemplate.update(sql, nuevoEstado, motivo, motivo, motivo, identificador, identificador);
            log.info("BD institucional actualizada: {} fila(s) modificada(s) para [{}]", rows, identificador);
        } catch (Exception e) {
            log.error("Error al actualizar estado en BD institucional: {}", e.getMessage(), e);
        }
    }

    /**
     * Guarda los datos de la propuesta elaborada o revisada (programa, presupuesto, fechas posibles).
     */
    public void guardarPropuesta(String identificador, String programa, String presupuesto, String fechasPosibles) {
        log.info("Guardando propuesta acordada en BD institucional para [{}]", identificador);
        try {
            String sql = """
                UPDATE cursos
                SET programa = COALESCE(?, programa),
                    presupuesto = COALESCE(?, presupuesto),
                    fechas_posibles = COALESCE(?, fechas_posibles),
                    updated_at = CURRENT_TIMESTAMP
                WHERE curso_id = ? OR process_instance_id = ?
            """;
            jdbcTemplate.update(sql, programa, presupuesto, fechasPosibles, identificador, identificador);
        } catch (Exception e) {
            log.error("Error al guardar propuesta en BD institucional: {}", e.getMessage(), e);
        }
    }

    /**
     * Guarda la fecha acordada y actualiza a 'en_preparacion' al aceptar la propuesta.
     */
    public void registrarAceptacion(String identificador, String fechaInicio) {
        log.info("Registrando aceptación y fecha de inicio [{}] en BD institucional para [{}]", fechaInicio, identificador);
        try {
            String sql = """
                UPDATE cursos
                SET fecha_inicio = ?,
                    estado = 'en_preparacion',
                    updated_at = CURRENT_TIMESTAMP
                WHERE curso_id = ? OR process_instance_id = ?
            """;
            jdbcTemplate.update(sql, fechaInicio, identificador, identificador);
        } catch (Exception e) {
            log.error("Error al registrar aceptación en BD institucional: {}", e.getMessage(), e);
        }
    }

    /**
     * Guarda el informe de resultados elaborado por el docente.
     */
    public void guardarInformeResultados(String identificador, String informe) {
        log.info("Guardando informe de resultados en BD institucional para [{}]", identificador);
        try {
            String sql = """
                UPDATE cursos
                SET informe_resultados = ?,
                    estado = 'en elaboracion de informe',
                    updated_at = CURRENT_TIMESTAMP
                WHERE curso_id = ? OR process_instance_id = ?
            """;
            jdbcTemplate.update(sql, informe, identificador, identificador);
        } catch (Exception e) {
            log.error("Error al guardar informe en BD institucional: {}", e.getMessage(), e);
        }
    }

    /**
     * Habilitar acceso de la empresa (Sección 4.1.2):
     * Crea cuenta si no existe o la reutiliza, y actualiza el estado a 'en_preparacion'.
     */
    public void habilitarAccesoEmpresa(String email, String processInstanceId) {
        log.info("Habilitando acceso de empresa [{}] para instancia [{}]", email, processInstanceId);
        try {
            obtenerOCrearEmpresa("Empresa " + email, "Contacto", email);
            actualizarEstadoCurso(processInstanceId, "en_preparacion", null);
        } catch (Exception e) {
            log.error("Error al habilitar acceso de la empresa: {}", e.getMessage(), e);
        }
    }

    /**
     * Consulta los cursos asociados a una empresa para el portal de seguimiento (Sección 4.1.2).
     */
    public List<Map<String, Object>> obtenerCursosPorEmpresa(String email) {
        String sql = """
            SELECT c.id, c.curso_id, c.process_instance_id, c.nombre, c.descripcion,
                   c.categoria, c.cupo_estimado, c.fechas_preferidas, c.programa,
                   c.presupuesto, c.fechas_posibles, c.fecha_inicio, c.estado,
                   c.informe_resultados, c.created_at, e.nombre AS empresa_nombre, e.email AS empresa_email
            FROM cursos c
            JOIN empresas e ON c.empresa_id = e.id
            WHERE LOWER(e.email) = LOWER(?)
            ORDER BY c.created_at DESC
        """;
        try {
            List<Map<String, Object>> list = jdbcTemplate.queryForList(sql, email);
            return list.stream().map(this::normalizarClavesMinusculas).toList();
        } catch (Exception e) {
            log.error("Error al obtener cursos por empresa [{}]: {}", email, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Consulta un curso por curso_id o process_instance_id.
     */
    public Map<String, Object> obtenerCurso(String identificador) {
        String sql = """
            SELECT c.*, e.nombre AS empresa_nombre, e.contacto AS empresa_contacto, e.email AS empresa_email
            FROM cursos c
            JOIN empresas e ON c.empresa_id = e.id
            WHERE c.curso_id = ? OR c.process_instance_id = ?
        """;
        try {
            Map<String, Object> map = jdbcTemplate.queryForMap(sql, identificador, identificador);
            return normalizarClavesMinusculas(map);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /**
     * Consulta una empresa por su email.
     */
    public Map<String, Object> obtenerEmpresaPorEmail(String email) {
        String sql = "SELECT * FROM empresas WHERE LOWER(email) = LOWER(?)";
        try {
            Map<String, Object> map = jdbcTemplate.queryForMap(sql, email);
            return normalizarClavesMinusculas(map);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public Map<String, Object> normalizarClavesMinusculas(Map<String, Object> original) {
        if (original == null) return null;
        Map<String, Object> normalized = new LinkedHashMap<>();
        original.forEach((k, v) -> normalized.put(k.toLowerCase(), v));
        return normalized;
    }

    public Long obtenerOCrearEmpresa(String nombre, String contacto, String email) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM empresas WHERE LOWER(email) = LOWER(?)",
                    Long.class,
                    email
            );
        } catch (EmptyResultDataAccessException e) {
            jdbcTemplate.update(
                    "INSERT INTO empresas (nombre, contacto, email, password) VALUES (?, ?, ?, 'empresa123')",
                    nombre, contacto, email
            );
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM empresas WHERE LOWER(email) = LOWER(?)",
                    Long.class,
                    email
            );
        }
    }
}
