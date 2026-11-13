package com.gestion.cursos.dto.response;

import java.time.LocalDateTime;

/**
 * DTO devuelto tras el inicio exitoso de una instancia de proceso BPMN.
 */
public class ProcesoIniciadoResponse {

    private String processInstanceId;
    private String businessKey;
    private String processDefinitionKey;
    private String estado;
    private LocalDateTime fechaInicio;

    public ProcesoIniciadoResponse() {
    }

    public ProcesoIniciadoResponse(String processInstanceId, String businessKey,
                                   String processDefinitionKey, String estado, LocalDateTime fechaInicio) {
        this.processInstanceId = processInstanceId;
        this.businessKey = businessKey;
        this.processDefinitionKey = processDefinitionKey;
        this.estado = estado;
        this.fechaInicio = fechaInicio;
    }

    public String getProcessInstanceId() {
        return processInstanceId;
    }

    public void setProcessInstanceId(String processInstanceId) {
        this.processInstanceId = processInstanceId;
    }

    public String getBusinessKey() {
        return businessKey;
    }

    public void setBusinessKey(String businessKey) {
        this.businessKey = businessKey;
    }

    public String getProcessDefinitionKey() {
        return processDefinitionKey;
    }

    public void setProcessDefinitionKey(String processDefinitionKey) {
        this.processDefinitionKey = processDefinitionKey;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public LocalDateTime getFechaInicio() {
        return fechaInicio;
    }

    public void setFechaInicio(LocalDateTime fechaInicio) {
        this.fechaInicio = fechaInicio;
    }
}
