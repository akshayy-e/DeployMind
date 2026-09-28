package com.deploymind.model;

import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;

public class CreateIncidentRequest {
    @NotBlank private String service;
    @NotBlank private String title;
    @NotBlank private String severity;
    @NotBlank private String deployment;
    @NotBlank private String error;
    private List<String> symptoms = new ArrayList<>();
    private List<String> recentChanges = new ArrayList<>();
    public String getService(){return service;} public void setService(String v){service=v;}
    public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public String getSeverity(){return severity;} public void setSeverity(String v){severity=v;}
    public String getDeployment(){return deployment;} public void setDeployment(String v){deployment=v;}
    public String getError(){return error;} public void setError(String v){error=v;}
    public List<String> getSymptoms(){return symptoms;} public void setSymptoms(List<String> v){symptoms=v==null?new ArrayList<>():v;}
    public List<String> getRecentChanges(){return recentChanges;} public void setRecentChanges(List<String> v){recentChanges=v==null?new ArrayList<>():v;}
}
