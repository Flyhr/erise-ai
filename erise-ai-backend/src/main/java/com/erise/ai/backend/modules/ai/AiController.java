package com.erise.ai.backend.modules;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erise.ai.backend.common.api.ApiResponse;
import com.erise.ai.backend.common.entity.AuditableEntity;
import com.erise.ai.backend.common.exception.BizException;
import com.erise.ai.backend.common.exception.ErrorCodes;
import com.erise.ai.backend.common.util.SecurityUtils;
import com.erise.ai.backend.integration.ai.CloudAiClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
}

@Service
@RequiredArgsConstructor
class AiService {

    private final AiSessionMapper aiSessionMapper;
    private final AiMessageMapper aiMessageMapper;
    private final AiCitationMapper aiCitationMapper;
    private final ProjectService projectService;
    private final CloudAiClient cloudAiClient;
    private final AuditLogService auditLogService;

    AiChatResponse chat(AiChatRequest request) {
        var currentUser = SecurityUtils.currentUser();
        AiSessionEntity session = ensureSession(currentUser.userId(), request.sessionId(), request.projectId(), request.question());
        Long effectiveProjectId = request.sessionId() != null ? session.getProjectId() : request.projectId();
        List<CloudAiClient.PromptMessage> promptMessages = buildPromptMessages(session.getId(), request.question());
        Long userMessageId = saveMessage(session.getId(), currentUser.userId(), "USER", request.question(), null, null);
        CloudAiClient.ChatResponse response = cloudAiClient.chat(new CloudAiClient.ChatRequest(
                currentUser.userId(),
                currentUser.username(),
                currentUser.roleCode(),
                session.getId(),
                effectiveProjectId,
                request.question(),
                promptMessages
        ));
        Long assistantMessageId = saveMessage(session.getId(), currentUser.userId(), "ASSISTANT",
                response.answer(), response.confidence(), response.refusedReason());
        saveCitations(assistantMessageId, response.citations());
        session.setLastMessageAt(LocalDateTime.now());
        session.setUpdatedBy(currentUser.userId());
        aiSessionMapper.updateById(session);
        auditLogService.log(currentUser, "AI_CHAT", "AI_SESSION", session.getId(), Map.of("userMessageId", userMessageId));
        return toChatResponse(session.getId(), assistantMessageId, response);
    }

    SseEmitter stream(AiChatRequest request) {
        var currentUser = SecurityUtils.currentUser();
        AiSessionEntity session = ensureSession(currentUser.userId(), request.sessionId(), request.projectId(), request.question());
        Long effectiveProjectId = request.sessionId() != null ? session.getProjectId() : request.projectId();
        List<CloudAiClient.PromptMessage> promptMessages = buildPromptMessages(session.getId(), request.question());
        saveMessage(session.getId(), currentUser.userId(), "USER", request.question(), null, null);
        SseEmitter emitter = new SseEmitter(0L);
        AtomicReference<StringBuilder> answerBuilder = new AtomicReference<>(new StringBuilder());
        cloudAiClient.stream(new CloudAiClient.ChatRequest(
                        currentUser.userId(),
                        currentUser.username(),
                        currentUser.roleCode(),
                        session.getId(),
                        effectiveProjectId,
                        request.question(),
                        promptMessages
                ))
                .doOnNext(chunk -> {
                    answerBuilder.get().append(chunk);
                    try {
                        emitter.send(SseEmitter.event().name("chunk").data(chunk));
                    } catch (IOException exception) {
                        throw new RuntimeException(exception);
                    }
                })
                .doOnError(error -> {
                    try {
                        emitter.send(SseEmitter.event().name("error").data(error.getMessage()));
                    } catch (IOException ignored) {
                    }
                    emitter.completeWithError(error);
                })
                .doOnComplete(() -> {
                    Long messageId = saveMessage(session.getId(), currentUser.userId(), "ASSISTANT",
                            answerBuilder.get().toString(), 0.7, null);
                    session.setLastMessageAt(LocalDateTime.now());
                    session.setUpdatedBy(currentUser.userId());
                    aiSessionMapper.updateById(session);
                    try {
                        emitter.send(SseEmitter.event().name("done").data(Map.of("sessionId", session.getId(), "messageId", messageId)));
                    } catch (IOException ignored) {
                    }
                    emitter.complete();
                })
                .subscribe();
        return emitter;
    }

    List<AiSessionSummaryView> sessions() {
        var currentUser = SecurityUtils.currentUser();
        return aiSessionMapper.selectList(new LambdaQueryWrapper<AiSessionEntity>()
                        .eq(AiSessionEntity::getOwnerUserId, currentUser.userId())
                        .orderByDesc(AiSessionEntity::getLastMessageAt)
                        .last("limit 30"))
                .stream()
                .map(session -> new AiSessionSummaryView(session.getId(), session.getProjectId(), session.getTitle(),
                        session.getLastMessageAt(), session.getCreatedAt()))
                .toList();
    }

    AiSessionDetailView session(Long id) {
        var currentUser = SecurityUtils.currentUser();
        AiSessionEntity session = requireSession(id, currentUser.userId());
        List<AiMessageEntity> messages = aiMessageMapper.selectList(new LambdaQueryWrapper<AiMessageEntity>()
                .eq(AiMessageEntity::getSessionId, id)
                .orderByAsc(AiMessageEntity::getCreatedAt));
        List<AiMessageView> items = messages.stream().map(message -> {
            List<AiCitationView> citations = aiCitationMapper.selectList(new LambdaQueryWrapper<AiCitationEntity>()
                            .eq(AiCitationEntity::getMessageId, message.getId()))
                    .stream()
                    .map(citation -> new AiCitationView(citation.getSourceType(), citation.getSourceId(), citation.getSourceTitle(),
                            citation.getSnippet(), citation.getPageNo()))
                    .toList();
            return new AiMessageView(message.getId(), message.getRoleCode(), message.getContent(), message.getConfidence(),
                    message.getRefusedReason(), citations, message.getCreatedAt());
        }).toList();
        return new AiSessionDetailView(session.getId(), session.getProjectId(), session.getTitle(), items);
    }

    void deleteSession(Long id) {
        var currentUser = SecurityUtils.currentUser();
        requireSession(id, currentUser.userId());
        aiSessionMapper.deleteById(id);
        aiMessageMapper.delete(new LambdaQueryWrapper<AiMessageEntity>().eq(AiMessageEntity::getSessionId, id));
        auditLogService.log(currentUser, "AI_SESSION_DELETE", "AI_SESSION", id, null);
    }

    void persistAssistantMessage(InternalPersistMessageRequest request) {
        saveMessage(request.sessionId(), request.userId(), "ASSISTANT", request.answer(), request.confidence(), request.refusedReason());
    }

    private AiSessionEntity ensureSession(Long userId, Long sessionId, Long projectId, String question) {
        if (sessionId != null) {
            return requireSession(sessionId, userId);
        }
        if (projectId != null) {
            projectService.requireAccessibleProject(projectId);
        }
        AiSessionEntity entity = new AiSessionEntity();
        entity.setOwnerUserId(userId);
        entity.setProjectId(projectId);
        entity.setTitle(question.length() > 30 ? question.substring(0, 30) : question);
        entity.setLastMessageAt(LocalDateTime.now());
        entity.setCreatedBy(userId);
        entity.setUpdatedBy(userId);
        aiSessionMapper.insert(entity);
        return entity;
    }

    private AiSessionEntity requireSession(Long sessionId, Long userId) {
        AiSessionEntity session = aiSessionMapper.selectById(sessionId);
        if (session == null || !session.getOwnerUserId().equals(userId)) {
            throw new BizException(ErrorCodes.NOT_FOUND, "Session not found", HttpStatus.NOT_FOUND);
        }
        return session;
    }

    private List<CloudAiClient.PromptMessage> buildPromptMessages(Long sessionId, String question) {
        List<AiMessageEntity> history = aiMessageMapper.selectList(new LambdaQueryWrapper<AiMessageEntity>()
                .eq(AiMessageEntity::getSessionId, sessionId)
                .orderByAsc(AiMessageEntity::getCreatedAt));
        List<CloudAiClient.PromptMessage> promptMessages = new ArrayList<>();
        for (AiMessageEntity item : history) {
            if (item.getContent() == null || item.getContent().isBlank()) {
                continue;
            }
            String role = "USER".equals(item.getRoleCode()) ? "user" : "assistant";
            promptMessages.add(new CloudAiClient.PromptMessage(role, item.getContent()));
        }
        promptMessages.add(new CloudAiClient.PromptMessage("user", question));
        return promptMessages;
    }
    private Long saveMessage(Long sessionId, Long userId, String role, String content, Double confidence, String refusedReason) {

        AiMessageEntity entity = new AiMessageEntity();
        entity.setSessionId(sessionId);
        entity.setOwnerUserId(userId);
        entity.setRoleCode(role);
        entity.setContent(content);
        entity.setConfidence(confidence);
        entity.setRefusedReason(refusedReason);
        entity.setCreatedBy(userId);
        entity.setUpdatedBy(userId);
        aiMessageMapper.insert(entity);
        return entity.getId();
    }

    private void saveCitations(Long messageId, List<CloudAiClient.Citation> citations) {
        if (citations == null) {
            return;
        }
        for (CloudAiClient.Citation citation : citations) {
            AiCitationEntity entity = new AiCitationEntity();
            entity.setMessageId(messageId);
            entity.setSourceType(citation.sourceType());
            entity.setSourceId(citation.sourceId());
            entity.setSourceTitle(citation.sourceTitle());
            entity.setSnippet(citation.snippet());
            entity.setPageNo(citation.pageNo());
            entity.setCreatedBy(0L);
            entity.setUpdatedBy(0L);
            aiCitationMapper.insert(entity);
        }
    }

    private AiChatResponse toChatResponse(Long sessionId, Long messageId, CloudAiClient.ChatResponse response) {
        List<AiCitationView> citations = response.citations() == null ? List.of() : response.citations().stream()
                .map(item -> new AiCitationView(item.sourceType(), item.sourceId(), item.sourceTitle(), item.snippet(), item.pageNo()))
                .toList();
        return new AiChatResponse(sessionId, messageId, response.answer(), citations, response.usedTools(),
                response.confidence(), response.refusedReason());
    }
}

interface AiSessionMapper extends BaseMapper<AiSessionEntity> {
}

interface AiMessageMapper extends BaseMapper<AiMessageEntity> {
}

interface AiCitationMapper extends BaseMapper<AiCitationEntity> {
}

@Data
@TableName("ea_ai_session")
class AiSessionEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerUserId;
    private Long projectId;
    private String title;
    private LocalDateTime lastMessageAt;
}

@Data
@TableName("ea_ai_message")
class AiMessageEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private Long ownerUserId;
    private String roleCode;
    private String content;
    private Double confidence;
    private String refusedReason;
}

@Data
@TableName("ea_ai_citation")
class AiCitationEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long messageId;
    private String sourceType;
    private Long sourceId;
    private String sourceTitle;
    private String snippet;
    private Integer pageNo;
}

record AiChatRequest(Long projectId, Long sessionId, @NotBlank String question) {
}

record AiCitationView(String sourceType, Long sourceId, String sourceTitle, String snippet, Integer pageNo) {
}

record AiChatResponse(
        Long sessionId,
        Long messageId,
        String answer,
        List<AiCitationView> citations,
        List<String> usedTools,
        Double confidence,
        String refusedReason
) {
}

record AiSessionSummaryView(Long id, Long projectId, String title, LocalDateTime lastMessageAt,
                            LocalDateTime createdAt) {
}

record AiMessageView(
        Long id,
        String roleCode,
        String content,
        Double confidence,
        String refusedReason,
        List<AiCitationView> citations,
        LocalDateTime createdAt
) {
}

record AiSessionDetailView(Long id, Long projectId, String title, List<AiMessageView> messages) {
}

record InternalPersistMessageRequest(
        Long sessionId,
        Long userId,
        String answer,
        Double confidence,
        String refusedReason
) {
}


