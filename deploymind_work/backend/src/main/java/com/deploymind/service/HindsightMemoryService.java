package com.deploymind.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class HindsightMemoryService {
    private final RestClient client;
    private final String apiKey;
    private final String bankId;
    private final List<LocalMemory> localMemories = new CopyOnWriteArrayList<>();

    public HindsightMemoryService(
            @Value("${hindsight.api-url}") String apiUrl,
            @Value("${hindsight.api-key}") String apiKey,
            @Value("${hindsight.bank-id}") String bankId) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.bankId = bankId;
        this.client = RestClient.builder().baseUrl(apiUrl).build();
    }

    public String backend() { return apiKey.isBlank() ? "local-demo" : "hindsight"; }

    public Map<String, Object> retain(String content, Map<String, String> metadata) {
        if (!apiKey.isBlank()) {
            try {
                Map<String, Object> item = new HashMap<>();
                item.put("content", content);
                item.put("context", "production incident postmortem");
                Map<String, Object> body = Map.of("items", List.of(item));
                Map<String, Object> response = client.post()
                        .uri("/v1/default/banks/{bankId}/memories", bankId)
                        .header("Authorization", "Bearer " + apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});
                return Map.of("stored", true, "backend", "hindsight", "detail", response == null ? "" : response);
            } catch (Exception e) {
                return Map.of("stored", false, "backend", "hindsight", "error", e.getMessage() == null ? "Hindsight request failed" : e.getMessage());
            }
        }

        localMemories.add(new LocalMemory(content, metadata == null ? Map.of() : metadata));
        return Map.of("stored", true, "backend", "local-demo");
    }

    public List<Map<String, Object>> recall(String query, int limit) {
        if (!apiKey.isBlank()) {
            try {
                Map<String, Object> body = Map.of("query", query, "max_tokens", 3000, "budget", "mid");
                Map<String, Object> response = client.post()
                        .uri("/v1/default/banks/{bankId}/memories/recall", bankId)
                        .header("Authorization", "Bearer " + apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});

                if (response == null || !(response.get("results") instanceof List<?> results)) return List.of();
                List<Map<String, Object>> out = new ArrayList<>();
                for (Object raw : results) {
                    if (!(raw instanceof Map<?, ?> m)) continue;
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", String.valueOf(valueOrEmpty(m, "id")));
                    item.put("text", String.valueOf(valueOrEmpty(m, "text")));
                    item.put("type", String.valueOf(valueOrEmpty(m, "type")));
                    item.put("source", "hindsight");
                    out.add(item);
                    if (out.size() >= limit) break;
                }
                return out;
            } catch (Exception e) {
                return List.of(Map.of("text", "Hindsight recall error: " + e.getMessage(), "source", "error"));
            }
        }

        String[] queryWords = query.toLowerCase().replace(",", " ").split("\\s+");
        List<ScoredMemory> scored = new ArrayList<>();
        for (int i = 0; i < localMemories.size(); i++) {
            String content = localMemories.get(i).content().toLowerCase();
            int score = 0;
            for (String word : queryWords) if (!word.isBlank() && content.contains(word)) score++;
            if (score > 0) scored.add(new ScoredMemory(score, localMemories.get(i), i));
        }
        scored.sort(Comparator.comparingInt(ScoredMemory::score).reversed());
        List<Map<String, Object>> out = new ArrayList<>();
        for (ScoredMemory sm : scored.stream().limit(limit).toList()) {
            out.add(Map.of("id", "local-" + sm.index(), "text", sm.memory().content(), "type", "experience", "source", "local-demo", "score", sm.score()));
        }
        return out;
    }

    public List<Map<String, Object>> seed(List<String> payloads) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (String payload : payloads) results.add(retain(payload, Map.of("seed", "true")));
        return results;
    }

    private static Object valueOrEmpty(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : value;
    }

    private record LocalMemory(String content, Map<String, String> metadata) {}
    private record ScoredMemory(int score, LocalMemory memory, int index) {}
}
