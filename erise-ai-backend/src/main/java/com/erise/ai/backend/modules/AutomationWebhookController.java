package com.erise.ai.backend.modules;

import com.erise.ai.backend.common.api.ApiResponse;
import com.erise.ai.backend.common.config.EriseProperties;
import com.erise.ai.backend.common.exception.BizException;
import com.erise.ai.backend.common.exception.ErrorCodes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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

    @PostMapping("/workflow-status")
    public ApiResponse<AutomationWorkflowStatusWebhookView> workflowStatusWebhook(
            @RequestHeader("X-N8N-Webhook-Secret") String secret,
            @RequestHeader("X-N8N-Signature") String signature,
            @RequestHeader("X-N8N-Signature-Timestamp") String signatureTimestamp,
            @RequestBody String body) {
        return ApiResponse.success(automationWebhookService.receiveWorkflowStatus(secret, signature, signatureTimestamp, body));
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

    AutomationWorkflowStatusWebhookView receiveWorkflowStatus(
            String secret,
            String signature,
            String signatureTimestamp,
            String body) {
        AutomationWorkflowStatusWebhookRequest request;
        try {
            ensureEnabled(secret);
            request = objectMapper.readValue(body, AutomationWorkflowStatusWebhookRequest.class);
            ensureWorkflowCallbackSignature(request, signatureTimestamp, signature, body);
            EventContext eventContext = resolveEventContext(request);
            String normalizedWorkflowStatus = normalizeWorkflowStatus(request.workflowStatus());
            String normalizedManualStatus = normalizeManualStatus(request.manualStatus());
            String errorSummary = firstNonBlank(request.errorSummary(), request.errorMessage());
            jdbcTemplate.update("""
                            update n8n_event_log
                            set workflow_status = ?,
                                workflow_name = coalesce(nullif(?, ''), workflow_name),
                                workflow_version = coalesce(nullif(?, ''), workflow_version),
                                workflow_domain = coalesce(nullif(?, ''), workflow_domain),
                                workflow_owner = coalesce(nullif(?, ''), workflow_owner),
                                external_execution_id = coalesce(nullif(?, ''), external_execution_id),
                                workflow_error_summary = coalesce(nullif(?, ''), workflow_error_summary),
                                workflow_duration_ms = coalesce(?, workflow_duration_ms),
                                manual_status = coalesce(nullif(?, ''), manual_status),
                                manual_reason = coalesce(nullif(?, ''), manual_reason),
                                error_code = coalesce(nullif(?, ''), error_code),
                                error_message = coalesce(nullif(?, ''), error_message),
                                callback_payload_json = ?,
                                last_callback_at = now(),
                                updated_at = now()
                            where id = ?
                            """,
                    normalizedWorkflowStatus,
                    safeValue(request.workflowName()),
                    safeValue(request.workflowVersion()),
                    safeValue(request.workflowDomain()),
                    safeValue(request.workflowOwner()),
                    safeValue(request.externalExecutionId()),
                    safeValue(errorSummary),
                    request.processingTimeMs(),
                    safeValue(normalizedManualStatus),
                    safeValue(firstNonBlank(request.manualReason(), errorSummary)),
                    safeValue(request.errorCode()),
                    safeValue(firstNonBlank(request.errorMessage(), errorSummary)),
                    body,
                    eventContext.eventLogId());
            insertWorkflowAuditIfNeeded(eventContext, request, normalizedWorkflowStatus, normalizedManualStatus, body);
            insertLog(request.requestId(), request.workflowCode(), request.eventType(), 200, true, null, body);
            return new AutomationWorkflowStatusWebhookView(
                    eventContext.eventLogId(),
                    normalizedWorkflowStatus,
                    normalizedManualStatus,
                    request.externalExecutionId()
            );
        } catch (BizException exception) {
            String requestId = requestIdFromBody(body);
            String workflowCode = workflowCodeFromBody(body);
            String eventType = eventTypeFromBody(body);
            insertLog(requestId, workflowCode, eventType, exception.getStatus().value(), false, exception.getMessage(), body);
            throw exception;
        } catch (Exception exception) {
            String requestId = requestIdFromBody(body);
            String workflowCode = workflowCodeFromBody(body);
            String eventType = eventTypeFromBody(body);
            insertLog(requestId, workflowCode, eventType, HttpStatus.INTERNAL_SERVER_ERROR.value(), false, exception.getMessage(), body);
            throw new BizException(ErrorCodes.SERVER_ERROR, "Failed to process n8n workflow callback", HttpStatus.INTERNAL_SERVER_ERROR);
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

    private void ensureWorkflowCallbackSignature(
            AutomationWorkflowStatusWebhookRequest request,
            String signatureTimestamp,
            String signature,
            String body) {
        if (signatureTimestamp == null || signatureTimestamp.isBlank() || signature == null || signature.isBlank()) {
            throw new BizException(ErrorCodes.UNAUTHORIZED, "Missing n8n callback signature headers", HttpStatus.UNAUTHORIZED);
        }
        String secret = properties.getN8n().getWebhookSecret();
        String message = String.join("\n",
                safeValue(request.requestId()),
                safeValue(request.eventType()),
                safeValue(request.workflowCode()),
                signatureTimestamp.trim(),
                body);
        String expected = hmacSha256(secret, message);
        if (!expected.equalsIgnoreCase(signature.trim())) {
            throw new BizException(ErrorCodes.UNAUTHORIZED, "Invalid n8n callback signature", HttpStatus.UNAUTHORIZED);
        }
    }

    private EventContext resolveEventContext(AutomationWorkflowStatusWebhookRequest request) {
        if (request.eventLogId() != null) {
            EventContext context = jdbcTemplate.query("""
                            select id, request_id, approval_id
                            from n8n_event_log
                            where id = ?
                            """,
                    rs -> rs.next() ? new EventContext(
                            rs.getLong("id"),
                            rs.getString("request_id"),
                            nullableLong(rs, "approval_id")
                    ) : null,
                    request.eventLogId());
            if (context != null) {
                return context;
            }
        }
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            EventContext context = jdbcTemplate.query("""
                            select id, request_id, approval_id
                            from n8n_event_log
                            where idempotency_key = ?
                            order by id desc
                            limit 1
                            """,
                    rs -> rs.next() ? new EventContext(
                            rs.getLong("id"),
                            rs.getString("request_id"),
                            nullableLong(rs, "approval_id")
                    ) : null,
                    request.idempotencyKey().trim());
            if (context != null) {
                return context;
            }
        }
        throw new BizException(ErrorCodes.BAD_REQUEST, "n8n callback could not resolve event log by eventLogId or idempotencyKey", HttpStatus.BAD_REQUEST);
    }

    private void insertWorkflowAuditIfNeeded(
            EventContext eventContext,
            AutomationWorkflowStatusWebhookRequest request,
            String workflowStatus,
            String manualStatus,
            String body) {
        if (eventContext.approvalId() == null) {
            return;
        }
        ApprovalAuditContext approval = jdbcTemplate.query("""
                        select id, request_id, initiated_user_id, confirmed_user_id, executed_user_id, action_code, target_type, target_id
                        from approval_request
                        where id = ?
                        """,
                rs -> rs.next() ? new ApprovalAuditContext(
                        rs.getLong("id"),
                        rs.getString("request_id"),
                        rs.getLong("initiated_user_id"),
                        nullableLong(rs, "confirmed_user_id"),
                        nullableLong(rs, "executed_user_id"),
                        rs.getString("action_code"),
                        rs.getString("target_type"),
                        nullableLong(rs, "target_id")
                ) : null,
                eventContext.approvalId());
        if (approval == null) {
            return;
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("eventLogId", eventContext.eventLogId());
        detail.put("workflowCode", request.workflowCode());
        detail.put("workflowName", request.workflowName());
        detail.put("workflowVersion", request.workflowVersion());
        detail.put("workflowDomain", request.workflowDomain());
        detail.put("workflowOwner", request.workflowOwner());
        detail.put("workflowStatus", workflowStatus);
        detail.put("manualStatus", manualStatus);
        detail.put("externalExecutionId", request.externalExecutionId());
        detail.put("errorCode", request.errorCode());
        detail.put("errorSummary", firstNonBlank(request.errorSummary(), request.errorMessage()));
        detail.put("processingTimeMs", request.processingTimeMs());
        detail.put("callbackPayload", deserializeBody(body));
        String actionStatus = manualStatus != null && !manualStatus.isBlank()
                ? "WORKFLOW_MANUAL_" + manualStatus
                : "WORKFLOW_" + workflowStatus;
        jdbcTemplate.update("""
                        insert into admin_action_request (
                            approval_request_id,
                            request_id,
                            initiated_user_id,
                            confirmed_user_id,
                            executed_user_id,
                            action_code,
                            action_status,
                            target_type,
                            target_id,
                            audit_payload_json
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                approval.approvalId(),
                approval.requestId(),
                approval.initiatedUserId(),
                approval.confirmedUserId(),
                approval.executedUserId(),
                approval.actionCode(),
                actionStatus,
                approval.targetType(),
                approval.targetId(),
                serializeRequest(detail));
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

    private void insertLog(
            String requestId,
            String workflowCode,
            String eventType,
            int statusCode,
            boolean success,
            String errorMessage,
            Object request) {
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
                requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId,
                workflowCode == null || workflowCode.isBlank() ? "UNKNOWN_WORKFLOW" : workflowCode,
                eventType == null || eventType.isBlank() ? "unknown" : eventType,
                statusCode,
                success ? 1 : 0,
                errorMessage,
                serializeRequest(request));
    }

    private String serializeRequest(Object request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            return "{\"fallback\":\"serialization_failed\"}";
        }
    }

    private Object deserializeBody(String body) {
        try {
            return objectMapper.readValue(body, Object.class);
        } catch (Exception exception) {
            return body;
        }
    }

    private String requestIdFromBody(String body) {
        return jsonField(body, "requestId");
    }

    private String workflowCodeFromBody(String body) {
        return jsonField(body, "workflowCode");
    }

    private String eventTypeFromBody(String body) {
        return jsonField(body, "eventType");
    }

    private String jsonField(String body, String field) {
        try {
            Object value = objectMapper.readTree(body).path(field).isMissingNode() ? null : objectMapper.readTree(body).path(field).asText(null);
            return value == null ? null : value.toString();
        } catch (Exception exception) {
            return null;
        }
    }

    private String normalizeWorkflowStatus(String value) {
        String normalized = safeValue(value).toUpperCase();
        return switch (normalized) {
            case "RUNNING", "PROCESSING" -> "RUNNING";
            case "COMPLETED", "SUCCESS" -> "COMPLETED";
            case "FAILED", "ERROR" -> "FAILED";
            case "CANCELLED", "ABORTED", "REJECTED" -> "CANCELLED";
            case "PENDING", "QUEUED" -> "PENDING";
            case "NOT_STARTED" -> "NOT_STARTED";
            default -> throw new BizException(ErrorCodes.BAD_REQUEST, "Unsupported workflowStatus: " + value, HttpStatus.BAD_REQUEST);
        };
    }

    private String normalizeManualStatus(String value) {
        String normalized = safeValue(value).toUpperCase();
        if (normalized.isBlank()) {
            return null;
        }
        return switch (normalized) {
            case "PENDING" -> "PENDING";
            case "RESOLVED", "DONE" -> "RESOLVED";
            default -> throw new BizException(ErrorCodes.BAD_REQUEST, "Unsupported manualStatus: " + value, HttpStatus.BAD_REQUEST);
        };
    }

    private String hmacSha256(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new BizException(ErrorCodes.SERVER_ERROR, "Failed to validate n8n callback signature", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.trim();
            }
        }
        return null;
    }

    private String safeValue(String value) {
        return value == null ? "" : value.trim();
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

record AutomationWorkflowStatusWebhookRequest(
        Long eventLogId,
        String requestId,
        String idempotencyKey,
        @NotBlank String workflowCode,
        @NotBlank String eventType,
        @NotBlank String workflowStatus,
        String workflowName,
        String workflowVersion,
        String workflowDomain,
        String workflowOwner,
        String externalExecutionId,
        String errorCode,
        String errorMessage,
        String errorSummary,
        Integer processingTimeMs,
        String manualStatus,
        String manualReason
) {
}

record AutomationWorkflowStatusWebhookView(
        Long eventLogId,
        String workflowStatus,
        String manualStatus,
        String externalExecutionId
) {
}

record EventContext(
        Long eventLogId,
        String requestId,
        Long approvalId
) {
}

record ApprovalAuditContext(
        Long approvalId,
        String requestId,
        Long initiatedUserId,
        Long confirmedUserId,
        Long executedUserId,
        String actionCode,
        String targetType,
        Long targetId
) {
}
