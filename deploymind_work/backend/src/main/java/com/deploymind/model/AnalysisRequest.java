package com.deploymind.model;

import jakarta.validation.constraints.NotBlank;

public class AnalysisRequest {
    @NotBlank
    private String incidentId;
    private boolean useMemory = true;

    public String getIncidentId() { return incidentId; }
    public void setIncidentId(String incidentId) { this.incidentId = incidentId; }
    public boolean isUseMemory() { return useMemory; }
    public void setUseMemory(boolean useMemory) { this.useMemory = useMemory; }
}
