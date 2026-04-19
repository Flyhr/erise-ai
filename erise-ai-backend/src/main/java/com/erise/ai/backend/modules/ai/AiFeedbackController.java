package com.erise.ai.backend.modules;

import com.erise.ai.backend.common.api.ApiResponse;
import com.erise.ai.backend.common.exception.BizException;
import com.erise.ai.backend.common.exception.ErrorCodes;
import com.erise.ai.backend.common.util.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiFeedbackController {

    private final AiFeedbackService aiFeedbackService;

    @PostMapping("/messages/{id}/feedback")
    public ApiResponse<Void> submitFeedback(@PathVariable Long id, @Valid @RequestBody AiMessageFeedbackRequest request) {
        aiFeedbackService.submit(id, request);
        return ApiResponse.success("success", null);
    }
}

@Service
@RequiredArgsConstructor
class AiFeedbackService {

    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;

    void submit(Long messageId, AiMessageFeedbackRequest request) {
        var currentUser = SecurityUtils.currentUser();
        OwnedAiMessage ownedMessage = loadOwnedAssistantMessage(messageId, currentUser.userId());
        if (ownedMessage == null) {
            throw new BizException(ErrorCodes.NOT_FOUND, "AI assistant message not found", HttpStatus.NOT_FOUND);
        }
        String feedbackType = normalizeFeedbackType(request.feedbackType());
        int updated = jdbcTemplate.update("""
                        update ai_message_feedback
                        set feedback_type = ?,
                            feedback_note = ?,
                            updated_at = current_timestamp(6)
                        where message_id = ? and user_id = ?
                        """,
                feedbackType,
                trimToNull(request.feedbackNote()),
                messageId,
                currentUser.userId());
        if (updated <= 0) {
            jdbcTemplate.update("""
                            insert into ai_message_feedback (message_id, session_id, user_id, feedback_type, feedback_note,
                                                             created_at, updated_at)
                            values (?, ?, ?, ?, ?, current_timestamp(6), current_timestamp(6))
                            """,
                    messageId,
                    ownedMessage.sessionId(),
                    currentUser.userId(),
                    feedbackType,
                    trimToNull(request.feedbackNote()));
        }
        auditLogService.log(currentUser, "AI_MESSAGE_FEEDBACK", "AI_MESSAGE", messageId,
                Map.of("feedbackType", feedbackType, "sessionId", ownedMessage.sessionId()));
    }

    private OwnedAiMessage loadOwnedAssistantMessage(Long messageId, Long userId) {
        return jdbcTemplate.query("""
                        select m.id, m.session_id
                        from ai_chat_message m
                        join ai_chat_session s on s.id = m.session_id
                        where m.id = ?
                          and s.user_id = ?
                          and lower(m.role) = 'assistant'
                        limit 1
                        """,
                rs -> rs.next() ? new OwnedAiMessage(rs.getLong("id"), rs.getLong("session_id")) : null,
                messageId,
                userId);
    }

    private String normalizeFeedbackType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!"UP".equals(normalized) && !"DOWN".equals(normalized)) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "Feedback type must be UP or DOWN", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}

record AiMessageFeedbackRequest(
        @NotBlank String feedbackType,
        @Size(max = 500) String feedbackNote
) {
}

record OwnedAiMessage(Long id, Long sessionId) {
}
