package com.erise.ai.backend.modules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erise.ai.backend.common.exception.BizException;
import com.erise.ai.backend.common.security.CurrentUser;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AiFeedbackServiceTest {

    private final JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
    private final AuditLogService auditLogService = org.mockito.Mockito.mock(AuditLogService.class);
    private final AiFeedbackService aiFeedbackService = new AiFeedbackService(jdbcTemplate, auditLogService);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void submitRejectsMessageOutsideCurrentUsersScope() {
        authenticate(7L, "alice", "USER");
        when(jdbcTemplate.query(any(String.class), org.mockito.ArgumentMatchers.<ResultSetExtractor<OwnedAiMessage>>any(), eq(101L), eq(7L)))
                .thenReturn(null);

        BizException error = assertThrows(
                BizException.class,
                () -> aiFeedbackService.submit(101L, new AiMessageFeedbackRequest("UP", "not mine"))
        );

        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, error.getStatus());
        verify(jdbcTemplate, never()).update(any(String.class), any(), any(), any(), any());
        verify(auditLogService, never()).log(any(CurrentUser.class), any(String.class), any(String.class), any(Long.class), any(Object.class));
    }

    @Test
    void submitUpdatesOwnedAssistantMessageAndWritesAuditLog() {
        authenticate(9L, "bob", "USER");
        when(jdbcTemplate.query(any(String.class), org.mockito.ArgumentMatchers.<ResultSetExtractor<OwnedAiMessage>>any(), eq(202L), eq(9L)))
                .thenReturn(new OwnedAiMessage(202L, 55L));
        when(jdbcTemplate.update(any(String.class), eq("DOWN"), eq("needs citation"), eq(202L), eq(9L)))
                .thenReturn(1);

        aiFeedbackService.submit(202L, new AiMessageFeedbackRequest("DOWN", "needs citation"));

        verify(jdbcTemplate).update(any(String.class), eq("DOWN"), eq("needs citation"), eq(202L), eq(9L));
        verify(auditLogService).log(
                eq((CurrentUser) new CurrentUser(9L, "bob", "USER")),
                eq("AI_MESSAGE_FEEDBACK"),
                eq("AI_MESSAGE"),
                eq(202L),
                eq((Object) Map.of("feedbackType", "DOWN", "sessionId", 55L))
        );
    }

    private void authenticate(Long userId, String username, String roleCode) {
        CurrentUser principal = new CurrentUser(userId, username, roleCode);
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
