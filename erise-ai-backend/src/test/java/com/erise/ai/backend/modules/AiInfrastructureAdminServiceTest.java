package com.erise.ai.backend.modules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erise.ai.backend.common.exception.BizException;
import com.erise.ai.backend.common.exception.ErrorCodes;
import com.erise.ai.backend.common.security.CurrentUser;
import com.erise.ai.backend.integration.ai.CloudAiClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AiInfrastructureAdminServiceTest {

    @Test
    void overviewFallsBackPerSectionInsteadOfFailingWholePage() {
        CloudAiClient cloudAiClient = mock(CloudAiClient.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        CurrentUser currentUser = new CurrentUser(1L, "admin", "ADMIN");
        AiInfrastructureAdminService service = new AiInfrastructureAdminService(cloudAiClient, auditLogService) {
            @Override
            CurrentUser currentUser() {
                return currentUser;
            }

            @Override
            String requestId() {
                return "req-ai-infra-test";
            }
        };

        CloudAiClient.AiServiceHealthResponse serviceHealth = new CloudAiClient.AiServiceHealthResponse(
                "erise-ai-chat-service",
                "UP",
                "UP",
                "UP",
                new CloudAiClient.ModelHealthResponse("UP", List.of())
        );
        CloudAiClient.N8nEventSummaryResponse n8nSummary = new CloudAiClient.N8nEventSummaryResponse(
                24,
                6,
                4,
                1,
                1,
                0,
                0,
                1,
                0,
                1,
                66.7,
                null,
                List.of()
        );

        when(cloudAiClient.serviceHealth(currentUser, "req-ai-infra-test")).thenReturn(serviceHealth);
        when(cloudAiClient.providerHealth(currentUser, "req-ai-infra-test")).thenThrow(
                new BizException(ErrorCodes.AI_ERROR, "Provider inventory unavailable", HttpStatus.BAD_GATEWAY)
        );
        when(cloudAiClient.n8nEventSummary(currentUser, 24, "req-ai-infra-test")).thenReturn(n8nSummary);
        when(cloudAiClient.fileCapabilities(currentUser, "req-ai-infra-test")).thenThrow(
                new BizException(ErrorCodes.AI_ERROR, "File capability matrix unavailable", HttpStatus.BAD_GATEWAY)
        );

        AiInfrastructureOverviewView overview = service.overview(24);

        assertThat(overview.serviceHealth()).isEqualTo(serviceHealth);
        assertThat(overview.providers().status()).isEqualTo("DOWN");
        assertThat(overview.providers().effectiveRoutes()).isEmpty();
        assertThat(overview.n8nSummary()).isEqualTo(n8nSummary);
        assertThat(overview.fileCapabilities().fileTypes()).isEmpty();
        assertThat(overview.warnings())
                .extracting(AiInfrastructureWarningView::section)
                .containsExactly("PROVIDER_HEALTH", "FILE_CAPABILITIES");
        assertThat(overview.warnings())
                .extracting(AiInfrastructureWarningView::message)
                .containsExactly("Provider inventory unavailable", "File capability matrix unavailable");
    }
}
