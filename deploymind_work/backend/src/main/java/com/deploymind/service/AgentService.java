package com.deploymind.service;

import com.deploymind.model.Incident;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AgentService {
    private final LlmService llm;

    public AgentService(LlmService llm) {
        this.llm = llm;
    }

    /**
     * Deterministic evidence-first fallback. It remains available when the LLM
     * provider is not configured or temporarily unavailable.
     */
    public String generic(Incident i) {
        return buildPlan(i).recommendation();
    }

    public boolean llmEnabled() {
        return llm.enabled();
    }

    public String llmProvider() {
        return llm.provider();
    }

    public String llmModel() {
        return llm.model();
    }

    public Map<String, Object> analyze(Incident i, List<Map<String, Object>> memories) {
        String category = classify(i);
        List<Map<String, Object>> relevant = filterRelevantMemories(i, memories);

        Map<String, Object> llmResult = callLlm(i, relevant, category);
        if (llmResult != null) {
            llmResult.put("llm_used", true);
            llmResult.put("llm_provider", llm.provider());
            llmResult.put("llm_model", llm.model());
            llmResult.putIfAbsent("category", category);
            llmResult.putIfAbsent("memory_relevance", relevant.isEmpty() ? "NONE" : "MATCHED");
            return llmResult;
        }

        InvestigationPlan plan = buildPlan(i);
        List<String> rationale = new ArrayList<>(plan.rationale());
        String confidence = relevant.isEmpty() ? "MEDIUM" : "HIGH";
        String recommendation = plan.recommendation();

        if (!relevant.isEmpty()) {
            recommendation = memoryAwareRecommendation(i, plan, relevant);
            rationale.add(0, "Recalled experience matches the current " + category + " incident pattern.");
            rationale.add("Historical actions are treated as evidence, not as automatic commands.");
        } else {
            rationale.add(0, "No sufficiently relevant historical experience was found for this incident category.");
            rationale.add("The recommendation is derived from the current incident evidence only.");
        }
        rationale.add("Human approval is required before any production remediation.");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("category", category);
        out.put("recommendation", recommendation);
        out.put("confidence", confidence);
        out.put("rationale", rationale);
        out.put("investigation_steps", plan.steps());
        out.put("evidence_to_check", plan.evidence());
        out.put("memory_relevance", relevant.isEmpty() ? "NONE" : "MATCHED");
        out.put("historical_insights", List.of());
        out.put("risk_flags", List.of("LLM not configured or unavailable; deterministic evidence-first fallback used."));
        out.put("llm_used", false);
        out.put("llm_provider", llm.provider());
        out.put("llm_model", llm.model());
        return out;
    }

    /** Alias kept small so the public API remains stable while the provider is abstracted. */
    private Map<String, Object> callLlm(Incident i, List<Map<String, Object>> memories, String category) {
        String historical = memories.isEmpty()
                ? "No relevant historical memory was retrieved."
                : memories.stream()
                .map(m -> "- " + String.valueOf(m.getOrDefault("text", "")))
                .reduce("", (a, b) -> a + "\n" + b);

        String systemPrompt = """
                You are DeployMind, a production incident-response copilot for DevOps engineers.

                You do NOT execute production actions. A human engineer makes the final decision.
                Use current incident facts as the primary source of truth.
                Treat historical Hindsight memories as supporting evidence, not guaranteed truth.
                Never invent current infrastructure state, metrics, logs, capacity, or completed actions.
                Clearly separate facts, historical evidence, hypotheses, and recommended checks.
                Prefer reversible, evidence-gathering steps before risky remediation.
                If no relevant memory exists, say so.
                """;

        String userPrompt = """
                CURRENT INCIDENT
                Incident ID: %s
                Service: %s
                Title: %s
                Severity: %s
                Deployment: %s
                Error: %s
                Symptoms: %s
                Recent changes: %s
                Classified category: %s

                RELEVANT HINDSIGHT MEMORY
                %s

                TASK
                1. Summarize the incident using only current facts.
                2. Identify plausible causes and label them as hypotheses unless verified.
                3. Explain which historical memories are genuinely relevant and why.
                4. Give concrete investigation steps.
                5. Give a safe recommended next action, without claiming it has been executed.
                6. List evidence that should be checked before remediation.
                7. List important risk flags or uncertainty.
                """.formatted(
                i.getId(), i.getService(), i.getTitle(), i.getSeverity(), i.getDeployment(),
                i.getError(), String.join("; ", safe(i.getSymptoms())),
                String.join("; ", safe(i.getRecentChanges())), category, historical
        );

        return llm.generateStructured(systemPrompt, userPrompt, responseSchema());
    }

    private Map<String, Object> responseSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<>(Map.of(
                "category", Map.of("type", "string"),
                "recommendation", Map.of("type", "string"),
                "confidence", Map.of("type", "string", "enum", List.of("LOW", "MEDIUM", "HIGH")),
                "rationale", Map.of("type", "array", "items", Map.of("type", "string")),
                "investigation_steps", Map.of("type", "array", "items", Map.of("type", "string")),
                "evidence_to_check", Map.of("type", "array", "items", Map.of("type", "string")),
                "memory_relevance", Map.of("type", "string", "enum", List.of("NONE", "PARTIAL", "MATCHED")),
                "historical_insights", Map.of("type", "array", "items", Map.of("type", "string")),
                "risk_flags", Map.of("type", "array", "items", Map.of("type", "string"))
        )));
        schema.put("required", List.of(
                "category", "recommendation", "confidence", "rationale",
                "investigation_steps", "evidence_to_check", "memory_relevance",
                "historical_insights", "risk_flags"
        ));
        schema.put("additionalProperties", false);
        return schema;
    }

    public String classify(Incident i) {
        String t = incidentText(i).toLowerCase(Locale.ROOT);
        if (containsAny(t, "redis", "cache", "session store", "cache miss")) return "REDIS_CACHE";
        if (containsAny(t, "kafka", "consumer lag", "partition", "broker", "queue", "message backlog")) return "MESSAGING";
        if (containsAny(t, "401", "403", "unauthorized", "forbidden", "authentication", "authorization", "jwt", "token", "oauth")) return "AUTHENTICATION";
        if (containsAny(t, "out of memory", "oom", "heap", "gc overhead", "memory leak")) return "MEMORY";
        if (containsAny(t, "cpu", "high load", "load average", "throttling", "throttled")) return "CPU";
        if (containsAny(t, "disk", "storage", "no space", "filesystem", "file system", "volume full")) return "STORAGE";
        if (containsAny(t, "database", "db", "sql", "postgres", "mysql", "oracle", "connection pool", "connection timeout", "deadlock", "query timeout")) return "DATABASE";
        if (containsAny(t, "dns", "network", "connection refused", "connection reset", "unreachable", "packet loss", "tcp")) return "NETWORK";
        if (containsAny(t, "5xx", "500", "502", "503", "504", "internal server", "gateway", "service unavailable")) return "HTTP_SERVER_ERROR";
        if (containsAny(t, "latency", "slow", "timed out", "timeout", "response time", "p95", "p99")) return "LATENCY";
        return "UNKNOWN";
    }

    private InvestigationPlan buildPlan(Incident i) {
        String category = classify(i);
        return switch (category) {
            case "REDIS_CACHE" -> new InvestigationPlan(
                    "Inspect Redis availability, connection behavior, timeout settings, retry/backoff policy and recent client changes before changing cache configuration.",
                    List.of("Check Redis health and connection count", "Compare timeout/retry settings with the previous release", "Measure retry amplification and cache error rate", "Verify whether the recent deployment introduced the change"),
                    List.of("Redis connection errors", "retry count", "cache hit/miss rate", "client configuration diff"),
                    List.of("Redis/cache symptoms are present", "Recent client or retry-policy changes are relevant", "Restarting without identifying retry amplification can hide the cause"));
            case "MESSAGING" -> new InvestigationPlan(
                    "Investigate consumer lag, partition distribution, broker health, consumer errors and concurrency/retry configuration before restarting consumers or changing capacity.",
                    List.of("Check consumer lag by partition", "Inspect broker health and rebalance events", "Compare consumer concurrency and retry settings", "Identify whether message processing time increased after deployment"),
                    List.of("consumer lag", "partition skew", "rebalance count", "processing latency", "consumer errors"),
                    List.of("Messaging/queue symptoms are present", "Recent consumer or producer configuration changes matter", "A restart alone may not address sustained lag"));
            case "AUTHENTICATION" -> new InvestigationPlan(
                    "Trace the authentication failure to token validation, identity-provider health, issuer/audience configuration or permission changes before rotating credentials.",
                    List.of("Group failures by endpoint and identity provider", "Validate token issuer, audience and expiry", "Check recent secret/role/policy changes", "Compare successful and failing authentication requests"),
                    List.of("401/403 rate", "token claims", "issuer/audience", "identity-provider status", "permission changes"),
                    List.of("Authentication evidence is present", "Recent auth configuration changes are relevant", "Credential rotation should follow evidence, not precede it"));
            case "MEMORY" -> new InvestigationPlan(
                    "Investigate heap pressure, allocation rate, garbage collection and container limits, then correlate the spike with the latest code or dependency change.",
                    List.of("Check heap and non-heap usage", "Inspect GC frequency and pause time", "Compare allocation behavior before and after deployment", "Look for retained objects or a new high-volume workload"),
                    List.of("heap usage", "GC pressure", "allocation rate", "container memory limit", "deployment diff"),
                    List.of("Memory-pressure evidence is present", "A restart may only mask the leak", "Recent code/dependency changes should be correlated"));
            case "CPU" -> new InvestigationPlan(
                    "Identify the workload causing CPU saturation by endpoint, instance and thread pool before scaling or restarting the service.",
                    List.of("Break CPU down by instance and endpoint", "Check request rate and expensive operations", "Inspect thread-pool saturation", "Compare CPU behavior before and after the latest deployment"),
                    List.of("CPU by instance", "request rate", "hot endpoints", "thread count", "deployment diff"),
                    List.of("CPU saturation is the current signal", "Workload and code changes must be correlated", "Scaling without finding the hot workload can increase cost without fixing the cause"));
            case "STORAGE" -> new InvestigationPlan(
                    "Find what is consuming storage—logs, temporary files, database growth or artifacts—before deleting data or increasing the volume.",
                    List.of("Identify top directories/files", "Check log rotation and temporary-file growth", "Inspect database/storage growth", "Compare storage consumption with the latest deployment"),
                    List.of("filesystem utilization", "growth rate", "largest files", "log volume", "database size"),
                    List.of("Storage pressure is the current signal", "The source of growth should be known before cleanup", "Capacity expansion alone may not stop recurring growth"));
            case "DATABASE" -> new InvestigationPlan(
                    "Trace the database failure through connections, pool saturation, query latency, locks and recent schema/configuration changes before choosing rollback or restart.",
                    List.of("Check active connections and pool saturation", "Inspect slow queries and lock waits", "Compare schema/configuration changes", "Verify application connection lifecycle"),
                    List.of("active connections", "pool wait time", "query latency", "locks", "schema/config diff"),
                    List.of("Database evidence is present", "Connection or query behavior should be isolated", "A pool-size increase can mask a connection leak"));
            case "NETWORK" -> new InvestigationPlan(
                    "Localize the network failure by checking DNS, reachability, connection resets and the affected dependency path before restarting application components.",
                    List.of("Test DNS resolution", "Check reachability from affected instances", "Inspect connection reset/refused patterns", "Trace the failing dependency path"),
                    List.of("DNS results", "TCP resets/refusals", "dependency health", "packet loss", "route path"),
                    List.of("Network evidence is present", "The failing hop should be localized", "Restarting before localization can erase useful evidence"));
            case "HTTP_SERVER_ERROR" -> new InvestigationPlan(
                    "Localize the 5xx failure across gateway, application and dependencies, then correlate onset with the latest deployment or configuration diff.",
                    List.of("Group 5xx responses by endpoint", "Inspect gateway and application logs", "Check dependency health", "Compare error onset with deployment timing"),
                    List.of("status-code distribution", "gateway logs", "application exceptions", "dependency health", "deployment timestamp"),
                    List.of("HTTP server-error evidence is present", "The failing layer must be isolated", "Rollback should follow evidence that the deployment introduced the failure"));
            case "LATENCY" -> new InvestigationPlan(
                    "Locate where latency begins—application, database, cache, network or downstream service—before applying remediation.",
                    List.of("Compare p50/p95/p99 by endpoint", "Break latency down by dependency", "Check traffic and resource saturation", "Compare latency before and after the latest change"),
                    List.of("p95/p99", "endpoint latency", "downstream timing", "traffic volume", "resource saturation"),
                    List.of("Latency is the current symptom", "The slowest dependency should be identified", "A restart without localization is low-evidence remediation"));
            default -> new InvestigationPlan(
                    "Correlate the error, symptoms, dependency health and recent changes to form and validate a specific hypothesis before choosing remediation.",
                    List.of("Normalize the error and symptoms", "Check service and dependency health", "Compare the latest deployment/configuration diff", "Validate the strongest hypothesis with evidence"),
                    List.of("logs", "metrics", "dependency health", "deployment diff", "timeline"),
                    List.of("No specialized incident pattern was confidently identified", "The agent should avoid inventing a root cause", "Human approval remains required"));
        };
    }

    private String memoryAwareRecommendation(Incident i, InvestigationPlan plan, List<Map<String, Object>> memories) {
        return switch (classify(i)) {
            case "REDIS_CACHE" -> "Compare the current Redis client/retry evidence with the recalled cache incident. If the retry behavior matches the historical failure, validate bounded backoff and the configuration diff before approving a controlled change.";
            case "MESSAGING" -> "Compare current consumer lag, partition and concurrency evidence with the recalled messaging incident. If the failure pattern matches, validate consumer configuration and broker health before changing concurrency.";
            case "AUTHENTICATION" -> "Compare current token/identity evidence with the recalled authentication incident. Validate issuer, audience, expiry and permission changes before changing credentials.";
            case "MEMORY" -> "Compare current heap/GC evidence with the recalled memory incident. Validate whether the same allocation or deployment pattern is present before changing limits or restarting instances.";
            case "CPU" -> "Compare the current hot workload and deployment diff with the recalled CPU incident. Validate the expensive endpoint or thread workload before scaling or restarting.";
            case "STORAGE" -> "Compare the current storage growth source with the recalled storage incident. Validate whether the same log, temporary-file or data-growth pattern exists before cleanup or capacity changes.";
            case "DATABASE" -> "Compare current connection/pool/query evidence with the recalled database incidents. Check whether the same configuration or connection-lifecycle pattern is present before choosing rollback or pool changes.";
            case "NETWORK" -> "Compare the current failing network path with the recalled network incident. Validate DNS/reachability/reset evidence before restarting application components.";
            case "HTTP_SERVER_ERROR" -> "Compare the failing endpoint, dependency and deployment timeline with the recalled 5xx incident. Validate the failing layer before approving a rollback or configuration change.";
            case "LATENCY" -> "Compare the current latency breakdown with the recalled performance incident. Validate the same slow dependency or workload pattern before applying remediation.";
            default -> "Use the recalled experience as a hypothesis only: compare its symptoms, failed actions, successful actions and root cause with the current evidence, then choose a controlled remediation.";
        };
    }

    private List<Map<String, Object>> filterRelevantMemories(Incident i, List<Map<String, Object>> memories) {
        String category = classify(i);
        String[] keys = switch (category) {
            case "REDIS_CACHE" -> new String[]{"redis", "cache", "session", "retry", "backoff"};
            case "MESSAGING" -> new String[]{"kafka", "consumer", "queue", "partition", "lag", "message"};
            case "AUTHENTICATION" -> new String[]{"auth", "401", "403", "token", "jwt", "identity", "permission"};
            case "MEMORY" -> new String[]{"memory", "heap", "oom", "gc", "allocation"};
            case "CPU" -> new String[]{"cpu", "load", "thread", "throttl"};
            case "STORAGE" -> new String[]{"disk", "storage", "filesystem", "log", "space"};
            case "DATABASE" -> new String[]{"database", "db", "sql", "mysql", "postgres", "connection", "pool", "query"};
            case "NETWORK" -> new String[]{"network", "dns", "connection refused", "connection reset", "packet"};
            case "HTTP_SERVER_ERROR" -> new String[]{"5xx", "500", "502", "503", "504", "gateway", "server error"};
            case "LATENCY" -> new String[]{"latency", "slow", "timeout", "response time", "p95", "p99"};
            default -> new String[]{"incident", "error", "deployment", "root cause"};
        };

        List<Map<String, Object>> out = new ArrayList<>();
        if (memories == null) return out;

        for (Map<String, Object> m : memories) {
            String source = String.valueOf(m.getOrDefault("source", ""));
            if ("error".equalsIgnoreCase(source)) continue;
            String text = String.valueOf(m.getOrDefault("text", "")).toLowerCase(Locale.ROOT);
            long hits = Arrays.stream(keys).filter(text::contains).count();
            boolean sameService = i.getService() != null && text.contains(i.getService().toLowerCase(Locale.ROOT));
            if (hits >= (keys.length <= 2 ? 1 : 2) || (sameService && hits >= 1)) out.add(m);
        }
        return out.stream().limit(5).toList();
    }

    private boolean containsAny(String text, String... terms) {
        for (String term : terms) if (text.contains(term)) return true;
        return false;
    }

    private String incidentText(Incident i) {
        return safeValue(i.getService()) + " " + safeValue(i.getTitle()) + " " + safeValue(i.getError()) + " " +
                String.join(" ", safe(i.getSymptoms())) + " " + String.join(" ", safe(i.getRecentChanges()));
    }

    private List<String> safe(List<String> values) {
        return values == null ? List.of() : values.stream().filter(Objects::nonNull).toList();
    }

    private String safeValue(String value) {
        return value == null ? "" : value;
    }

    private record InvestigationPlan(String recommendation, List<String> steps, List<String> evidence, List<String> rationale) {}
}
