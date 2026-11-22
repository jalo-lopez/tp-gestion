package com.gestion.cursos.controller;

import com.gestion.cursos.dto.request.CompletarTareaRequest;
import com.gestion.cursos.dto.request.IniciarCursoRequest;
import com.gestion.cursos.dto.response.ApiResponse;
import com.gestion.cursos.dto.response.EstadoCursoResponse;
import com.gestion.cursos.dto.response.ProcesoIniciadoResponse;
import com.gestion.cursos.dto.response.TareaResponse;
import com.gestion.cursos.service.CursoProcessService;
import com.gestion.cursos.service.PdfGeneratorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controlador REST que expone los endpoints del ciclo de vida del proceso BPMN,
 * la autenticación con Flowable (Sección 3.3 y 4.1.2),
 * el detalle de las 3 ramas paralelas (Sección 1.2 y 4.1.2),
 * la simulación de comunicaciones empresariales (Sección 4.2.2),
 * las métricas históricas (Sección 4.3) y la descarga de documentos oficiales en PDF.
 */
@RestController
@RequestMapping("/api/v1/cursos/procesos")
@CrossOrigin(origins = "*")
public class CursoProcesoController {

    private final CursoProcessService cursoProcessService;
    private final PdfGeneratorService pdfGeneratorService;

    public CursoProcesoController(CursoProcessService cursoProcessService, PdfGeneratorService pdfGeneratorService) {
        this.cursoProcessService = cursoProcessService;
        this.pdfGeneratorService = pdfGeneratorService;
    }

    // =========================================================================
    // 0. AUTENTICACIÓN CON FLOWABLE IDM (Sección 3.3 y 4.1.2)
    // =========================================================================

    @PostMapping("/auth/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(
            @RequestBody(required = false) Map<String, String> credenciales) {
        String username = credenciales != null ? credenciales.get("username") : null;
        String password = credenciales != null ? credenciales.get("password") : null;
        try {
            Map<String, Object> usuario = cursoProcessService.autenticarUsuario(username, password);
            return ResponseEntity.ok(ApiResponse.exito("Autenticación exitosa en Flowable", usuario));
        } catch (SecurityException se) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(se.getMessage()));
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(iae.getMessage()));
        }
    }

    // =========================================================================
    // 1. GESTIÓN DEL FLUJO DEL PROCESO
    // =========================================================================

    @PostMapping("/iniciar")
    public ResponseEntity<ApiResponse<ProcesoIniciadoResponse>> iniciarProceso(
            @Valid @RequestBody IniciarCursoRequest request) {
        ProcesoIniciadoResponse response = cursoProcessService.iniciarProceso(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.exito("Proceso de curso iniciado correctamente", response));
    }

    @GetMapping("/instancias/activas")
    public ResponseEntity<ApiResponse<List<ProcesoIniciadoResponse>>> listarInstanciasActivas() {
        List<ProcesoIniciadoResponse> instancias = cursoProcessService.obtenerInstanciasActivas();
        return ResponseEntity.ok(ApiResponse.exito("Instancias activas recuperadas", instancias));
    }

    @GetMapping("/tareas/pendientes")
    public ResponseEntity<ApiResponse<List<TareaResponse>>> consultarTareasPendientes(
            @RequestParam(name = "usuario") String usuario) {
        List<TareaResponse> tareas = cursoProcessService.obtenerTareasPorUsuario(usuario);
        return ResponseEntity.ok(ApiResponse.exito("Tareas pendientes para el usuario: " + usuario, tareas));
    }

    @GetMapping("/instancias/{processInstanceId}/tareas")
    public ResponseEntity<ApiResponse<List<TareaResponse>>> consultarTareasPorInstancia(
            @PathVariable("processInstanceId") String processInstanceId) {
        List<TareaResponse> tareas = cursoProcessService.obtenerTareasPorInstancia(processInstanceId);
        return ResponseEntity.ok(ApiResponse.exito("Tareas para la instancia: " + processInstanceId, tareas));
    }

    @PostMapping("/tareas/{taskId}/completar")
    public ResponseEntity<ApiResponse<Void>> completarTarea(
            @PathVariable("taskId") String taskId,
            @RequestBody(required = false) CompletarTareaRequest request) {
        CompletarTareaRequest req = (request != null) ? request : new CompletarTareaRequest();
        cursoProcessService.completarTarea(taskId, req);
        return ResponseEntity.ok(ApiResponse.exito("Tarea " + taskId + " completada satisfactoriamente.", null));
    }

    @PostMapping("/tareas/{taskId}/reclamar")
    public ResponseEntity<ApiResponse<Void>> reclamarTarea(
            @PathVariable("taskId") String taskId,
            @RequestParam(name = "usuario") String usuario) {
        cursoProcessService.reclamarTarea(taskId, usuario);
        return ResponseEntity.ok(ApiResponse.exito("Tarea " + taskId + " reclamada exitosamente por " + usuario, null));
    }

    @GetMapping("/cursos/{identificador}/estado")
    public ResponseEntity<ApiResponse<EstadoCursoResponse>> consultarEstadoCurso(
            @PathVariable("identificador") String identificador) {
        EstadoCursoResponse estado = cursoProcessService.consultarEstadoCurso(identificador);
        return ResponseEntity.ok(ApiResponse.exito("Estado del curso obtenido correctamente", estado));
    }

    @GetMapping("/cursos/{identificador}/detalle-ramas")
    public ResponseEntity<ApiResponse<Map<String, Object>>> consultarDetalleRamas(
            @PathVariable("identificador") String identificador) {
        Map<String, Object> ramas = cursoProcessService.obtenerDetalleRamasPreparacion(identificador);
        return ResponseEntity.ok(ApiResponse.exito("Detalle de ramas de preparación obtenido correctamente", ramas));
    }

    @GetMapping("/empresas/{email}/cursos")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listarCursosPorEmpresa(
            @PathVariable("email") String email) {
        List<Map<String, Object>> cursos = cursoProcessService.getCursoExternalService().obtenerCursosPorEmpresa(email);
        return ResponseEntity.ok(ApiResponse.exito("Cursos asociados a la empresa: " + email, cursos));
    }

    @GetMapping("/cursos/{identificador}/detalle-bd")
    public ResponseEntity<ApiResponse<Map<String, Object>>> consultarDetalleBd(
            @PathVariable("identificador") String identificador) {
        Map<String, Object> detalle = cursoProcessService.getCursoExternalService().obtenerCurso(identificador);
        if (detalle == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Curso no encontrado en la base de datos institucional"));
        }
        return ResponseEntity.ok(ApiResponse.exito("Detalle recuperado de la base de datos institucional", detalle));
    }

    // =========================================================================
    // 2. SIMULACIÓN DE COMUNICACIONES DE LA EMPRESA (Sección 4.2.2)
    // =========================================================================

    @GetMapping("/instancias/{processInstanceId}/comunicaciones-habilitadas")
    public ResponseEntity<ApiResponse<List<String>>> obtenerComunicacionesHabilitadas(
            @PathVariable("processInstanceId") String processInstanceId) {
        List<String> habilitadas = cursoProcessService.obtenerComunicacionesHabilitadas(processInstanceId);
        return ResponseEntity.ok(ApiResponse.exito("Comunicaciones habilitadas para la instancia", habilitadas));
    }

    @PostMapping("/instancias/{processInstanceId}/comunicacion/aceptar")
    public ResponseEntity<ApiResponse<Void>> aceptarPropuesta(
            @PathVariable("processInstanceId") String processInstanceId,
            @RequestBody(required = false) Map<String, String> body) {
        String fecha = (body != null) ? body.get("fechaInicioCurso") : null;
        cursoProcessService.aceptarPropuesta(processInstanceId, fecha);
        return ResponseEntity.ok(ApiResponse.exito("Propuesta aceptada con éxito", null));
    }

    @PostMapping("/instancias/{processInstanceId}/comunicacion/rechazar")
    public ResponseEntity<ApiResponse<Void>> rechazarPropuesta(
            @PathVariable("processInstanceId") String processInstanceId,
            @RequestBody(required = false) Map<String, String> body) {
        String motivo = (body != null) ? body.get("motivo") : null;
        cursoProcessService.rechazarPropuesta(processInstanceId, motivo);
        return ResponseEntity.ok(ApiResponse.exito("Propuesta rechazada con éxito", null));
    }

    @PostMapping("/instancias/{processInstanceId}/comunicacion/modificaciones")
    public ResponseEntity<ApiResponse<Void>> solicitarModificaciones(
            @PathVariable("processInstanceId") String processInstanceId,
            @RequestBody(required = false) Map<String, String> body) {
        String observaciones = (body != null) ? body.get("observaciones") : null;
        cursoProcessService.solicitarModificaciones(processInstanceId, observaciones);
        return ResponseEntity.ok(ApiResponse.exito("Modificaciones solicitadas con éxito", null));
    }

    @PostMapping("/instancias/{processInstanceId}/comunicacion/nomina")
    @SuppressWarnings("unchecked")
    public ResponseEntity<ApiResponse<Void>> enviarNomina(
            @PathVariable("processInstanceId") String processInstanceId,
            @RequestBody(required = false) Map<String, Object> body) {
        List<String> nomina = null;
        if (body != null && body.get("nomina") instanceof List) {
            nomina = (List<String>) body.get("nomina");
        }
        cursoProcessService.enviarNomina(processInstanceId, nomina);
        return ResponseEntity.ok(ApiResponse.exito("Nómina de participantes enviada con éxito", null));
    }

    @PostMapping("/instancias/{processInstanceId}/comunicacion/cancelar")
    public ResponseEntity<ApiResponse<Void>> cancelarPreparacion(
            @PathVariable("processInstanceId") String processInstanceId,
            @RequestBody(required = false) Map<String, String> body) {
        String motivo = (body != null) ? body.get("motivo") : null;
        cursoProcessService.cancelarPreparacion(processInstanceId, motivo);
        return ResponseEntity.ok(ApiResponse.exito("Cancelación procesada y compensaciones activadas", null));
    }

    // =========================================================================
    // 3. TOGGLE DE DEMOSTRACIÓN ESCENARIO 7 (Falta de aula física)
    // =========================================================================

    @GetMapping("/simulacion/toggle-falta-aula")
    public ResponseEntity<ApiResponse<Boolean>> obtenerToggleFaltaAula() {
        return ResponseEntity.ok(ApiResponse.exito("Estado de toggle falta de aula", cursoProcessService.isForzarFaltaAulaGlobal()));
    }

    @PostMapping("/simulacion/toggle-falta-aula")
    public ResponseEntity<ApiResponse<Boolean>> toggleFaltaAula(
            @RequestParam("forzar") boolean forzar) {
        cursoProcessService.setForzarFaltaAulaGlobal(forzar);
        return ResponseEntity.ok(ApiResponse.exito("Toggle forzar falta de aula configurado a: " + forzar, forzar));
    }

    @PostMapping("/instancias/{processInstanceId}/avanzar-temporizador")
    public ResponseEntity<ApiResponse<Boolean>> avanzarTemporizador(
            @PathVariable("processInstanceId") String processInstanceId) {
        boolean avanzado = cursoProcessService.avanzarTemporizador(processInstanceId);
        return ResponseEntity.ok(ApiResponse.exito("Temporizador avanzado: " + avanzado, avanzado));
    }

    // =========================================================================
    // 4. DESCARGA DE DOCUMENTOS PDF (Secciones 1.1, 1.4, 4.1)
    // =========================================================================

    @GetMapping("/instancias/{processInstanceId}/pdf/propuesta")
    public ResponseEntity<byte[]> descargarPdfPropuesta(@PathVariable("processInstanceId") String processInstanceId) {
        EstadoCursoResponse estado = cursoProcessService.consultarEstadoCurso(processInstanceId);
        byte[] pdf = pdfGeneratorService.generarPdfPropuesta(estado.getCursoId(), estado.getNombreCurso(), estado.getVariables());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"propuesta-" + estado.getCursoId() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/instancias/{processInstanceId}/pdf/informe")
    public ResponseEntity<byte[]> descargarPdfInforme(@PathVariable("processInstanceId") String processInstanceId) {
        EstadoCursoResponse estado = cursoProcessService.consultarEstadoCurso(processInstanceId);
        byte[] pdf = pdfGeneratorService.generarPdfInformeResultados(estado.getCursoId(), estado.getNombreCurso(), estado.getVariables());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"informe-resultados-" + estado.getCursoId() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    // =========================================================================
    // 5. CONSULTAS HISTÓRICAS Y MÉTRICAS (Sección 4.3)
    // =========================================================================

    @GetMapping("/metricas/resultados")
    public ResponseEntity<ApiResponse<Map<String, Object>>> metricasResultados() {
        return ResponseEntity.ok(ApiResponse.exito("Métricas por resultado", cursoProcessService.obtenerMetricasResultados()));
    }

    @GetMapping("/metricas/duracion-ramas")
    public ResponseEntity<ApiResponse<Map<String, Object>>> metricasDuracionRamas() {
        return ResponseEntity.ok(ApiResponse.exito("Métricas de duración de ramas paralelas", cursoProcessService.obtenerMetricasDuracionRamas()));
    }

    @GetMapping("/metricas/tareas-usuarios")
    public ResponseEntity<ApiResponse<Map<String, Object>>> metricasTareasUsuarios() {
        return ResponseEntity.ok(ApiResponse.exito("Métricas de tareas completadas por usuario", cursoProcessService.obtenerMetricasTareasPorUsuario()));
    }

    @GetMapping("/metricas/completo")
    public ResponseEntity<ApiResponse<Map<String, Object>>> metricasCompletas() {
        Map<String, Object> resumen = Map.of(
                "metricasResultados", cursoProcessService.obtenerMetricasResultados(),
                "metricasDuracionRamas", cursoProcessService.obtenerMetricasDuracionRamas(),
                "metricasTareasUsuarios", cursoProcessService.obtenerMetricasTareasPorUsuario()
        );
        return ResponseEntity.ok(ApiResponse.exito("Resumen integral de métricas históricas", resumen));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> manejarExcepcion(Exception ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }
}
