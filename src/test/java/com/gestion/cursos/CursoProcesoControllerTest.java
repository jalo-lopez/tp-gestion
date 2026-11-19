package com.gestion.cursos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestion.cursos.dto.request.CompletarTareaRequest;
import com.gestion.cursos.dto.request.IniciarCursoRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CursoProcesoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Debe iniciar proceso, gestionar tareas y simular comunicación por REST API")
    void testEndpointsCicloDeVidaProceso() throws Exception {
        // 1. Iniciar el proceso via POST /api/v1/cursos/procesos/iniciar
        IniciarCursoRequest request = new IniciarCursoRequest(
                "CURSO-REST-202",
                "Arquitectura Empresarial y BPM",
                "Sistemas de Información",
                "profesor@universidad.edu",
                45,
                "Curso avanzado de integración de procesos"
        );

        MvcResult iniciarResult = mockMvc.perform(post("/api/v1/cursos/procesos/iniciar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exito").value(true))
                .andExpect(jsonPath("$.datos.processInstanceId").isNotEmpty())
                .andExpect(jsonPath("$.datos.businessKey").value("CURSO-REST-202"))
                .andReturn();

        String bodyInicio = iniciarResult.getResponse().getContentAsString();
        Map<?, ?> respMap = objectMapper.readValue(bodyInicio, Map.class);
        Map<?, ?> datosInicio = (Map<?, ?>) respMap.get("datos");
        String processInstanceId = (String) datosInicio.get("processInstanceId");

        assertThat(processInstanceId).isNotNull();

        // 2. Consultar tareas pendientes para la jefa administrativa (admin_laura)
        MvcResult tareasResult = mockMvc.perform(get("/api/v1/cursos/procesos/tareas/pendientes")
                        .param("usuario", "admin_laura"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(true))
                .andExpect(jsonPath("$.datos", hasSize(greaterThanOrEqualTo(1))))
                .andReturn();

        String bodyTareas = tareasResult.getResponse().getContentAsString();
        Map<?, ?> respTareas = objectMapper.readValue(bodyTareas, Map.class);
        var listaTareas = (java.util.List<?>) respTareas.get("datos");
        Map<?, ?> tareaPropuesta = (Map<?, ?>) listaTareas.get(0);
        String taskId = (String) tareaPropuesta.get("taskId");

        // 3. Completar la tarea 'Registrar y Elaborar propuesta'
        CompletarTareaRequest completarReq = new CompletarTareaRequest();
        completarReq.setAprobado(true);
        completarReq.setPrograma("Temario completo");
        completarReq.setPresupuesto("$ 900.000");
        completarReq.setFechasPosibles("Noviembre / Diciembre");

        mockMvc.perform(post("/api/v1/cursos/procesos/tareas/{taskId}/completar", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completarReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(true));

        // 4. Verificar que se habilitó la comunicación de Aceptación/Rechazo/Modificaciones
        mockMvc.perform(get("/api/v1/cursos/procesos/instancias/{id}/comunicaciones-habilitadas", processInstanceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos", hasItem("ACEPTAR_PROPUESTA")));

        // 5. Simular aceptación de la propuesta vía REST
        mockMvc.perform(post("/api/v1/cursos/procesos/instancias/{id}/comunicacion/aceptar", processInstanceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fechaInicioCurso\":\"2026-12-01T10:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(true));

        // 6. Verificar descarga de PDF de la propuesta
        mockMvc.perform(get("/api/v1/cursos/procesos/instancias/{id}/pdf/propuesta", processInstanceId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        // 7. Verificar consulta de cursos por empresa en BD institucional (Sección 3.2 y 4.1.2)
        mockMvc.perform(get("/api/v1/cursos/procesos/empresas/{email}/cursos", "profesor@universidad.edu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(true))
                .andExpect(jsonPath("$.datos", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.datos[0].curso_id").value("CURSO-REST-202"));

        // 8. Verificar detalle persistido en BD institucional
        mockMvc.perform(get("/api/v1/cursos/procesos/cursos/{identificador}/detalle-bd", "CURSO-REST-202"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(true))
                .andExpect(jsonPath("$.datos.programa").value("Temario completo"))
                .andExpect(jsonPath("$.datos.presupuesto").value("$ 900.000"));

        // 9. Verificar detalle de las 3 ramas paralelas (Sección 1.2 y 4.1.2)
        mockMvc.perform(get("/api/v1/cursos/procesos/cursos/{identificador}/detalle-ramas", "CURSO-REST-202"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(true))
                .andExpect(jsonPath("$.datos.ramaInscripciones").exists())
                .andExpect(jsonPath("$.datos.ramaMateriales").exists())
                .andExpect(jsonPath("$.datos.ramaRecursos").exists());

        // 10. Verificar endpoint de métricas
        mockMvc.perform(get("/api/v1/cursos/procesos/metricas/resultados"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(true))
                .andExpect(jsonPath("$.datos.distribucion").isArray());
    }

    @Test
    @DisplayName("Debe autenticar usuarios institucionales y empresas contra Flowable IdentityService")
    void testLoginFlowable() throws Exception {
        // 1. Login exitoso de empresa de prueba creada en Flowable
        mockMvc.perform(post("/api/v1/cursos/procesos/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"contacto@techsolutions.com\",\"password\":\"empresa123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(true))
                .andExpect(jsonPath("$.datos.tipo").value("EMPRESA"))
                .andExpect(jsonPath("$.datos.email").value("contacto@techsolutions.com"));

        // 2. Login exitoso de usuario institucional de Flowable (admin_laura)
        mockMvc.perform(post("/api/v1/cursos/procesos/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin_laura\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(true))
                .andExpect(jsonPath("$.datos.tipo").value("ADMINISTRACION"))
                .andExpect(jsonPath("$.datos.userId").value("admin_laura"));

        // 3. Login fallido con contraseña incorrecta (HTTP 401 Unauthorized)
        mockMvc.perform(post("/api/v1/cursos/procesos/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin_laura\",\"password\":\"clave_erronea\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.exito").value(false));
    }
}
