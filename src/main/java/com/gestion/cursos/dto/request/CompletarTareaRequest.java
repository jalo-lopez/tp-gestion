package com.gestion.cursos.dto.request;

import java.util.HashMap;
import java.util.Map;

/**
 * DTO para enviar la resolución de una tarea de usuario al motor Flowable.
 */
public class CompletarTareaRequest {

    private Boolean aprobado;
    private String observaciones;
    private String programa;
    private String presupuesto;
    private String fechasPosibles;
    private Map<String, Object> variablesAdicionales;

    public CompletarTareaRequest() {
        this.variablesAdicionales = new HashMap<>();
    }

    public CompletarTareaRequest(Boolean aprobado, String observaciones, Map<String, Object> variablesAdicionales) {
        this.aprobado = aprobado;
        this.observaciones = observaciones;
        this.variablesAdicionales = variablesAdicionales != null ? variablesAdicionales : new HashMap<>();
    }

    /**
     * Construye el mapa consolidado de variables para completar la tarea.
     */
    public Map<String, Object> toVariables() {
        Map<String, Object> vars = new HashMap<>();
        if (variablesAdicionales != null) {
            vars.putAll(variablesAdicionales);
        }
        if (aprobado != null) {
            vars.put("aprobado", aprobado);
        }
        if (observaciones != null) {
            vars.put("observaciones", observaciones);
        }
        if (programa != null) {
            vars.put("programa", programa);
        }
        if (presupuesto != null) {
            vars.put("presupuesto", presupuesto);
        }
        if (fechasPosibles != null) {
            vars.put("fechasPosibles", fechasPosibles);
        }
        return vars;
    }

    public Boolean getAprobado() {
        return aprobado;
    }

    public void setAprobado(Boolean aprobado) {
        this.aprobado = aprobado;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public String getPrograma() {
        return programa;
    }

    public void setPrograma(String programa) {
        this.programa = programa;
    }

    public String getPresupuesto() {
        return presupuesto;
    }

    public void setPresupuesto(String presupuesto) {
        this.presupuesto = presupuesto;
    }

    public String getFechasPosibles() {
        return fechasPosibles;
    }

    public void setFechasPosibles(String fechasPosibles) {
        this.fechasPosibles = fechasPosibles;
    }

    public Map<String, Object> getVariablesAdicionales() {
        return variablesAdicionales;
    }

    public void setVariablesAdicionales(Map<String, Object> variablesAdicionales) {
        this.variablesAdicionales = variablesAdicionales;
    }
}
