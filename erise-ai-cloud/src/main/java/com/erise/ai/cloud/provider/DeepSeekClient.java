package com.erise.ai.cloud.provider;

import com.erise.ai.cloud.config.EriseCloudProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@Component
@RequiredArgsConstructor
public class DeepSeekClient {

    private static final ParameterizedTypeReference<ServerSentEvent<String>> SSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final EriseCloudProperties properties;
    private final WebClient.Builder webClientBuilder = WebClient.builder();

    public boolean isConfigured() {
        return properties != null
                && properties.getDeepseek() != null
                && StringUtils.hasText(properties.getDeepseek().getApiKey())
                && StringUtils.hasText(properties.getDeepseek().getChatModel())
                && StringUtils.hasText(properties.getDeepseek().getBaseUrl());
    }

    public String chat(String systemPrompt, String userPrompt) {
        return chat(List.of(
                new ChatMessage("system", systemPrompt),
                new ChatMessage("user", userPrompt)
        ));
    }

    public String chat(List<ChatMessage> messages) {
        ensureConfigured();
        JsonNode response = restTemplate.postForObject(
                properties.getDeepseek().getBaseUrl() + "/chat/completions",
                new HttpEntity<>(buildPayload(messages, false), buildHeaders()),
                JsonNode.class
        );
        if (response == null) {
            return "";
        }
        JsonNode messageNode = response.path("choices").path(0).path("message");
        return extractTextContent(messageNode.path("content")).trim();
    }

    public Flux<String> stream(List<ChatMessage> messages) {
        ensureConfigured();
        return webClientBuilder
                .baseUrl(properties.getDeepseek().getBaseUrl())
                .defaultHeaders(headers -> {
                    headers.setBearerAuth(properties.getDeepseek().getApiKey());
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.setAccept(List.of(MediaType.TEXT_EVENT_STREAM));
                })
                .build()
                .post()
                .uri("/chat/completions")
                .bodyValue(buildPayload(messages, true))
                .retrieve()
                .bodyToFlux(SSE_TYPE)
                .map(ServerSentEvent::data)
                .filter(StringUtils::hasText)
                .flatMap(this::extractStreamChunks);
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(properties.getDeepseek().getApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private Map<String, Object> buildPayload(List<ChatMessage> messages, boolean stream) {
        return Map.of(
                "model", properties.getDeepseek().getChatModel(),
                "messages", messages.stream()
                        .map(message -> Map.of("role", message.role(), "content", message.content()))
                        .toList(),
                "temperature", 0.3,
                "stream", stream
        );
    }

    private void ensureConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException("DeepSeek provider is not configured");
        }
    }

    private Flux<String> extractStreamChunks(String data) {
        if (!StringUtils.hasText(data) || "[DONE]".equals(data.trim())) {
            return Flux.empty();
        }
        try {
            JsonNode response = objectMapper.readTree(data);
            List<String> pieces = new ArrayList<>();
            for (JsonNode choice : response.path("choices")) {
                String content = extractTextContent(choice.path("delta").path("content"));
                if (StringUtils.hasText(content)) {
                    pieces.add(content);
                }
            }
            return Flux.fromIterable(pieces);
        } catch (Exception exception) {
            return Flux.error(new IllegalStateException("Failed to parse DeepSeek stream response", exception));
        }
    }

    private String extractTextContent(JsonNode contentNode) {
        if (contentNode == null || contentNode.isMissingNode() || contentNode.isNull()) {
            return "";
        }
        if (contentNode.isTextual()) {
            return contentNode.asText("");
        }
        if (contentNode.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode item : contentNode) {
                if (item.isTextual()) {
                    builder.append(item.asText(""));
                    continue;
                }
                builder.append(item.path("text").asText(""));
            }
            return builder.toString();
        }
        return contentNode.path("text").asText("");
    }

    public record ChatMessage(String role, String content) {
    }
}