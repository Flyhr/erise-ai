package com.erise.ai.backend.modules;

import com.erise.ai.backend.common.api.ApiResponse;
import com.erise.ai.backend.common.exception.BizException;
import com.erise.ai.backend.common.exception.ErrorCodes;
import com.erise.ai.backend.common.security.CurrentUser;
import com.erise.ai.backend.common.util.SecurityUtils;
import com.erise.ai.backend.integration.ai.CloudAiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    @PostMapping("/chat")
    public ApiResponse<AiChatResponse> chat(@Valid @RequestBody AiChatRequest request) {
        return ApiResponse.success(aiService.chat(request));
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody AiChatRequest request) {
        return aiService.stream(request);
    }

    @PostMapping("/chat/{requestId}/cancel")
    public ApiResponse<Void> cancel(@PathVariable String requestId) {
        aiService.cancel(requestId);
        return ApiResponse.success("success", null);
    }

    @GetMapping("/sessions")
    public ApiResponse<List<AiSessionSummaryView>> sessions() {
        return ApiResponse.success(aiService.sessions());
    }

    @GetMapping("/sessions/{id}")
    public ApiResponse<AiSessionDetailView> session(@PathVariable Long id) {
        return ApiResponse.success(aiService.session(id));
    }

    @DeleteMapping("/sessions/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        aiService.deleteSession(id);
        return ApiResponse.success("success", null);
    }

    @GetMapping("/models")
    public ApiResponse<List<AiModelView>> models() {
        return ApiResponse.success(aiService.models());
    }
}

@Service
@RequiredArgsConstructor
class AiService {

    private final ProjectService projectService;
    private final DocumentService documentService;
    private final FileService fileService;
    private final AiTempFileService aiTempFileService;
    private final AiRetrievalSettingService aiRetrievalSettingService;
    private final CloudAiClient cloudAiClient;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    AiChatResponse chat(AiChatRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        ResolvedChatContext resolvedContext = resolveChatContext(request);
        validateProjectAccess(resolvedContext.projectId());
        String requestId = requestId();
        RetrievalSettings retrievalSettings = aiRetrievalSettingService.resolveEffectiveSettings(
                request.webSearchEnabled(),
                request.similarityThreshold(),
                request.topK()
        );
        CloudAiClient.ChatResponse response = cloudAiClient.chat(
                currentUser,
                toCompletionRequest(request, resolvedContext, retrievalSettings),
                requestId
        );
        auditLogService.log(currentUser, "AI_CHAT", "AI_SESSION", response.sessionId(), Map.of("requestId", response.requestId()));
        return new AiChatResponse(
                response.sessionId(),
                response.assistantMessageId(),
                response.answer(),
                response.citations() == null ? List.of() : response.citations().stream().map(this::toCitationView).toList(),
                response.usedTools() == null ? List.of() : response.usedTools(),
                response.confidence(),
                response.refusedReason(),
                response.requestId(),
                response.messageStatus(),
                response.modelCode(),
                response.providerCode()
        );
    }

    SseEmitter stream(AiChatRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        ResolvedChatContext resolvedContext = resolveChatContext(request);
        validateProjectAccess(resolvedContext.projectId());
        String requestId = requestId();
        RetrievalSettings retrievalSettings = aiRetrievalSettingService.resolveEffectiveSettings(
                request.webSearchEnabled(),
                request.similarityThreshold(),
                request.topK()
        );
        SseEmitter emitter = new SseEmitter(0L);
        Disposable subscription = cloudAiClient.stream(currentUser, toCompletionRequest(request, resolvedContext, retrievalSettings), requestId)
                .subscribe(
                        event -> sendEvent(emitter, event),
                        error -> {
                            sendError(emitter, error.getMessage());
                            emitter.completeWithError(error);
                        },
                        emitter::complete
                );
        emitter.onCompletion(subscription::dispose);
        emitter.onTimeout(() -> {
            subscription.dispose();
            emitter.complete();
        });
        return emitter;
    }

    void cancel(String requestId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        cloudAiClient.cancel(currentUser, requestId, requestId());
    }

    List<AiSessionSummaryView> sessions() {
        CurrentUser currentUser = SecurityUtils.currentUser();
        CloudAiClient.PageResult<CloudAiClient.SessionSummaryResponse> page = cloudAiClient.sessions(currentUser, 1, 30, requestId());
        return page.records().stream().map(this::toSummaryView).toList();
    }

    AiSessionDetailView session(Long id) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        CloudAiClient.SessionDetailResponse detail = cloudAiClient.session(currentUser, id, requestId());
        return new AiSessionDetailView(
                detail.id(),
                detail.projectId(),
                detail.title(),
                detail.messages().stream().map(this::toMessageView).toList()
        );
    }

    void deleteSession(Long id) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        cloudAiClient.deleteSession(currentUser, id, requestId());
        aiTempFileService.deleteSessionTempFiles(currentUser.userId(), id);
        auditLogService.log(currentUser, "AI_SESSION_DELETE", "AI_SESSION", id, null);
    }

    List<AiModelView> models() {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return cloudAiClient.models(currentUser, requestId()).stream()
                .map(item -> new AiModelView(item.providerCode(), item.modelCode(), item.modelName(), item.supportStream(), item.maxContextTokens()))
                .toList();
    }

    private void validateProjectAccess(Long projectId) {
        if (projectId != null) {
            projectService.requireAccessibleProject(projectId);
        }
    }

    private Long resolveAttachmentProjectId(AiAttachmentRequest attachment) {
        return switch (normalizeAttachmentType(attachment.attachmentType())) {
            case "DOCUMENT" -> documentService.detail(attachment.sourceId()).projectId();
            case "FILE" -> fileService.detail(attachment.sourceId()).projectId();
            default -> throw new BizException(ErrorCodes.BAD_REQUEST, "Unsupported attachment type: " + attachment.attachmentType());
        };
    }

    private ResolvedChatContext resolveChatContext(AiChatRequest request) {
        Long resolvedProjectId = request.projectId();
        List<CloudAiClient.AttachmentRef> resolvedAttachments = new ArrayList<>();

        for (AiAttachmentRequest attachment : normalizeAttachments(request.attachments())) {
            Long attachmentProjectId = resolveAttachmentProjectId(attachment);
            resolvedProjectId = mergeResolvedProjectId(resolvedProjectId, attachmentProjectId);
            resolvedAttachments.add(toAttachmentRef(attachment));
        }

        for (ResolvedTempFileAttachment tempFile : aiTempFileService.resolveUsableTempFiles(request.sessionId(), request.tempFileIds())) {
            resolvedProjectId = mergeResolvedProjectId(resolvedProjectId, tempFile.projectId());
            resolvedAttachments.add(new CloudAiClient.AttachmentRef("TEMP_FILE", tempFile.id(), tempFile.projectId(), request.sessionId(), tempFile.fileName()));
        }

        return new ResolvedChatContext(resolvedProjectId, resolvedAttachments);
    }

    private Long mergeResolvedProjectId(Long resolvedProjectId, Long attachmentProjectId) {
        if (attachmentProjectId == null) {
            return resolvedProjectId;
        }
        if (resolvedProjectId == null) {
            return attachmentProjectId;
        }
        if (!resolvedProjectId.equals(attachmentProjectId)) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "All attached files and documents must belong to the same project");
        }
        return resolvedProjectId;
    }

    private String resolveScene(ResolvedChatContext context) {
        return context.attachments().isEmpty()
                ? (context.projectId() == null ? "general_chat" : "project_chat")
                : "document_chat";
    }

    private CloudAiClient.ChatCompletionRequest toCompletionRequest(AiChatRequest request,
                                                                    ResolvedChatContext resolvedContext,
                                                                    RetrievalSettings retrievalSettings) {
        return new CloudAiClient.ChatCompletionRequest(
                request.sessionId(),
                resolveScene(resolvedContext),
                request.modelCode(),
                request.question(),
                new CloudAiClient.ChatContext(
                        resolvedContext.projectId(),
                        null,
                        resolvedContext.attachments()
                ),
                0.3,
                2048,
                request.mode(),
                retrievalSettings.webSearchEnabled(),
                retrievalSettings.similarityThreshold(),
                retrievalSettings.topK()
        );
    }

    private CloudAiClient.AttachmentRef toAttachmentRef(AiAttachmentRequest attachment) {
        return new CloudAiClient.AttachmentRef(
                normalizeAttachmentType(attachment.attachmentType()),
                attachment.sourceId(),
                attachment.projectId(),
                null,
                attachment.title()
        );
    }

    private List<AiAttachmentRequest> normalizeAttachments(List<AiAttachmentRequest> attachments) {
        return attachments == null ? List.of() : attachments.stream().filter(item -> item != null).toList();
    }

    private String normalizeAttachmentType(String attachmentType) {
        if (attachmentType == null || attachmentType.isBlank()) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "Attachment type is required");
        }
        return attachmentType.trim().toUpperCase();
    }

    private AiSessionSummaryView toSummaryView(CloudAiClient.SessionSummaryResponse item) {
        return new AiSessionSummaryView(item.id(), item.projectId(), item.title(), item.lastMessageAt(), item.createdAt());
    }

    private AiMessageView toMessageView(CloudAiClient.MessageResponse message) {
        return new AiMessageView(
                message.id(),
                toRoleCode(message.role()),
                message.content(),
                message.confidence(),
                message.refusedReason(),
                message.citations() == null ? List.of() : message.citations().stream().map(this::toCitationView).toList(),
                message.createdAt(),
                message.messageStatus(),
                message.errorMessage(),
                message.requestId()
        );
    }

    private AiCitationView toCitationView(CloudAiClient.CitationResponse citation) {
        return new AiCitationView(
                citation.sourceType(),
                citation.sourceId(),
                citation.sourceTitle(),
                citation.snippet(),
                citation.pageNo(),
                citation.score(),
                citation.url()
        );
    }

    private String toRoleCode(String role) {
        return "user".equalsIgnoreCase(role) ? "USER" : "ASSISTANT";
    }

    private String requestId() {
        String traceId = MDC.get("traceId");
        return traceId == null || traceId.isBlank() ? java.util.UUID.randomUUID().toString() : traceId;
    }

    private void sendEvent(SseEmitter emitter, ServerSentEvent<String> event) {
        try {
            String name = event.event() == null ? "message" : event.event();
            String data = event.data() == null ? "" : event.data();
            JsonNode payload = parsePayload(data);
            switch (name) {
                case "stream.start" -> emitter.send(SseEmitter.event().name("start").data(startPayload(payload)));
                case "stream.delta" -> emitter.send(SseEmitter.event().name("chunk").data(payload.path("delta").asText("")));
                case "stream.end" -> emitter.send(SseEmitter.event().name("done").data(donePayload(payload)));
                case "stream.error" -> emitter.send(SseEmitter.event().name("error").data(payload.path("message").asText(data)));
                default -> emitter.send(SseEmitter.event().name(name).data(data));
            }
        } catch (IOException exception) {
            throw new BizException(ErrorCodes.AI_ERROR, "Failed to forward AI stream: " + exception.getMessage());
        }
    }

    private JsonNode parsePayload(String data) {
        try {
            return objectMapper.readTree(data);
        } catch (Exception exception) {
            return objectMapper.createObjectNode();
        }
    }

    private Map<String, Object> startPayload(JsonNode payload) {
        Map<String, Object> result = new HashMap<>();
        result.put("requestId", payload.path("requestId").asText(""));
        if (payload.hasNonNull("sessionId")) {
            result.put("sessionId", payload.path("sessionId").asLong());
        }
        if (payload.hasNonNull("assistantMessageId")) {
            result.put("messageId", payload.path("assistantMessageId").asLong());
        }
        return result;
    }

    private Map<String, Object> donePayload(JsonNode payload) {
        Map<String, Object> result = new HashMap<>();
        result.put("requestId", payload.path("requestId").asText(""));
        if (payload.hasNonNull("sessionId")) {
            result.put("sessionId", payload.path("sessionId").asLong());
        }
        if (payload.hasNonNull("assistantMessageId")) {
            result.put("messageId", payload.path("assistantMessageId").asLong());
        }
        if (payload.hasNonNull("latencyMs")) {
            result.put("latencyMs", payload.path("latencyMs").asLong());
        }
        return result;
    }

    private void sendError(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(message == null ? "stream error" : message));
        } catch (IOException ignored) {
        }
    }
}

record AiChatRequest(Long projectId,
                     Long sessionId,
                     @NotBlank String question,
                     String modelCode,
                     String mode,
                     List<AiAttachmentRequest> attachments,
                     List<Long> tempFileIds,
                     Boolean webSearchEnabled,
                     Double similarityThreshold,
                     Integer topK) {
}

record AiAttachmentRequest(@NotBlank String attachmentType, @NotNull Long sourceId, Long projectId, String title) {
}

record AiCitationView(String sourceType, Long sourceId, String sourceTitle, String snippet, Integer pageNo, Double score, String url) {
}

record AiChatResponse(
        Long sessionId,
        Long messageId,
        String answer,
        List<AiCitationView> citations,
        List<String> usedTools,
        Double confidence,
        String refusedReason,
        String requestId,
        String messageStatus,
        String modelCode,
        String providerCode
) {
}

record AiSessionSummaryView(Long id, Long projectId, String title, LocalDateTime lastMessageAt, LocalDateTime createdAt) {
}

record AiMessageView(
        Long id,
        String roleCode,
        String content,
        Double confidence,
        String refusedReason,
        List<AiCitationView> citations,
        LocalDateTime createdAt,
        String status,
        String errorMessage,
        String requestId
) {
}

record AiSessionDetailView(Long id, Long projectId, String title, List<AiMessageView> messages) {
}

record AiModelView(String providerCode, String modelCode, String modelName, boolean supportStream, Integer maxContextTokens) {
}

record ResolvedChatContext(Long projectId, List<CloudAiClient.AttachmentRef> attachments) {
}
