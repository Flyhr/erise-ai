package com.erise.ai.backend.modules;

import com.erise.ai.backend.common.api.ApiResponse;
import com.erise.ai.backend.common.config.EriseProperties;
import com.erise.ai.backend.common.exception.BizException;
import com.erise.ai.backend.common.exception.ErrorCodes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/automation/webhooks")
@RequiredArgsConstructor
public class AutomationWebhookController {

    private final AutomationWebhookService automationWebhookService;

    @PostMapping("/notifications")
    public ApiResponse<Void> notificationWebhook(
            @RequestHeader("X-N8N-Webhook-Secret") String secret,
            @Valid @RequestBody AutomationNotificationWebhookRequest request) {
        automationWebhookService.receiveNotification(secret, request);
        return ApiResponse.success("success", null);
    }
}

@RequiredArgsConstructor
@org.springframework.stereotype.Service
class AutomationWebhookService {

    private final JdbcTemplate jdbcTemplate;
    private final EriseProperties properties;
    private final ObjectMapper objectMapper;

    void receiveNotification(String secret, AutomationNotificationWebhookRequest request) {
        String requestId = request.requestId() == null || request.requestId().isBlank() ? UUID.randomUUID().toString() : request.requestId();
        try {
            ensureEnabled(secret);
            List<Long> targets = resolveTargetUserIds(request);
            if (targets.isEmpty()) {
                throw new BizException(ErrorCodes.BAD_REQUEST, "No notification recipients found", HttpStatus.BAD_REQUEST);
            }
            for (Long userId : targets) {
                jdbcTemplate.update("""
                                insert into ea_user_notification (
                                    user_id,
                                    notification_type,
                                    title,
                                    content,
                                    read_flag,
                                    broadcast_flag,
                                    created_by,
                                    updated_by
                                ) values (?, ?, ?, ?, 0, ?, 0, 0)
                                """,
                        userId,
                        request.notificationType() == null || request.notificationType().isBlank() ? "N8N_EVENT" : request.notificationType().trim(),
                        request.title().trim(),
                        request.content().trim(),
                        Boolean.TRUE.equals(request.sendToAll()) ? 1 : 0);
            }
            insertLog(requestId, request.workflowCode(), request.eventType(), 200, true, null, request);
        } catch (BizException exception) {
            insertLog(requestId, request.workflowCode(), request.eventType(), exception.getStatus().value(), false, exception.getMessage(), request);
            throw exception;
        }
    }

    private void ensureEnabled(String secret) {
        if (!properties.getN8n().isEnabled()) {
            throw new BizException(ErrorCodes.FORBIDDEN, "n8n integration is disabled", HttpStatus.FORBIDDEN);
        }
        String expected = properties.getN8n().getWebhookSecret();
        if (expected == null || expected.isBlank() || !expected.equals(secret)) {
            throw new BizException(ErrorCodes.UNAUTHORIZED, "Invalid n8n webhook secret", HttpStatus.UNAUTHORIZED);
        }
    }

    private List<Long> resolveTargetUserIds(AutomationNotificationWebhookRequest request) {
        if (Boolean.TRUE.equals(request.sendToAll())) {
            return jdbcTemplate.query("select id from ea_user where deleted = 0 order by id asc", (rs, rowNum) -> rs.getLong("id"));
        }
        LinkedHashSet<Long> requestedIds = new LinkedHashSet<>(
                request.userIds() == null ? List.of() : request.userIds().stream().filter(Objects::nonNull).toList());
        if (requestedIds.isEmpty()) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "Notification userIds are required when sendToAll=false", HttpStatus.BAD_REQUEST);
        }
        List<Object> params = new ArrayList<>(requestedIds);
        String placeholders = String.join(", ", requestedIds.stream().map(id -> "?").toList());
        String sql = "select id from ea_user where deleted = 0 and id in (" + placeholders + ") order by id asc";
        List<Long> existingIds = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("id"), params.toArray());
        if (existingIds.size() != requestedIds.size()) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "Some notification recipients do not exist", HttpStatus.BAD_REQUEST);
        }
        return existingIds;
    }

    private void insertLog(String requestId, String workflowCode, String eventType, int statusCode, boolean success, String errorMessage, AutomationNotificationWebhookRequest request) {
        jdbcTemplate.update("""
                        insert into automation_webhook_log (
                            request_id,
                            workflow_code,
                            event_type,
                            status_code,
                            success_flag,
                            error_message,
                            request_payload_json
                        ) values (?, ?, ?, ?, ?, ?, ?)
                        """,
                requestId,
                workflowCode,
                eventType,
                statusCode,
                success ? 1 : 0,
                errorMessage,
                serializeRequest(request));
    }

    private String serializeRequest(AutomationNotificationWebhookRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            return "{\"requestId\":\"" + safeValue(request.requestId()) + "\","
                    + "\"workflowCode\":\"" + safeValue(request.workflowCode()) + "\","
                    + "\"eventType\":\"" + safeValue(request.eventType()) + "\","
                    + "\"title\":\"" + safeValue(request.title()) + "\"}";
        }
    }

    private String safeValue(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

record AutomationNotificationWebhookRequest(
        String requestId,
        @NotBlank String workflowCode,
        @NotBlank String eventType,
        @NotBlank String title,
        @NotBlank String content,
        String notificationType,
        Boolean sendToAll,
        List<Long> userIds
) {
}
