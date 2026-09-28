package com.deploymind.model;

import jakarta.validation.constraints.NotBlank;

public class ResolutionRequest {
    @NotBlank private String incidentId;
    @NotBlank private String action;
    @NotBlank private String outcome;
    @NotBlank private String rootCause;
    private String engineerNote = "";

    public String getIncidentId() { return incidentId; }
    public void setIncidentId(String incidentId) { this.incidentId = incidentId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getRootCause() { return rootCause; }
    public void setRootCause(String rootCause) { this.rootCause = rootCause; }
    public String getEngineerNote() { return engineerNote; }
    public void setEngineerNote(String engineerNote) { this.engineerNote = engineerNote; }
}
