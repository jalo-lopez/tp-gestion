package com.gestion.cursos.dto.response;

import java.util.Date;
import java.util.Map;

/**
 * DTO para la consulta del estado de un curso por parte de la empresa solicitante.
 */
public class EstadoCursoResponse {

    private String cursoId;
    private String processInstanceId;
    private String nombreCurso;
    private String estado;
    private String tareaActual;
    private Date fechaInicio;
    private Date fechaFin;
    private Map<String, Object> variables;
    private Map<String, Object> detalleRamas;

    public EstadoCursoResponse() {
    }

    public EstadoCursoResponse(String cursoId, String processInstanceId, String nombreCurso,
                               String estado, String tareaActual, Date fechaInicio,
                               Date fechaFin, Map<String, Object> variables) {
        this.cursoId = cursoId;
        this.processInstanceId = processInstanceId;
        this.nombreCurso = nombreCurso;
        this.estado = estado;
        this.tareaActual = tareaActual;
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
        this.variables = variables;
    }

    public EstadoCursoResponse(String cursoId, String processInstanceId, String nombreCurso,
                               String estado, String tareaActual, Date fechaInicio,
                               Date fechaFin, Map<String, Object> variables,
                               Map<String, Object> detalleRamas) {
        this(cursoId, processInstanceId, nombreCurso, estado, tareaActual, fechaInicio, fechaFin, variables);
        this.detalleRamas = detalleRamas;
    }

    public String getCursoId() {
        return cursoId;
    }

    public void setCursoId(String cursoId) {
        this.cursoId = cursoId;
    }

    public String getProcessInstanceId() {
        return processInstanceId;
    }

    public void setProcessInstanceId(String processInstanceId) {
        this.processInstanceId = processInstanceId;
    }

    public String getNombreCurso() {
        return nombreCurso;
    }

    public void setNombreCurso(String nombreCurso) {
        this.nombreCurso = nombreCurso;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getTareaActual() {
        return tareaActual;
    }

    public void setTareaActual(String tareaActual) {
        this.tareaActual = tareaActual;
    }

    public Date getFechaInicio() {
        return fechaInicio;
    }

    public void setFechaInicio(Date fechaInicio) {
        this.fechaInicio = fechaInicio;
    }

    public Date getFechaFin() {
        return fechaFin;
    }

    public void setFechaFin(Date fechaFin) {
        this.fechaFin = fechaFin;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, Object> variables) {
        this.variables = variables;
    }

    public Map<String, Object> getDetalleRamas() {
        return detalleRamas;
    }

    public void setDetalleRamas(Map<String, Object> detalleRamas) {
        this.detalleRamas = detalleRamas;
    }
}
