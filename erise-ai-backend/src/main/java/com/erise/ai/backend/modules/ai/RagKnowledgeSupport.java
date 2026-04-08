package com.erise.ai.backend.modules;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erise.ai.backend.common.entity.AuditableEntity;
import com.erise.ai.backend.integration.ai.CloudAiClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class RagKnowledgeService {

    private static final String SCOPE_KB = "KB";
    private static final String SCOPE_TEMP = "TEMP";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_READY = "READY";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_DELETED = "DELETED";
    private static final String STATUS_NEEDS_REPAIR = "NEEDS_REPAIR";
    private static final String TASK_TYPE_INDEX = "INDEX";
    private static final String TASK_TYPE_DELETE = "DELETE";
    private static final int TARGET_CHUNK_SIZE = 420;
    private static final int MAX_CHUNK_SIZE = 500;
    private static final int OVERLAP_SIZE = 60;

    private final RagSourceMapper ragSourceMapper;
    private final RagChunkMapper ragChunkMapper;
    private final RagTaskMapper ragTaskMapper;
    private final CloudAiClient cloudAiClient;
    private final ObjectMapper objectMapper;

    void replaceKbSource(Long ownerUserId,
                         Long projectId,
                         String sourceType,
                         Long sourceId,
                         String sourceTitle,
                         List<ChunkInput> chunks) {
        replaceSource(SCOPE_KB, ownerUserId, projectId, 0L, sourceType, sourceId, sourceTitle, chunks);
    }

    void replaceTempSource(Long ownerUserId,
                           Long projectId,
                           Long sessionId,
                           Long sourceId,
                           String sourceTitle,
                           List<ChunkInput> chunks) {
        replaceSource(SCOPE_TEMP, ownerUserId, projectId, sessionId, "TEMP_FILE", sourceId, sourceTitle, chunks);
    }

    void deleteKbSource(Long ownerUserId, Long projectId, String sourceType, Long sourceId) {
        deleteSource(SCOPE_KB, ownerUserId, projectId, 0L, sourceType, sourceId);
    }

    void deleteTempSource(Long ownerUserId, Long sessionId, Long sourceId) {
        deleteSource(SCOPE_TEMP, ownerUserId, null, sessionId, "TEMP_FILE", sourceId);
    }

    void deleteProjectSources(Long ownerUserId, Long projectId) {
        ragSourceMapper.selectList(new LambdaQueryWrapper<RagSourceEntity>()
                        .eq(RagSourceEntity::getOwnerUserId, ownerUserId)
                        .eq(RagSourceEntity::getProjectId, projectId))
                .forEach(source -> deleteSource(
                        source.getScopeType(),
                        ownerUserId,
                        projectId,
                        normalizeSessionId(source.getSessionId()),
                        source.getSourceType(),
                        source.getSourceId()
                ));
    }

    List<ChunkInput> splitText(String text, Integer pageNo) {
        List<ChunkInput> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        String normalized = text.replace("\r", "").trim();
        String[] rawParagraphs = normalized.split("\\n\\s*\\n+");
        List<String> paragraphs = new ArrayList<>();
        for (String rawParagraph : rawParagraphs) {
            String trimmed = rawParagraph.trim();
            if (!trimmed.isBlank()) {
                paragraphs.add(trimmed);
            }
        }
        if (paragraphs.isEmpty()) {
            return List.of(new ChunkInput(0, normalized, pageNo, null));
        }

        int chunkIndex = 0;
        String currentSection = null;
        StringBuilder buffer = new StringBuilder();
        for (String paragraph : paragraphs) {
            if (isLikelyHeading(paragraph)) {
                currentSection = paragraph;
            }
            if (buffer.length() == 0) {
                buffer.append(paragraph);
                continue;
            }
            if (buffer.length() + 2 + paragraph.length() <= TARGET_CHUNK_SIZE) {
                buffer.append("\n\n").append(paragraph);
                continue;
            }
            chunkIndex = flushBuffer(chunks, chunkIndex, buffer, pageNo, currentSection);
            if (buffer.length() > 0) {
                buffer.append("\n\n");
            }
            buffer.append(paragraph);
        }
        flushBuffer(chunks, chunkIndex, buffer, pageNo, currentSection);
        return chunks;
    }

    private void replaceSource(String scopeType,
                               Long ownerUserId,
                               Long projectId,
                               Long sessionId,
                               String sourceType,
                               Long sourceId,
                               String sourceTitle,
                               List<ChunkInput> chunks) {
        RagSourceEntity source = upsertSource(scopeType, ownerUserId, projectId, sessionId, sourceType, sourceId, sourceTitle);
        RagTaskEntity task = createTask(source, ownerUserId, projectId, sessionId, sourceType, sourceId, TASK_TYPE_INDEX);
        try {
            CloudAiClient.RagIndexUpsertResponse response = cloudAiClient.upsertRagIndex(
                    ownerUserId,
                    new CloudAiClient.RagIndexUpsertRequest(
                            ownerUserId,
                            scopeType,
                            projectId,
                            sessionId,
                            sourceType,
                            sourceId,
                            sourceTitle,
                            chunks == null ? List.of() : chunks.stream()
                                    .map(item -> new CloudAiClient.RagChunkRequest(
                                            item.chunkIndex(),
                                            item.chunkText(),
                                            item.pageNo(),
                                            item.sectionPath()
                                    ))
                                    .toList(),
                            LocalDateTime.now()
                    ),
                    requestId(scopeType, sourceType, sourceId)
            );

            ragChunkMapper.delete(new LambdaQueryWrapper<RagChunkEntity>().eq(RagChunkEntity::getRagSourceId, source.getId()));
            String collectionName = response == null || response.collectionName() == null
                    ? defaultCollection(scopeType)
                    : response.collectionName();
            for (ChunkInput chunk : chunks == null ? List.<ChunkInput>of() : chunks) {
                RagChunkEntity entity = new RagChunkEntity();
                entity.setRagSourceId(source.getId());
                entity.setOwnerUserId(ownerUserId);
                entity.setProjectId(projectId);
                entity.setSessionId(sessionId);
                entity.setSourceType(sourceType);
                entity.setSourceId(sourceId);
                entity.setChunkNum(chunk.chunkIndex());
                entity.setChunkText(chunk.chunkText());
                entity.setPageNo(chunk.pageNo());
                entity.setSectionPath(chunk.sectionPath());
                entity.setVectorCollection(collectionName);
                entity.setVectorPointId(pointId(scopeType, sourceType, sourceId, sessionId, chunk.chunkIndex()));
                entity.setEmbeddingModelCode(response == null ? null : response.embeddingModelCode());
                entity.setEmbeddingVersion(response == null ? null : response.embeddingVersion());
                entity.setEmbeddingDimension(response == null ? null : response.embeddingDimension());
                entity.setCreatedBy(ownerUserId);
                entity.setUpdatedBy(ownerUserId);
                ragChunkMapper.insert(entity);
            }

            source.setStatus(STATUS_READY);
            source.setChunkCount(chunks == null ? 0 : chunks.size());
            source.setLastError(null);
            source.setLastIndexedAt(LocalDateTime.now());
            source.setContentHash(contentHash(chunks));
            source.setEmbeddingModelCode(response == null ? null : response.embeddingModelCode());
            source.setEmbeddingVersion(response == null ? null : response.embeddingVersion());
            source.setEmbeddingDimension(response == null ? null : response.embeddingDimension());
            source.setUpdatedBy(ownerUserId);
            ragSourceMapper.updateById(source);

            task.setTaskStatus(STATUS_READY);
            task.setResultJson(toJson(response));
            task.setLastError(null);
            task.setUpdatedBy(ownerUserId);
            ragTaskMapper.updateById(task);
        } catch (Exception exception) {
            ragChunkMapper.delete(new LambdaQueryWrapper<RagChunkEntity>().eq(RagChunkEntity::getRagSourceId, source.getId()));
            source.setStatus(STATUS_FAILED);
            source.setChunkCount(0);
            source.setLastError(trimError(exception.getMessage()));
            source.setUpdatedBy(ownerUserId);
            ragSourceMapper.updateById(source);

            task.setTaskStatus(STATUS_FAILED);
            task.setLastError(trimError(exception.getMessage()));
            task.setUpdatedBy(ownerUserId);
            ragTaskMapper.updateById(task);
            throw exception;
        }
    }

    private void deleteSource(String scopeType,
                              Long ownerUserId,
                              Long projectId,
                              Long sessionId,
                              String sourceType,
                              Long sourceId) {
        RagSourceEntity source = findSource(scopeType, ownerUserId, sourceType, sourceId, sessionId);
        if (source == null) {
            return;
        }
        RagTaskEntity task = createTask(source, ownerUserId, projectId, sessionId, sourceType, sourceId, TASK_TYPE_DELETE);
        try {
            CloudAiClient.RagIndexDeleteResponse response = cloudAiClient.deleteRagIndex(
                    ownerUserId,
                    new CloudAiClient.RagIndexDeleteRequest(
                            ownerUserId,
                            scopeType,
                            projectId,
                            sessionId,
                            sourceType,
                            sourceId
                    ),
                    requestId(scopeType, sourceType, sourceId)
            );
            ragChunkMapper.delete(new LambdaQueryWrapper<RagChunkEntity>().eq(RagChunkEntity::getRagSourceId, source.getId()));
            source.setStatus(STATUS_DELETED);
            source.setChunkCount(0);
            source.setLastError(null);
            source.setUpdatedBy(ownerUserId);
            ragSourceMapper.updateById(source);

            task.setTaskStatus(STATUS_READY);
            task.setResultJson(toJson(response));
            task.setLastError(null);
            task.setUpdatedBy(ownerUserId);
            ragTaskMapper.updateById(task);
        } catch (Exception exception) {
            source.setStatus(STATUS_NEEDS_REPAIR);
            source.setLastError(trimError(exception.getMessage()));
            source.setUpdatedBy(ownerUserId);
            ragSourceMapper.updateById(source);

            task.setTaskStatus(STATUS_NEEDS_REPAIR);
            task.setLastError(trimError(exception.getMessage()));
            task.setUpdatedBy(ownerUserId);
            ragTaskMapper.updateById(task);
            throw exception;
        }
    }

    private RagSourceEntity upsertSource(String scopeType,
                                         Long ownerUserId,
                                         Long projectId,
                                         Long sessionId,
                                         String sourceType,
                                         Long sourceId,
                                         String sourceTitle) {
        Long normalizedSessionId = normalizeSessionId(sessionId);
        RagSourceEntity source = findSource(scopeType, ownerUserId, sourceType, sourceId, normalizedSessionId);
        if (source == null) {
            source = new RagSourceEntity();
            source.setOwnerUserId(ownerUserId);
            source.setScopeType(scopeType);
            source.setSourceType(sourceType);
            source.setSourceId(sourceId);
            source.setSessionId(normalizedSessionId);
            source.setCreatedBy(ownerUserId);
        }
        source.setProjectId(projectId);
        source.setSourceTitle(sourceTitle);
        source.setStatus(STATUS_PROCESSING);
        source.setLastError(null);
        source.setUpdatedBy(ownerUserId);
        if (source.getId() == null) {
            ragSourceMapper.insert(source);
        } else {
            ragSourceMapper.updateById(source);
        }
        return source;
    }

    private RagTaskEntity createTask(RagSourceEntity source,
                                     Long ownerUserId,
                                     Long projectId,
                                     Long sessionId,
                                     String sourceType,
                                     Long sourceId,
                                     String taskType) {
        RagTaskEntity task = new RagTaskEntity();
        task.setOwnerUserId(ownerUserId);
        task.setProjectId(projectId);
        task.setSessionId(normalizeSessionId(sessionId));
        task.setRagSourceId(source.getId());
        task.setTaskType(taskType);
        task.setTaskStatus(STATUS_PROCESSING);
        task.setScopeType(source.getScopeType());
        task.setSourceType(sourceType);
        task.setSourceId(sourceId);
        task.setRetryCount(0);
        task.setPayloadJson(toJson(source));
        task.setCreatedBy(ownerUserId);
        task.setUpdatedBy(ownerUserId);
        ragTaskMapper.insert(task);
        return task;
    }

    private RagSourceEntity findSource(String scopeType, Long ownerUserId, String sourceType, Long sourceId, Long sessionId) {
        LambdaQueryWrapper<RagSourceEntity> wrapper = new LambdaQueryWrapper<RagSourceEntity>()
                .eq(RagSourceEntity::getOwnerUserId, ownerUserId)
                .eq(RagSourceEntity::getScopeType, scopeType)
                .eq(RagSourceEntity::getSourceType, sourceType)
                .eq(RagSourceEntity::getSourceId, sourceId);
        wrapper.eq(RagSourceEntity::getSessionId, normalizeSessionId(sessionId));
        return ragSourceMapper.selectOne(wrapper.last("limit 1"));
    }

    private Long normalizeSessionId(Long sessionId) {
        return sessionId == null ? 0L : sessionId;
    }

    private int flushBuffer(List<ChunkInput> chunks,
                            int chunkIndex,
                            StringBuilder buffer,
                            Integer pageNo,
                            String sectionPath) {
        while (buffer.length() > MAX_CHUNK_SIZE) {
            int splitPosition = findSplitPosition(buffer.toString());
            String piece = buffer.substring(0, splitPosition).trim();
            chunks.add(new ChunkInput(chunkIndex++, piece, pageNo, sectionPath));
            String remainder = buffer.substring(Math.max(0, splitPosition - OVERLAP_SIZE)).trim();
            buffer.setLength(0);
            buffer.append(remainder);
        }
        String finalText = buffer.toString().trim();
        if (!finalText.isBlank()) {
            chunks.add(new ChunkInput(chunkIndex++, finalText, pageNo, sectionPath));
        }
        buffer.setLength(0);
        return chunkIndex;
    }

    private int findSplitPosition(String value) {
        int candidate = Math.min(MAX_CHUNK_SIZE, value.length());
        for (int index = candidate; index >= TARGET_CHUNK_SIZE; index--) {
            char current = value.charAt(index - 1);
            if (current == '\n' || current == ' ' || current == '。' || current == '；' || current == ';') {
                return index;
            }
        }
        return Math.min(TARGET_CHUNK_SIZE, value.length());
    }

    private boolean isLikelyHeading(String paragraph) {
        return paragraph.length() <= 60
                && !paragraph.contains("。")
                && !paragraph.contains(".")
                && !paragraph.contains("：")
                && !paragraph.contains(":");
    }

    private String contentHash(List<ChunkInput> chunks) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            String joined = chunks == null ? "" : chunks.stream()
                    .map(ChunkInput::chunkText)
                    .reduce("", (left, right) -> left + "\n" + right);
            byte[] encoded = digest.digest(joined.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : encoded) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception exception) {
            return null;
        }
    }

    private String pointId(String scopeType, String sourceType, Long sourceId, Long sessionId, Integer chunkIndex) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            String raw = "%s:%s:%s:%s:%s".formatted(scopeType, sourceType, sourceId, sessionId == null ? 0 : sessionId, chunkIndex);
            byte[] encoded = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : encoded) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception exception) {
            return Long.toHexString(System.nanoTime());
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private String trimError(String message) {
        if (message == null || message.isBlank()) {
            return "Unknown error";
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    private String requestId(String scopeType, String sourceType, Long sourceId) {
        return "rag-" + scopeType.toLowerCase() + "-" + sourceType.toLowerCase() + "-" + sourceId + "-" + System.currentTimeMillis();
    }

    private String defaultCollection(String scopeType) {
        return SCOPE_TEMP.equalsIgnoreCase(scopeType) ? "temp_chunks" : "kb_chunks";
    }

    record ChunkInput(int chunkIndex, String chunkText, Integer pageNo, String sectionPath) {
    }
}

interface RagSourceMapper extends BaseMapper<RagSourceEntity> {
}

interface RagChunkMapper extends BaseMapper<RagChunkEntity> {
}

interface RagTaskMapper extends BaseMapper<RagTaskEntity> {
}

@Data
@TableName("ea_rag_source")
class RagSourceEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerUserId;
    private Long projectId;
    private Long sessionId;
    private String scopeType;
    private String sourceType;
    private Long sourceId;
    private String sourceTitle;
    private String status;
    private String contentHash;
    private Integer chunkCount;
    private LocalDateTime lastIndexedAt;
    private String lastError;
    private String embeddingModelCode;
    private String embeddingVersion;
    private Integer embeddingDimension;
}

@Data
@TableName("ea_rag_chunk")
class RagChunkEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ragSourceId;
    private Long ownerUserId;
    private Long projectId;
    private Long sessionId;
    private String sourceType;
    private Long sourceId;
    private Integer chunkNum;
    private String chunkText;
    private Integer pageNo;
    private String sectionPath;
    private String vectorCollection;
    private String vectorPointId;
    private String embeddingModelCode;
    private String embeddingVersion;
    private Integer embeddingDimension;
}

@Data
@TableName("ea_rag_task")
class RagTaskEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerUserId;
    private Long projectId;
    private Long sessionId;
    private Long ragSourceId;
    private String taskType;
    private String taskStatus;
    private String scopeType;
    private String sourceType;
    private Long sourceId;
    private Integer retryCount;
    private String payloadJson;
    private String resultJson;
    private String lastError;
}
