package com.erise.ai.backend.integration.ai;

import com.erise.ai.backend.common.config.EriseProperties;
import com.erise.ai.backend.common.exception.BizException;
import com.erise.ai.backend.common.exception.ErrorCodes;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class RagSyncClient {

    private final RestTemplate restTemplate;
    private final EriseProperties properties;

    public void upsert(RagUpsertRequest request) {
        post("/internal/ai/rag/index/upsert", request, request.userId());
    }

    public void delete(RagDeleteRequest request) {
        post("/internal/ai/rag/index/delete", request, request.userId());
    }

    private void post(String path, Object body, Long userId) {
        try {
            restTemplate.exchange(
                    properties.getCloud().getBaseUrl() + path,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers(userId)),
                    String.class
            );
        } catch (RestClientException exception) {
            throw new BizException(ErrorCodes.AI_ERROR, "RAG sync failed: " + exception.getMessage());
        }
    }

    private HttpHeaders headers(Long userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Service-Token", properties.getInternal().getApiKey());
        headers.set("X-Internal-Key", properties.getInternal().getApiKey());
        headers.set("X-Request-Id", UUID.randomUUID().toString());
        if (userId != null) {
            headers.set("X-User-Id", String.valueOf(userId));
        }
        headers.set("X-Org-Id", "0");
        return headers;
    }

    public record RagUpsertRequest(
            Long userId,
            Long projectId,
            Long sessionId,
            String sourceType,
            Long sourceId,
            String sourceName,
            List<RagChunkPayload> chunks,
            LocalDateTime updatedAt
    ) {
    }

    public record RagDeleteRequest(Long userId, Long projectId, Long sessionId, String sourceType, Long sourceId) {
    }

    public record RagChunkPayload(Integer chunkNum, String chunkText, Integer pageNo, String sectionPath) {
    }
}
