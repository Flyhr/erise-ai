package com.erise.ai.cloud.integration;

import com.erise.ai.cloud.config.EriseCloudProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Legacy backend integration client kept only for reference.
 * Runtime retrieval/project-context calls are now made by the Python AI service.
 */
// @Component
@RequiredArgsConstructor
@Deprecated(forRemoval = false)
public class BackendInternalClient {

    private final RestTemplate restTemplate;
    private final EriseCloudProperties properties;
    private final ObjectMapper objectMapper;

    public List<KnowledgeChunk> retrieveKnowledge(Long userId, Long projectId, String keyword, int limit) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Key", properties.getInternal().getApiKey());
        JsonNode response = restTemplate.postForObject(
                properties.getBackend().getBaseUrl() + "/internal/v1/knowledge/retrieve",
                new HttpEntity<>(new KnowledgeRetrieveRequest(userId, projectId, keyword, limit), headers),
                JsonNode.class
        );
        if (response == null || response.path("data").isMissingNode()) {
            return List.of();
        }
        return objectMapper.convertValue(
                response.path("data"),
                objectMapper.getTypeFactory().constructCollectionType(List.class, KnowledgeChunk.class)
        );
    }

    public ProjectContext projectContext(Long projectId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Key", properties.getInternal().getApiKey());
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                properties.getBackend().getBaseUrl() + "/internal/v1/projects/" + projectId + "/context",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                JsonNode.class
        );
        if (response.getBody() == null || response.getBody().path("data").isMissingNode()) {
            return null;
        }
        return objectMapper.convertValue(response.getBody().path("data"), ProjectContext.class);
    }

    public record KnowledgeRetrieveRequest(Long userId, Long projectId, String keyword, int limit) {
    }

    public record KnowledgeChunk(
            String sourceType,
            Long sourceId,
            Long projectId,
            String title,
            String mimeType,
            String snippet,
            java.time.LocalDateTime updatedAt
    ) {
    }

    public record ProjectContext(
            Long id,
            Long ownerUserId,
            String name,
            String description,
            String projectStatus,
            Integer archived,
            long fileCount,
            long documentCount,
            java.time.LocalDateTime createdAt,
            java.time.LocalDateTime updatedAt
    ) {
    }
}
