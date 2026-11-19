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

        // 2. Consultar las tareas pendientes asignadas a admin_laura (Jefe administrativo)
        MvcResult queryResult = mockMvc.perform(get("/api/v1/cursos/procesos/tareas/pendientes")
                        .param("usuario", "admin_laura"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(true))
                .andReturn();

        String responseBody = queryResult.getResponse().getContentAsString();
        Map<?, ?> map = objectMapper.readValue(responseBody, Map.class);
        java.util.List<?> datos = (java.util.List<?>) map.get("datos");

        String taskIdEncontrada = null;
        for (Object obj : datos) {
            Map<?, ?> tarea = (Map<?, ?>) obj;
            if ("Registrar y Elaborar propuesta".equals(tarea.get("taskName"))
                    && processInstanceId.equals(tarea.get("processInstanceId"))) {
                taskIdEncontrada = (String) tarea.get("taskId");
                break;
            }
        }

        assertThat(taskIdEncontrada).isNotNull();

        // 3. Completar la tarea 'Registrar y Elaborar propuesta'
        CompletarTareaRequest completarReq = new CompletarTareaRequest();
        completarReq.setAprobado(true);
        completarReq.setObservaciones("Propuesta elaborada desde test REST");
        completarReq.setVariablesAdicionales(Map.of("presupuesto", "$ 900.000", "programa", "Temario completo"));

        mockMvc.perform(post("/api/v1/cursos/procesos/tareas/{taskId}/completar", taskIdEncontrada)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completarReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(true));

        // 4. Consultar comunicaciones habilitadas (debe incluir ACEPTAR_PROPUESTA)
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

        // 9. Verificar endpoint de métricas
        mockMvc.perform(get("/api/v1/cursos/procesos/metricas/resultados"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(true))
                .andExpect(jsonPath("$.datos.distribucion").isArray());
    }
}
