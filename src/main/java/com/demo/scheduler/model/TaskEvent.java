package com.demo.scheduler.model;

import java.io.Serializable;

public class TaskEvent implements Serializable {
    private Long taskId;
    private String payload;
    private String createdAt;

    public TaskEvent() {}

    public TaskEvent(Long taskId, String payload, String createdAt) {
        this.taskId = taskId;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    
    @Override
    public String toString() {
        return "TaskEvent{id=" + taskId + ", payload='" + payload + "'}";
    }
}
