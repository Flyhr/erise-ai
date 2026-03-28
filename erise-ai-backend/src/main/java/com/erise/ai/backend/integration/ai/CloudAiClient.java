package com.erise.ai.backend.integration.ai;

import com.erise.ai.backend.common.config.EriseProperties;
import com.erise.ai.backend.common.exception.BizException;
import com.erise.ai.backend.common.exception.ErrorCodes;
import com.erise.ai.backend.common.security.CurrentUser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;

@Component
@RequiredArgsConstructor
public class CloudAiClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder = WebClient.builder();
    private final EriseProperties properties;

    public ChatResponse chat(CurrentUser user, ChatCompletionRequest request, String requestId) {
        return post(user, "/internal/ai/chat/completions", request, requestId, ChatResponse.class);
    }

    public Flux<ServerSentEvent<String>> stream(CurrentUser user, ChatCompletionRequest request, String requestId) {
        WebClient client = webClientBuilder
                .baseUrl(properties.getCloud().getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create().responseTimeout(Duration.ofMinutes(5))))
                .build();
        return client.post()
                .uri("/internal/ai/chat/completions/stream")
                .headers(headers -> headers.addAll(buildHeaders(user, requestId)))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(request)
                .exchangeToFlux(response -> {
                    if (response.statusCode().isError()) {
                        return response.bodyToMono(String.class)
                                .flatMapMany(body -> Flux.error(new BizException(ErrorCodes.AI_ERROR, extractMessage(body))));
                    }
                    return response.bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() { });
                });
    }

    public CancelResponse cancel(CurrentUser user, String requestId, String traceId) {
        return post(user, "/internal/ai/chat/completions/" + requestId + "/cancel", null, traceId, CancelResponse.class);
    }

    public PageResult<SessionSummaryResponse> sessions(CurrentUser user, int pageNum, int pageSize, String requestId) {
        String url = UriComponentsBuilder.fromHttpUrl(properties.getCloud().getBaseUrl() + "/internal/ai/chat/sessions")
                .queryParam("pageNum", pageNum)
                .queryParam("pageSize", pageSize)
                .toUriString();
        return get(user, url, requestId, new TypeReference<PageResult<SessionSummaryResponse>>() { });
    }

    public SessionDetailResponse session(CurrentUser user, Long sessionId, String requestId) {
        return get(user, properties.getCloud().getBaseUrl() + "/internal/ai/chat/sessions/" + sessionId, requestId, new TypeReference<SessionDetailResponse>() { });
    }

    public DeleteResponse deleteSession(CurrentUser user, Long sessionId, String requestId) {
        return delete(user, properties.getCloud().getBaseUrl() + "/internal/ai/chat/sessions/" + sessionId, requestId, DeleteResponse.class);
    }

    public List<ModelResponse> models(CurrentUser user, String requestId) {
        return get(user, properties.getCloud().getBaseUrl() + "/internal/ai/chat/models", requestId, new TypeReference<List<ModelResponse>>() { });
    }

    private <T> T post(CurrentUser user, String path, Object body, String requestId, Class<T> type) {
        try {
            HttpEntity<Object> entity = new HttpEntity<>(body, buildHeaders(user, requestId));
            String payload = restTemplate.exchange(
                    properties.getCloud().getBaseUrl() + path,
                    HttpMethod.POST,
                    entity,
                    String.class
            ).getBody();
            return readData(payload, type);
        } catch (RestClientException exception) {
            throw new BizException(ErrorCodes.AI_ERROR, "AI service unavailable: " + exception.getMessage());
        }
    }

    private <T> T delete(CurrentUser user, String url, String requestId, Class<T> type) {
        try {
            HttpEntity<Void> entity = new HttpEntity<>(buildHeaders(user, requestId));
            String payload = restTemplate.exchange(url, HttpMethod.DELETE, entity, String.class).getBody();
            return readData(payload, type);
        } catch (RestClientException exception) {
            throw new BizException(ErrorCodes.AI_ERROR, "AI service unavailable: " + exception.getMessage());
        }
    }

    private <T> T get(CurrentUser user, String url, String requestId, TypeReference<T> type) {
        try {
            HttpEntity<Void> entity = new HttpEntity<>(buildHeaders(user, requestId));
            String payload = restTemplate.exchange(url, HttpMethod.GET, entity, String.class).getBody();
            return readData(payload, type);
        } catch (RestClientException exception) {
            throw new BizException(ErrorCodes.AI_ERROR, "AI service unavailable: " + exception.getMessage());
        }
    }

    private HttpHeaders buildHeaders(CurrentUser user, String requestId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Service-Token", properties.getInternal().getApiKey());
        headers.set("X-Internal-Key", properties.getInternal().getApiKey());
        headers.set("X-User-Id", String.valueOf(user.userId()));
        headers.set("X-Org-Id", "0");
        headers.set("X-Request-Id", requestId);
        return headers;
    }

    private <T> T readData(String body, Class<T> type) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.path("code").asInt(-1) != 0) {
                throw new BizException(ErrorCodes.AI_ERROR, root.path("msg").asText("AI request failed"));
            }
            return objectMapper.treeToValue(root.path("data"), type);
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException(ErrorCodes.AI_ERROR, "Failed to parse AI response: " + exception.getMessage());
        }
    }

    private <T> T readData(String body, TypeReference<T> type) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.path("code").asInt(-1) != 0) {
                throw new BizException(ErrorCodes.AI_ERROR, root.path("msg").asText("AI request failed"));
            }
            return objectMapper.convertValue(root.path("data"), type);
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException(ErrorCodes.AI_ERROR, "Failed to parse AI response: " + exception.getMessage());
        }
    }

    private String extractMessage(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            return root.path("msg").asText("AI stream unavailable");
        } catch (Exception exception) {
            return body == null || body.isBlank() ? "AI stream unavailable" : body;
        }
    }

    public record ChatCompletionRequest(
            Long sessionId,
            String scene,
            String modelCode,
            String message,
            ChatContext context,
            Double temperature,
            Integer maxTokens
    ) {
    }

    public record ChatContext(Long projectId, Long documentId, List<AttachmentRef> attachments) {
    }

    public record AttachmentRef(String attachmentType, Long sourceId, Long projectId, String title) {
    }

    public record ChatResponse(
            String requestId,
            Long sessionId,
            Long userMessageId,
            Long assistantMessageId,
            String answer,
            String scene,
            String modelCode,
            String providerCode,
            String messageStatus,
            Usage usage,
            Integer latencyMs
    ) {
    }

    public record Usage(Integer promptTokens, Integer completionTokens, Integer totalTokens) {
    }

    public record PageResult<T>(List<T> records, long pageNum, long pageSize, long total, long totalPages) {
    }

    public record SessionSummaryResponse(
            Long id,
            Long userId,
            Long orgId,
            Long projectId,
            String scene,
            String title,
            String summaryText,
            LocalDateTime lastMessageAt,
            Integer messageCount,
            String status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record MessageResponse(
            Long id,
            String role,
            String content,
            String messageStatus,
            Integer sequenceNo,
            String modelCode,
            String providerCode,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens,
            Integer latencyMs,
            String errorCode,
            String errorMessage,
            String requestId,
            LocalDateTime createdAt
    ) {
    }

    public record SessionDetailResponse(
            Long id,
            Long userId,
            Long orgId,
            Long projectId,
            String scene,
            String title,
            String summaryText,
            LocalDateTime lastMessageAt,
            Integer messageCount,
            String status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            List<MessageResponse> messages
    ) {
    }

    public record DeleteResponse(Boolean deleted, Long sessionId) {
    }

    public record CancelResponse(String requestId, Boolean cancelled) {
    }

    public record ModelResponse(String providerCode, String modelCode, String modelName, boolean supportStream, Integer maxContextTokens) {
    }
}
