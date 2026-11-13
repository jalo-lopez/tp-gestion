package com.gestion.cursos.dto.response;

import java.util.Date;
import java.util.Map;

/**
 * DTO que representa una tarea humana (UserTask) pendiente en Flowable.
 */
public class TareaResponse {

    private String taskId;
    private String taskName;
    private String assignee;
    private String processInstanceId;
    private Date createTime;
    private Date dueDate;
    private Map<String, Object> variables;

    public TareaResponse() {
    }

    public TareaResponse(String taskId, String taskName, String assignee,
                         String processInstanceId, Date createTime, Date dueDate,
                         Map<String, Object> variables) {
        this.taskId = taskId;
        this.taskName = taskName;
        this.assignee = assignee;
        this.processInstanceId = processInstanceId;
        this.createTime = createTime;
        this.dueDate = dueDate;
        this.variables = variables;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getAssignee() {
        return assignee;
    }

    public void setAssignee(String assignee) {
        this.assignee = assignee;
    }

    public String getProcessInstanceId() {
        return processInstanceId;
    }

    public void setProcessInstanceId(String processInstanceId) {
        this.processInstanceId = processInstanceId;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getDueDate() {
        return dueDate;
    }

    public void setDueDate(Date dueDate) {
        this.dueDate = dueDate;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, Object> variables) {
        this.variables = variables;
    }
}
