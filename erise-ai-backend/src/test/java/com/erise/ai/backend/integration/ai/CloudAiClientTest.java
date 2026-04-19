package com.erise.ai.backend.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erise.ai.backend.common.config.EriseProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

class CloudAiClientTest {

    @Test
    void mcpProxyForwardsAuthorizationRequestIdAndBody() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        EriseProperties properties = new EriseProperties();
        properties.getCloud().setBaseUrl("http://cloud:8081");
        CloudAiClient client = new CloudAiClient(restTemplate, new ObjectMapper(), properties);

        when(restTemplate.exchange(
                eq("http://cloud:8081/mcp"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{}}"));

        ResponseEntity<String> response = client.mcpProxy("Bearer user-token", "req-mcp-1", "{\"jsonrpc\":\"2.0\"}");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<String>> entityCaptor = ArgumentCaptor.forClass((Class) HttpEntity.class);
        verify(restTemplate).exchange(
                eq("http://cloud:8081/mcp"),
                eq(HttpMethod.POST),
                entityCaptor.capture(),
                eq(String.class)
        );

        HttpEntity<String> entity = entityCaptor.getValue();
        assertThat(entity.getHeaders().getFirst("Authorization")).isEqualTo("Bearer user-token");
        assertThat(entity.getHeaders().getFirst("X-Request-Id")).isEqualTo("req-mcp-1");
        assertThat(entity.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(entity.getBody()).isEqualTo("{\"jsonrpc\":\"2.0\"}");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void mcpProxyPassesThroughUpstreamErrorPayload() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        EriseProperties properties = new EriseProperties();
        properties.getCloud().setBaseUrl("http://cloud:8081");
        CloudAiClient client = new CloudAiClient(restTemplate, new ObjectMapper(), properties);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"error\":{\"message\":\"No permission to access this Erise resource\"}}";
        when(restTemplate.exchange(
                eq("http://cloud:8081/mcp"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenThrow(HttpClientErrorException.create(
                HttpStatus.FORBIDDEN,
                "Forbidden",
                headers,
                body.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        ));

        ResponseEntity<String> response = client.mcpProxy("Bearer user-token", "req-mcp-2", "{\"jsonrpc\":\"2.0\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getBody()).isEqualTo(body);
    }
}
