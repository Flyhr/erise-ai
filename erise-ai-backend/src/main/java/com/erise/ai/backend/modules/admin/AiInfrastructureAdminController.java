package com.erise.ai.backend.modules;

import com.erise.ai.backend.common.api.ApiResponse;
import com.erise.ai.backend.common.api.PageResponse;
import com.erise.ai.backend.common.exception.BizException;
import com.erise.ai.backend.common.security.CurrentUser;
import com.erise.ai.backend.common.util.SecurityUtils;
import com.erise.ai.backend.integration.ai.CloudAiClient;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/ai/infrastructure")
@RequiredArgsConstructor
@Validated
public class AiInfrastructureAdminController {

    private final AiInfrastructureAdminService aiInfrastructureAdminService;

    @GetMapping("/overview")
    public ApiResponse<AiInfrastructureOverviewView> overview(
            @RequestParam(defaultValue = "24") @Min(1) @Max(168) int hours) {
        return ApiResponse.success(aiInfrastructureAdminService.overview(hours));
    }

    @GetMapping("/n8n/events")
    public ApiResponse<PageResponse<CloudAiClient.N8nEventResponse>> n8nEvents(
            @RequestParam(defaultValue = "1") @Min(1) long pageNum,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String deliveryStatus,
            @RequestParam(required = false) String workflowStatus,
            @RequestParam(required = false) String manualStatus,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdDate) {
        return ApiResponse.success(
                aiInfrastructureAdminService.n8nEvents(pageNum, pageSize, q, deliveryStatus, workflowStatus, manualStatus, eventType, createdDate)
        );
    }

    @GetMapping("/n8n/events/{eventId}")
    public ApiResponse<CloudAiClient.N8nEventDetailResponse> n8nEventDetail(@PathVariable Long eventId) {
        return ApiResponse.success(aiInfrastructureAdminService.n8nEventDetail(eventId));
    }

    @PostMapping("/n8n/events/{eventId}/retry")
    public ApiResponse<CloudAiClient.N8nRetryResponse> retryN8nEvent(@PathVariable Long eventId) {
        return ApiResponse.success(aiInfrastructureAdminService.retryN8nEvent(eventId));
    }

    @PostMapping("/n8n/events/{eventId}/manual-handoff")
    public ApiResponse<CloudAiClient.N8nEventResponse> manualHandoffN8nEvent(
            @PathVariable Long eventId,
            @RequestBody(required = false) N8nManualHandoffRequest request) {
        String reason = request == null ? null : request.reason();
        return ApiResponse.success(aiInfrastructureAdminService.manualHandoffN8nEvent(eventId, reason));
    }
}

@Service
@RequiredArgsConstructor
class AiInfrastructureAdminService {

    private final CloudAiClient cloudAiClient;
    private final AuditLogService auditLogService;

    AiInfrastructureOverviewView overview(int hours) {
        CurrentUser currentUser = currentUser();
        String requestId = requestId();
        List<AiInfrastructureWarningView> warnings = new ArrayList<>();
        CloudAiClient.AiServiceHealthResponse serviceHealth = safeOverviewSection(
                "SERVICE_HEALTH",
                () -> cloudAiClient.serviceHealth(currentUser, requestId),
                () -> fallbackServiceHealth(),
                warnings
        );
        CloudAiClient.AiProviderHealthInventoryResponse providerHealth = safeOverviewSection(
                "PROVIDER_HEALTH",
                () -> cloudAiClient.providerHealth(currentUser, requestId),
                () -> fallbackProviderHealth(),
                warnings
        );
        CloudAiClient.N8nEventSummaryResponse n8nSummary = safeOverviewSection(
                "N8N_SUMMARY",
                () -> cloudAiClient.n8nEventSummary(currentUser, hours, requestId),
                () -> fallbackN8nSummary(hours),
                warnings
        );
        CloudAiClient.FileCapabilityMatrixResponse fileCapabilities = safeOverviewSection(
                "FILE_CAPABILITIES",
                () -> cloudAiClient.fileCapabilities(currentUser, requestId),
                () -> fallbackFileCapabilities(),
                warnings
        );
        return new AiInfrastructureOverviewView(
                serviceHealth,
                providerHealth,
                n8nSummary,
                fileCapabilities,
                warnings
        );
    }

    PageResponse<CloudAiClient.N8nEventResponse> n8nEvents(long pageNum,
                                                           long pageSize,
                                                           String q,
                                                           String deliveryStatus,
                                                           String workflowStatus,
                                                           String manualStatus,
                                                           String eventType,
                                                           LocalDate createdDate) {
        CurrentUser currentUser = currentUser();
        CloudAiClient.PageResult<CloudAiClient.N8nEventResponse> page = cloudAiClient.n8nEvents(
                currentUser,
                Math.max((int) pageNum, 1),
                Math.max((int) pageSize, 1),
                q,
                deliveryStatus,
                workflowStatus,
                manualStatus,
                eventType,
                createdDate,
                requestId()
        );
        return PageResponse.of(page.records(), page.pageNum(), page.pageSize(), page.total());
    }

    CloudAiClient.N8nEventDetailResponse n8nEventDetail(Long eventId) {
        CurrentUser currentUser = currentUser();
        return cloudAiClient.n8nEventDetail(currentUser, eventId, requestId());
    }

    CloudAiClient.N8nRetryResponse retryN8nEvent(Long eventId) {
        CurrentUser currentUser = currentUser();
        CloudAiClient.N8nRetryResponse result = cloudAiClient.retryN8nEvent(currentUser, eventId, requestId());
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("sourceEventId", eventId);
        detail.put("retried", result.retried());
        if (result.event() != null) {
            detail.put("eventId", result.event().id());
            detail.put("deliveryStatus", result.event().deliveryStatus());
            detail.put("workflowStatus", result.event().workflowStatus());
            detail.put("statusCode", result.event().statusCode());
        }
        auditLogService.log(currentUser, "ADMIN_AI_N8N_RETRY", "N8N_EVENT_LOG", eventId, detail);
        return result;
    }

    CloudAiClient.N8nEventResponse manualHandoffN8nEvent(Long eventId, String reason) {
        CurrentUser currentUser = currentUser();
        CloudAiClient.N8nEventResponse result = cloudAiClient.manualHandoffN8nEvent(currentUser, eventId, reason, requestId());
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("eventId", eventId);
        detail.put("manualStatus", result.manualStatus());
        detail.put("manualReason", result.manualReason());
        auditLogService.log(currentUser, "ADMIN_AI_N8N_MANUAL_HANDOFF", "N8N_EVENT_LOG", eventId, detail);
        return result;
    }

    CurrentUser currentUser() {
        return SecurityUtils.currentUser();
    }

    String requestId() {
        String traceId = MDC.get("traceId");
        return traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId;
    }

    private <T> T safeOverviewSection(String section,
                                      Supplier<T> loader,
                                      Supplier<T> fallback,
                                      List<AiInfrastructureWarningView> warnings) {
        try {
            return loader.get();
        } catch (BizException exception) {
            warnings.add(new AiInfrastructureWarningView(section, sanitizeWarningMessage(exception.getMessage())));
            return fallback.get();
        } catch (RuntimeException exception) {
            warnings.add(new AiInfrastructureWarningView(section, sanitizeWarningMessage(exception.getMessage())));
            return fallback.get();
        }
    }

    private String sanitizeWarningMessage(String message) {
        return message == null || message.isBlank()
                ? "AI 基础设施子项暂时不可用，请稍后重试"
                : message;
    }

    private CloudAiClient.AiServiceHealthResponse fallbackServiceHealth() {
        return new CloudAiClient.AiServiceHealthResponse(
                "erise-ai-chat-service",
                "DOWN",
                "UNKNOWN",
                "UNKNOWN",
                new CloudAiClient.ModelHealthResponse("DOWN", List.of())
        );
    }

    private CloudAiClient.AiProviderHealthInventoryResponse fallbackProviderHealth() {
        return new CloudAiClient.AiProviderHealthInventoryResponse(
                "DOWN",
                LocalDateTime.now(),
                null,
                null,
                List.of(),
                List.of()
        );
    }

    private CloudAiClient.N8nEventSummaryResponse fallbackN8nSummary(int hours) {
        return new CloudAiClient.N8nEventSummaryResponse(
                hours,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0D,
                null,
                List.of()
        );
    }

    private CloudAiClient.FileCapabilityMatrixResponse fallbackFileCapabilities() {
        return new CloudAiClient.FileCapabilityMatrixResponse(
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }
}

record AiInfrastructureOverviewView(
        CloudAiClient.AiServiceHealthResponse serviceHealth,
        CloudAiClient.AiProviderHealthInventoryResponse providers,
        CloudAiClient.N8nEventSummaryResponse n8nSummary,
        CloudAiClient.FileCapabilityMatrixResponse fileCapabilities,
        List<AiInfrastructureWarningView> warnings
) {
}

record AiInfrastructureWarningView(
        String section,
        String message
) {
}

record N8nManualHandoffRequest(String reason) {
}
