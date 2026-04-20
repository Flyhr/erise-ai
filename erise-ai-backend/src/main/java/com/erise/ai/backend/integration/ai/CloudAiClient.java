package com.erise.ai.backend.integration.ai;

import com.erise.ai.backend.common.config.EriseProperties;
import com.erise.ai.backend.common.exception.BizException;
import com.erise.ai.backend.common.exception.ErrorCodes;
import com.erise.ai.backend.common.security.CurrentUser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;

@Component
public class CloudAiClient {

    private static final Logger log = LoggerFactory.getLogger(CloudAiClient.class);

    @Qualifier("aiRestTemplate")
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder = WebClient.builder();
    private final EriseProperties properties;

    public CloudAiClient(@Qualifier("aiRestTemplate") RestTemplate restTemplate,
                         ObjectMapper objectMapper,
                         EriseProperties properties) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

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

    public SessionSummaryResponse createSession(CurrentUser user, SessionCreateRequest request, String requestId) {
        return post(user, "/internal/ai/chat/sessions", request, requestId, SessionSummaryResponse.class);
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

    public AiServiceHealthResponse serviceHealth(CurrentUser user, String requestId) {
        return get(
                user,
                properties.getCloud().getBaseUrl() + "/internal/ai/chat/health?includeProviders=true",
                requestId,
                new TypeReference<AiServiceHealthResponse>() { }
        );
    }

    public AiProviderHealthInventoryResponse providerHealth(CurrentUser user, String requestId) {
        return get(
                user,
                properties.getCloud().getBaseUrl() + "/internal/ai/chat/providers/health",
                requestId,
                new TypeReference<AiProviderHealthInventoryResponse>() { }
        );
    }

    public FileCapabilityMatrixResponse fileCapabilities(CurrentUser user, String requestId) {
        return get(
                user,
                properties.getCloud().getBaseUrl() + "/internal/ai/chat/files/capabilities",
                requestId,
                new TypeReference<FileCapabilityMatrixResponse>() { }
        );
    }

    public N8nEventSummaryResponse n8nEventSummary(CurrentUser user, int hours, String requestId) {
        String url = UriComponentsBuilder.fromHttpUrl(properties.getCloud().getBaseUrl() + "/internal/ai/chat/n8n/events/summary")
                .queryParam("hours", hours)
                .toUriString();
        return get(user, url, requestId, new TypeReference<N8nEventSummaryResponse>() { });
    }

    public PageResult<N8nEventResponse> n8nEvents(CurrentUser user,
                                                  int pageNum,
                                                  int pageSize,
                                                  String q,
                                                  String deliveryStatus,
                                                  String workflowStatus,
                                                  String manualStatus,
                                                  String eventType,
                                                  LocalDate createdDate,
                                                  String requestId) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(properties.getCloud().getBaseUrl() + "/internal/ai/chat/n8n/events")
                .queryParam("pageNum", pageNum)
                .queryParam("pageSize", pageSize);
        if (q != null && !q.isBlank()) {
            builder.queryParam("q", q);
        }
        if (deliveryStatus != null && !deliveryStatus.isBlank()) {
            builder.queryParam("deliveryStatus", deliveryStatus);
        }
        if (workflowStatus != null && !workflowStatus.isBlank()) {
            builder.queryParam("workflowStatus", workflowStatus);
        }
        if (manualStatus != null && !manualStatus.isBlank()) {
            builder.queryParam("manualStatus", manualStatus);
        }
        if (eventType != null && !eventType.isBlank()) {
            builder.queryParam("eventType", eventType);
        }
        if (createdDate != null) {
            builder.queryParam("createdDate", createdDate);
        }
        return get(user, builder.toUriString(), requestId, new TypeReference<PageResult<N8nEventResponse>>() { });
    }

    public N8nEventDetailResponse n8nEventDetail(CurrentUser user, Long eventId, String requestId) {
        return get(
                user,
                properties.getCloud().getBaseUrl() + "/internal/ai/chat/n8n/events/" + eventId,
                requestId,
                new TypeReference<N8nEventDetailResponse>() { }
        );
    }

    public N8nRetryResponse retryN8nEvent(CurrentUser user, Long eventId, String requestId) {
        return post(user, "/internal/ai/chat/n8n/events/" + eventId + "/retry", null, requestId, N8nRetryResponse.class);
    }

    public N8nEventResponse manualHandoffN8nEvent(CurrentUser user, Long eventId, String reason, String requestId) {
        return post(
                user,
                "/internal/ai/chat/n8n/events/" + eventId + "/manual-handoff",
                new N8nManualHandoffRequest(reason),
                requestId,
                N8nEventResponse.class
        );
    }

    public ResponseEntity<String> mcpProxy(String authorization, String requestId, String body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", authorization);
            headers.set("X-Request-Id", requestId);
            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            return restTemplate.exchange(
                    properties.getCloud().getBaseUrl() + "/mcp",
                    HttpMethod.POST,
                    entity,
                    String.class
            );
        } catch (RestClientResponseException exception) {
            HttpHeaders headers = new HttpHeaders();
            if (exception.getResponseHeaders() != null) {
                headers.putAll(exception.getResponseHeaders());
            }
            return ResponseEntity.status(exception.getStatusCode())
                    .headers(headers)
                    .body(exception.getResponseBodyAsString());
        } catch (RestClientException exception) {
            log.warn("AI MCP proxy request failed", exception);
            throw new BizException(ErrorCodes.AI_ERROR, extractRestClientMessage(exception, "AI MCP proxy unavailable"));
        }
    }

    public RagIndexUpsertResponse upsertRagIndex(Long userId, RagIndexUpsertRequest request, String requestId) {
        return post(userId, "/internal/ai/chat/rag/index/upsert", request, requestId, RagIndexUpsertResponse.class);
    }

    public RagIndexDeleteResponse deleteRagIndex(Long userId, RagIndexDeleteRequest request, String requestId) {
        return post(userId, "/internal/ai/chat/rag/index/delete", request, requestId, RagIndexDeleteResponse.class);
    }

    public PdfOcrResponse extractPdfText(Long userId, String fileName, byte[] bytes, String requestId) {
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return fileName;
                }
            });
            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, buildHeaders(userId, requestId, MediaType.MULTIPART_FORM_DATA));
            String payload = restTemplate.exchange(
                    properties.getCloud().getBaseUrl() + "/internal/ai/chat/ocr/pdf-text",
                    HttpMethod.POST,
                    entity,
                    String.class
            ).getBody();
            return readData(payload, PdfOcrResponse.class);
        } catch (RestClientException exception) {
            log.warn("AI OCR request failed for file {}", fileName, exception);
            throw new BizException(ErrorCodes.AI_ERROR, extractRestClientMessage(exception, "AI OCR service unavailable"));
        }
    }

    public FileExtractResponse extractFileText(Long userId, String fileName, String fileExt, byte[] bytes, String requestId) {
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return fileName;
                }
            });
            body.add("fileName", fileName);
            body.add("fileExt", fileExt);
            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(
                    body,
                    buildHeaders(resolveUserId(userId), requestId, MediaType.MULTIPART_FORM_DATA)
            );
            String payload = restTemplate.exchange(
                    properties.getCloud().getBaseUrl() + "/internal/ai/chat/files/extract",
                    HttpMethod.POST,
                    entity,
                    String.class
            ).getBody();
            return readData(payload, FileExtractResponse.class);
        } catch (RestClientException exception) {
            log.warn("AI file extraction request failed for file {}", fileName, exception);
            throw new BizException(ErrorCodes.AI_ERROR, extractRestClientMessage(exception, "AI file extraction service unavailable"));
        }
    }

    public TextChunkResponse chunkText(Long userId, TextChunkRequest request, String requestId) {
        return post(resolveUserId(userId), "/internal/ai/chat/files/chunk-text", request, requestId, TextChunkResponse.class);
    }

    private <T> T post(CurrentUser user, String path, Object body, String requestId, Class<T> type) {
        return post(user.userId(), path, body, requestId, type);
    }

    private <T> T post(Long userId, String path, Object body, String requestId, Class<T> type) {
        try {
            HttpEntity<Object> entity = new HttpEntity<>(body, buildHeaders(userId, requestId));
            String payload = restTemplate.exchange(
                    properties.getCloud().getBaseUrl() + path,
                    HttpMethod.POST,
                    entity,
                    String.class
            ).getBody();
            return readData(payload, type);
        } catch (RestClientException exception) {
            log.warn("AI POST request failed: path={}", path, exception);
            throw new BizException(ErrorCodes.AI_ERROR, extractRestClientMessage(exception, "AI service unavailable"));
        }
    }

    private <T> T delete(CurrentUser user, String url, String requestId, Class<T> type) {
        try {
            HttpEntity<Void> entity = new HttpEntity<>(buildHeaders(user.userId(), requestId));
            String payload = restTemplate.exchange(url, HttpMethod.DELETE, entity, String.class).getBody();
            return readData(payload, type);
        } catch (RestClientException exception) {
            log.warn("AI DELETE request failed: url={}", url, exception);
            throw new BizException(ErrorCodes.AI_ERROR, extractRestClientMessage(exception, "AI service unavailable"));
        }
    }

    private <T> T get(CurrentUser user, String url, String requestId, TypeReference<T> type) {
        try {
            HttpEntity<Void> entity = new HttpEntity<>(buildHeaders(user.userId(), requestId));
            String payload = restTemplate.exchange(url, HttpMethod.GET, entity, String.class).getBody();
            return readData(payload, type);
        } catch (RestClientException exception) {
            log.warn("AI GET request failed: url={}", url, exception);
            throw new BizException(ErrorCodes.AI_ERROR, extractRestClientMessage(exception, "AI service unavailable"));
        }
    }

    private HttpHeaders buildHeaders(CurrentUser user, String requestId) {
        return buildHeaders(user.userId(), requestId);
    }

    private HttpHeaders buildHeaders(Long userId, String requestId) {
        return buildHeaders(userId, requestId, MediaType.APPLICATION_JSON);
    }

    private HttpHeaders buildHeaders(Long userId, String requestId, MediaType contentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(contentType);
        headers.set("X-Internal-Service-Token", properties.getInternal().getApiKey());
        headers.set("X-Internal-Key", properties.getInternal().getApiKey());
        headers.set("X-User-Id", String.valueOf(userId));
        headers.set("X-Org-Id", "0");
        headers.set("X-Request-Id", requestId);
        return headers;
    }

    private Long resolveUserId(Long userId) {
        return userId == null || userId <= 0 ? 0L : userId;
    }

    private <T> T readData(String body, Class<T> type) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.path("code").asInt(-1) != 0) {
                throw new BizException(ErrorCodes.AI_ERROR, normalizeAiMessage(root.path("msg").asText("AI request failed")));
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
                throw new BizException(ErrorCodes.AI_ERROR, normalizeAiMessage(root.path("msg").asText("AI request failed")));
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
            return normalizeAiMessage(root.path("msg").asText("AI stream unavailable"));
        } catch (Exception exception) {
            return normalizeAiMessage(body == null || body.isBlank() ? "AI stream unavailable" : body);
        }
    }

    private String extractRestClientMessage(RestClientException exception, String fallbackMessage) {
        if (exception instanceof RestClientResponseException responseException) {
            String body = responseException.getResponseBodyAsString();
            if (body != null && !body.isBlank()) {
                String message = extractMessage(body);
                if (message != null && !message.isBlank()) {
                    return message;
                }
            }
        }
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return fallbackMessage;
        }
        return normalizeAiMessage(fallbackMessage + ": " + message);
    }

    private String normalizeAiMessage(String message) {
        if (message == null || message.isBlank()) {
            return "AI service unavailable";
        }
        String normalized = message.trim();
        String lower = normalized.toLowerCase();
        if (lower.contains("insufficient_quota") || (lower.contains("quota") && lower.contains("exceeded"))) {
            return "AI provider quota exceeded. Please verify the configured provider quota or switch to DeepSeek.";
        }
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return "AI service timeout. Please retry in a moment.";
        }
        if (lower.contains("connection refused")
                || lower.contains("connection reset")
                || lower.contains("connection aborted")
                || lower.contains("i/o error")
                || lower.contains("network is unreachable")) {
            return "AI service is temporarily unreachable. Please retry in a moment.";
        }
        if (lower.contains("duplicate entry") && lower.contains("uk_ea_rag_chunk_source_chunk")) {
            return "RAG chunk metadata was written twice. Please retry the indexing task.";
        }
        return normalized;
    }

    public record SessionCreateRequest(
            Long projectId,
            String scene,
            String title
    ) {
    }

    public record ChatCompletionRequest(
            Long sessionId,
            String scene,
            String modelCode,
            String message,
            ChatContext context,
            Double temperature,
            Integer maxTokens,
            String mode,
            Boolean webSearchEnabled,
            Double similarityThreshold,
            Integer topK
    ) {
    }

    public record ChatContext(Long projectId, Long documentId, List<AttachmentRef> attachments) {
    }

    public record AttachmentRef(String attachmentType, Long sourceId, Long projectId, Long sessionId, String title) {
    }

    public record RagChunkRequest(
            Integer chunkNum,
            String chunkText,
            Integer pageNo,
            String sectionPath
    ) {
    }

    public record RagIndexUpsertRequest(
            Long userId,
            String scopeType,
            Long projectId,
            Long sessionId,
            String sourceType,
            Long sourceId,
            String sourceName,
            List<RagChunkRequest> chunks,
            Integer previousChunkCount,
            LocalDateTime updatedAt
    ) {
    }

    public record RagIndexDeleteRequest(
            Long userId,
            String scopeType,
            Long projectId,
            Long sessionId,
            String sourceType,
            Long sourceId
    ) {
    }

    public record RagIndexUpsertResponse(
            Integer upserted,
            String collectionName,
            String embeddingModelCode,
            String embeddingVersion,
            Integer embeddingDimension
    ) {
    }

    public record RagIndexDeleteResponse(
            Boolean deleted,
            String collectionName
    ) {
    }

    public record PdfOcrResponse(
            String text,
            List<String> pageTexts,
            Integer pageCount,
            Boolean usedOcr,
            String engine
    ) {
    }

    public record FileExtractChunkResponse(
            Integer chunkNum,
            String chunkText,
            Integer pageNo,
            String sectionPath
    ) {
    }

    public record FileExtractResponse(
            String plainText,
            List<FileExtractChunkResponse> chunks,
            String parser,
            Boolean usedOcr,
            Integer pageCount
    ) {
    }

    public record TextChunkRequest(
            String plainText,
            Integer pageNo
    ) {
    }

    public record TextChunkResponse(
            List<FileExtractChunkResponse> chunks
    ) {
    }

    public record ChatResponse(
            String requestId,
            Long sessionId,
            Long userMessageId,
            Long assistantMessageId,
            String answer,
            List<CitationResponse> citations,
            List<String> usedTools,
            Double confidence,
            String refusedReason,
            String scene,
            String modelCode,
            String providerCode,
            String messageStatus,
            Usage usage,
            Integer latencyMs
    ) {
    }

    public record CitationResponse(
            String sourceType,
            Long sourceId,
            String sourceTitle,
            String snippet,
            Integer pageNo,
            String sectionPath,
            Double score,
            String url
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
            Double confidence,
            String refusedReason,
            List<CitationResponse> citations,
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

    public record ModelResponse(String providerCode, String modelCode, String modelName, boolean isDefault, boolean supportStream, Integer maxContextTokens) {
    }

    public record AiServiceHealthResponse(
            String service,
            String status,
            String database,
            String redis,
            ModelHealthResponse providers
    ) {
    }

    public record ModelHealthResponse(
            String status,
            List<ProviderHealthResponse> routes
    ) {
    }

    public record ProviderHealthResponse(
            String role,
            String providerCode,
            String modelCode,
            String baseUrl,
            Boolean configured,
            String status,
            Integer latencyMs,
            String errorCode,
            String message
    ) {
    }

    public record AiProviderHealthInventoryResponse(
            String status,
            LocalDateTime generatedAt,
            String defaultProviderCode,
            String defaultModelCode,
            List<ProviderRouteHealthResponse> effectiveRoutes,
            List<ProviderRouteHealthResponse> enabledRoutes
    ) {
    }

    public record ProviderRouteHealthResponse(
            String role,
            String providerCode,
            String modelCode,
            String modelName,
            String baseUrl,
            String endpointUrl,
            String probeUrl,
            Boolean configured,
            String status,
            Integer timeoutSeconds,
            String source,
            Integer latencyMs,
            String errorCode,
            String message,
            Boolean isDefault,
            Boolean isEffective,
            Integer recentRequestCount24h,
            Integer recentErrorCount24h,
            List<ProviderRecentErrorResponse> recentErrorCodes
    ) {
    }

    public record ProviderRecentErrorResponse(
            String errorCode,
            Integer count,
            LocalDateTime lastSeenAt
    ) {
    }

    public record FileCapabilityMatrixResponse(
            List<String> parserOrder,
            List<String> parseStatuses,
            List<ParserRuntimeResponse> parserRuntimes,
            List<FileTypeCapabilityResponse> fileTypes
    ) {
    }

    public record ParserRuntimeResponse(
            String parserCode,
            String label,
            String status,
            String errorCode,
            String message,
            List<String> supportedExtensions
    ) {
    }

    public record FileTypeCapabilityResponse(
            String extension,
            String label,
            String primaryParser,
            String fallbackParser,
            Boolean supportsOcr,
            Boolean supportsPages,
            List<String> parseStatuses,
            String retryPolicy,
            String notes
    ) {
    }

    public record N8nEventResponse(
            Long id,
            String requestId,
            String eventType,
            String workflowHint,
            Long approvalId,
            Long sessionId,
            Long userId,
            Long projectId,
            String targetUrl,
            String deliveryStatus,
            String workflowStatus,
            String workflowName,
            String workflowVersion,
            String workflowDomain,
            String workflowOwner,
            String externalExecutionId,
            String workflowErrorSummary,
            Integer workflowDurationMs,
            Boolean deliveryRetryable,
            String manualStatus,
            String manualReason,
            Integer manualReplayCount,
            Long replayedFromEventId,
            LocalDateTime lastCallbackAt,
            Integer statusCode,
            Boolean successFlag,
            String errorCode,
            String errorMessage,
            Integer attemptCount,
            Integer maxAttempts,
            String idempotencyKey,
            String callbackPayloadJson,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record N8nErrorMetricResponse(
            String errorCode,
            Integer count
    ) {
    }

    public record N8nEventSummaryResponse(
            Integer windowHours,
            Integer totalEvents,
            Integer deliveredCount,
            Integer failedCount,
            Integer pendingCount,
            Integer skippedCount,
            Integer workflowFailedCount,
            Integer workflowRunningCount,
            Integer manualPendingCount,
            Integer retryableFailedCount,
            Double successRate,
            N8nEventResponse latestFailure,
            List<N8nErrorMetricResponse> topErrorCodes
    ) {
    }

    public record N8nEventDetailResponse(
            N8nEventResponse event,
            N8nEventResponse sourceEvent,
            List<N8nEventResponse> replayEvents
    ) {
    }

    public record N8nRetryResponse(
            Boolean retried,
            Long sourceEventId,
            N8nEventResponse event
    ) {
    }

    public record N8nManualHandoffRequest(String reason) {
    }
}
