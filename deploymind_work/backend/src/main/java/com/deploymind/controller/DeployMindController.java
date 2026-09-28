package com.deploymind.controller;

import com.deploymind.model.AnalysisRequest;
import com.deploymind.model.CreateIncidentRequest;
import com.deploymind.model.Incident;
import com.deploymind.model.ResolutionRequest;
import com.deploymind.service.AgentService;
import com.deploymind.service.HindsightMemoryService;
import com.deploymind.service.IncidentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DeployMindController {
    private final IncidentService incidents;
    private final HindsightMemoryService memory;
    private final AgentService agent;

    public DeployMindController(IncidentService incidents, HindsightMemoryService memory, AgentService agent) {
        this.incidents = incidents; this.memory = memory; this.agent = agent;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "ok");
        out.put("memory_backend", memory.backend());
        out.put("llm_enabled", agent.llmEnabled());
        out.put("llm_provider", agent.llmProvider());
        out.put("llm_model", agent.llmModel());
        out.put("human_approval_required", true);
        return out;
    }

    @GetMapping("/incidents")
    public List<Incident> incidents() { return incidents.all(); }

    @PostMapping("/incidents")
    public Incident createIncident(@Valid @RequestBody CreateIncidentRequest req) { return incidents.create(req); }

    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        List<Incident> all = incidents.all();
        long resolved = all.stream().filter(i -> "RESOLVED".equals(i.getStatus())).count();
        long open = all.stream().filter(i -> "OPEN".equals(i.getStatus())).count();
        return Map.of("total_incidents", all.size(), "resolved", resolved, "open", open,
                "memory_backend", memory.backend(), "llm_enabled", agent.llmEnabled(), "llm_provider", agent.llmProvider(), "llm_model", agent.llmModel(), "human_approval_required", true,
                "categories", all.stream().map(agent::classify).distinct().sorted().toList());
    }

    @PostMapping("/judge-challenge")
    public Incident judgeChallenge() {
        return incidents.createChallenge();
    }

    @PostMapping("/seed-demo")
    public Map<String, Object> seed() {
        List<String> payloads = incidents.all().stream().filter(i -> "RESOLVED".equals(i.getStatus())).map(this::memoryPayload).toList();
        return Map.of("backend", memory.backend(), "results", memory.seed(payloads));
    }

    @PostMapping("/analyze")
    public Map<String, Object> analyze(@Valid @RequestBody AnalysisRequest req) {
        Incident i = incidents.find(req.getIncidentId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found"));
        List<Map<String, Object>> memories = new ArrayList<>();
        if (req.isUseMemory()) {
            String q = i.getService() + " " + i.getError() + " " + String.join(",", i.getSymptoms()) + " " + String.join(",", i.getRecentChanges()) + " root cause resolution";
            memories = memory.recall(q, 5);
        }
        // Compare the same LLM with and without retrieved memory. This makes the
        // hackathon before/after demo fair: the only changing input is Hindsight context.
        Map<String, Object> result = agent.analyze(i, memories);
        Map<String, Object> baseline = req.isUseMemory()
                ? agent.analyze(i, List.of())
                : result;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("incident_id", i.getId()); out.put("memory_enabled", req.isUseMemory()); out.put("summary", i.getTitle());
        out.put("category", result.getOrDefault("category", agent.classify(i)));
        out.put("recommendation", result.get("recommendation")); out.put("confidence", result.get("confidence"));
        out.put("rationale", result.get("rationale"));
        out.put("historical_insights", result.getOrDefault("historical_insights", List.of()));
        out.put("risk_flags", result.getOrDefault("risk_flags", List.of()));
        out.put("llm_used", result.getOrDefault("llm_used", false));
        out.put("llm_provider", result.getOrDefault("llm_provider", agent.llmProvider()));
        out.put("llm_model", result.getOrDefault("llm_model", agent.llmModel()));
        out.put("investigation_steps", result.getOrDefault("investigation_steps", List.of()));
        out.put("evidence_to_check", result.getOrDefault("evidence_to_check", List.of()));
        out.put("memory_relevance", result.getOrDefault("memory_relevance", memories.isEmpty() ? "NONE" : "MATCHED"));
        out.put("memories", memories);
        out.put("generic_recommendation", baseline.getOrDefault("recommendation", agent.generic(i)));
        out.put("baseline_llm_used", baseline.getOrDefault("llm_used", false));
        out.put("baseline_llm_provider", baseline.getOrDefault("llm_provider", agent.llmProvider()));
        return out;
    }

    @PostMapping("/resolve")
    public Map<String, Object> resolve(@Valid @RequestBody ResolutionRequest req) {
        Incident i = incidents.find(req.getIncidentId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found"));
        i.setStatus("RESOLVED"); i.setRootCause(req.getRootCause()); i.setResolution(req.getAction());
        String content = "Verified production outcome for " + i.getId() + ". Service: " + i.getService() + ". Error: " + i.getError() +
                ". Recent changes: " + String.join(", ", i.getRecentChanges()) + ". Root cause: " + req.getRootCause() +
                ". Action: " + req.getAction() + ". Outcome: " + req.getOutcome() + ". Engineer note: " + req.getEngineerNote() + ".";
        Map<String, Object> stored = memory.retain(content, Map.of("incident_id", i.getId(), "outcome", req.getOutcome()));
        return Map.of("retained", Boolean.TRUE.equals(stored.get("stored")), "backend", stored.get("backend"), "message", "Resolution retained for future incidents.");
    }

    private String memoryPayload(Incident i) {
        return "Incident " + i.getId() + ". Service: " + i.getService() + ". Title: " + i.getTitle() + ". Deployment: " + i.getDeployment() +
                ". Error: " + i.getError() + ". Symptoms: " + String.join(", ", i.getSymptoms()) + ". Recent changes: " +
                String.join(", ", i.getRecentChanges()) + ". Root cause: " + i.getRootCause() + ". Resolution: " + i.getResolution() + ". Outcome: successful.";
    }
}
