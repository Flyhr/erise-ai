package com.erise.ai.backend.integration.ai;

import com.erise.ai.backend.common.config.EriseProperties;
import com.erise.ai.backend.common.exception.BizException;
import com.erise.ai.backend.common.exception.ErrorCodes;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;

@Component
@RequiredArgsConstructor
public class CloudAiClient {

    private final RestTemplate restTemplate;
    private final WebClient.Builder webClientBuilder = WebClient.builder();
    private final EriseProperties properties;

    public ChatResponse chat(ChatRequest request) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Key", properties.getInternal().getApiKey());
            return restTemplate.postForObject(
                    properties.getCloud().getBaseUrl() + "/internal/v1/ai/chat",
                    new HttpEntity<>(request, headers),
                    ChatResponse.class
            );
        } catch (RestClientException exception) {
            throw new BizException(ErrorCodes.AI_ERROR, "AI service unavailable: " + exception.getMessage());
        }
    }

    public Flux<String> stream(ChatRequest request) {
        WebClient client = webClientBuilder
                .baseUrl(properties.getCloud().getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create().responseTimeout(Duration.ofSeconds(60))))
                .defaultHeader("X-Internal-Key", properties.getInternal().getApiKey())
                .build();
        return client.post()
                .uri("/internal/v1/ai/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(String.class)
                .onErrorMap(error -> new BizException(ErrorCodes.AI_ERROR, "AI stream unavailable: " + error.getMessage()));
    }

    public record ChatRequest(
            Long userId,
            String username,
            String roleCode,
            Long sessionId,
            Long projectId,
            String question
    ) {
    }

    public record ChatResponse(
            String answer,
            java.util.List<Citation> citations,
            java.util.List<String> usedTools,
            Double confidence,
            String refusedReason
    ) {
    }

    public record Citation(
            String sourceType,
            Long sourceId,
            String sourceTitle,
            String snippet,
            Integer pageNo
    ) {
    }
}
