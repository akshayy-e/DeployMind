package com.deploymind.model;

import java.util.ArrayList;
import java.util.List;

public class Incident {
    private String id;
    private String service;
    private String title;
    private String severity;
    private String status;
    private String deployment;
    private String error;
    private List<String> symptoms = new ArrayList<>();
    private List<String> recentChanges = new ArrayList<>();
    private String createdAt;
    private String rootCause;
    private String resolution;

    public Incident() {}

    public Incident(String id, String service, String title, String severity, String status,
                    String deployment, String error, List<String> symptoms, List<String> recentChanges,
                    String createdAt, String rootCause, String resolution) {
        this.id = id; this.service = service; this.title = title; this.severity = severity;
        this.status = status; this.deployment = deployment; this.error = error;
        this.symptoms = symptoms; this.recentChanges = recentChanges; this.createdAt = createdAt;
        this.rootCause = rootCause; this.resolution = resolution;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getService() { return service; }
    public void setService(String service) { this.service = service; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDeployment() { return deployment; }
    public void setDeployment(String deployment) { this.deployment = deployment; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public List<String> getSymptoms() { return symptoms; }
    public void setSymptoms(List<String> symptoms) { this.symptoms = symptoms; }
    public List<String> getRecentChanges() { return recentChanges; }
    public void setRecentChanges(List<String> recentChanges) { this.recentChanges = recentChanges; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getRootCause() { return rootCause; }
    public void setRootCause(String rootCause) { this.rootCause = rootCause; }
    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
}
