package com.erise.ai.backend.modules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class AiMcpProxySecurityTest {

    @Test
    void controllerPassesThroughForbiddenMcpResponseForNormalUserToken() {
        AiService aiService = mock(AiService.class);
        AiController controller = new AiController(aiService);
        String body = "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"tools/call\",\"params\":{\"name\":\"projects.get\",\"arguments\":{\"projectId\":999}}}";
        ResponseEntity<String> upstream = ResponseEntity.status(403)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"error\":{\"message\":\"No permission to access this Erise resource\"}}");
        when(aiService.mcpProxy(eq("Bearer user-token"), eq(body))).thenReturn(upstream);

        ResponseEntity<String> response = controller.mcpProxy("Bearer user-token", body);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getBody()).isEqualTo(upstream.getBody());
        verify(aiService).mcpProxy(eq("Bearer user-token"), eq(body));
    }
}
