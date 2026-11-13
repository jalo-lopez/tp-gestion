package com.gestion.cursos.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.HashMap;
import java.util.Map;

/**
 * DTO para la solicitud de inicio de proceso proveniente del formulario web.
 */
public class IniciarCursoRequest {

    @NotBlank(message = "El código o ID del curso es obligatorio")
    private String cursoId;

    @NotBlank(message = "El nombre del curso es obligatorio")
    private String nombreCurso;

    @NotBlank(message = "La categoría es obligatoria")
    private String categoria;

    @NotBlank(message = "El email del docente o solicitante es obligatorio")
    private String emailSolicitante;

    @NotNull(message = "El cupo máximo es obligatorio")
    @Positive(message = "El cupo máximo debe ser mayor a 0")
    private Integer cupoMaximo;

    private String descripcion;
    private Map<String, Object> variablesAdicionales = new HashMap<>();

    public IniciarCursoRequest() {
    }

    public IniciarCursoRequest(String cursoId, String nombreCurso, String categoria,
                               String emailSolicitante, Integer cupoMaximo, String descripcion) {
        this.cursoId = cursoId;
        this.nombreCurso = nombreCurso;
        this.categoria = categoria;
        this.emailSolicitante = emailSolicitante;
        this.cupoMaximo = cupoMaximo;
        this.descripcion = descripcion;
    }

    /**
     * Convierte el DTO a un mapa de variables para el motor de procesos BPMN.
     */
    public Map<String, Object> toProcessVariables() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("cursoId", this.cursoId);
        variables.put("nombreCurso", this.nombreCurso);
        variables.put("categoria", this.categoria);
        variables.put("emailSolicitante", this.emailSolicitante);
        variables.put("cupoMaximo", this.cupoMaximo);
        variables.put("descripcion", this.descripcion);
        variables.put("estado", "INICIADO");
        if (this.variablesAdicionales != null) {
            variables.putAll(this.variablesAdicionales);
        }
        return variables;
    }

    public String getCursoId() {
        return cursoId;
    }

    public void setCursoId(String cursoId) {
        this.cursoId = cursoId;
    }

    public String getNombreCurso() {
        return nombreCurso;
    }

    public void setNombreCurso(String nombreCurso) {
        this.nombreCurso = nombreCurso;
    }

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    public String getEmailSolicitante() {
        return emailSolicitante;
    }

    public void setEmailSolicitante(String emailSolicitante) {
        this.emailSolicitante = emailSolicitante;
    }

    public Integer getCupoMaximo() {
        return cupoMaximo;
    }

    public void setCupoMaximo(Integer cupoMaximo) {
        this.cupoMaximo = cupoMaximo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public Map<String, Object> getVariablesAdicionales() {
        return variablesAdicionales;
    }

    public void setVariablesAdicionales(Map<String, Object> variablesAdicionales) {
        this.variablesAdicionales = variablesAdicionales;
    }
}
