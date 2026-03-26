package com.erise.ai.cloud.provider;

import com.erise.ai.cloud.config.EriseCloudProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class OpenAiCompatClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final EriseCloudProperties properties;

    public String chat(String systemPrompt, String userPrompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(properties.getProvider().getApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> payload = Map.of(
                "model", properties.getProvider().getChatModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "temperature", 0.2
        );
        JsonNode response = restTemplate.postForObject(
                properties.getProvider().getBaseUrl() + "/chat/completions",
                new HttpEntity<>(payload, headers),
                JsonNode.class
        );
        if (response == null) {
            return "";
        }
        return response.path("choices").path(0).path("message").path("content").asText("");
    }
}
