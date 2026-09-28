package com.deploymind.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small OpenAI-compatible LLM adapter. DeployMind defaults to Groq, but the
 * endpoint can be replaced with another OpenAI-compatible provider through env vars.
 */
@Service
public class LlmService {
    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final String apiUrl;

    public LlmService(
            @Value("${llm.api-url}") String apiUrl,
            @Value("${llm.api-key}") String apiKey,
            @Value("${llm.model}") String model,
            ObjectMapper objectMapper) {
        this.apiUrl = apiUrl == null ? "" : apiUrl.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null ? "" : model.trim();
        this.objectMapper = objectMapper;
        this.client = RestClient.builder().build();
    }

    public boolean enabled() {
        return !apiUrl.isBlank() && !apiKey.isBlank() && !model.isBlank();
    }

    public String model() {
        return model;
    }

    public String provider() {
        if (apiUrl.contains("groq.com")) return "Groq";
        return "OpenAI-compatible";
    }

    /**
     * Uses Groq's Structured Outputs when available so the backend receives
     * predictable JSON instead of brittle free-form text.
     */
    public Map<String, Object> generateStructured(
            String systemPrompt,
            String userPrompt,
            Map<String, Object> schema) {

        if (!enabled()) return null;

        Map<String, Object> jsonSchema = new LinkedHashMap<>();
        jsonSchema.put("name", "deploymind_incident_analysis");
        jsonSchema.put("strict", true);
        jsonSchema.put("schema", schema);

        Map<String, Object> responseFormat = new LinkedHashMap<>();
        responseFormat.put("type", "json_schema");
        responseFormat.put("json_schema", jsonSchema);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", model);
        request.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        request.put("temperature", 0.2);
        request.put("response_format", responseFormat);

        try {
            Map<String, Object> response = client.post()
                    .uri(apiUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            String content = extractMessageContent(response);
            if (content == null || content.isBlank()) return null;

            return objectMapper.readValue(
                    cleanJson(content),
                    new TypeReference<Map<String, Object>>() {}
            );
        } catch (Exception ex) {
            return null;
        }
    }

    private String extractMessageContent(Map<String, Object> response) {
        if (response == null) return null;
        Object choicesObject = response.get("choices");
        if (!(choicesObject instanceof List<?> choices) || choices.isEmpty()) return null;

        Object first = choices.get(0);
        if (!(first instanceof Map<?, ?> choice)) return null;

        Object messageObject = choice.get("message");
        if (!(messageObject instanceof Map<?, ?> message)) return null;

        Object content = message.get("content");
        return content == null ? null : String.valueOf(content);
    }

    private String cleanJson(String content) {
        String value = content.trim();
        if (value.startsWith("```") && value.endsWith("```")) {
            int firstNewline = value.indexOf('\n');
            if (firstNewline >= 0) {
                value = value.substring(firstNewline + 1, value.length() - 3).trim();
            }
        }
        return value;
    }
}
