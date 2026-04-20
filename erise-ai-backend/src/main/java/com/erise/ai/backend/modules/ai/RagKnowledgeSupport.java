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
import org.apache.ibatis.annotations.Param;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class RagKnowledgeService {

    private static final String SCOPE_KB = "KB";
    private static final String SCOPE_TEMP = "TEMP";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_READY = "READY";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_DELETED = "DELETED";
    private static final String STATUS_NEEDS_REPAIR = "NEEDS_REPAIR";
    private static final String TASK_TYPE_INDEX = "INDEX";
    private static final String TASK_TYPE_DELETE = "DELETE";
    private static final int MAX_CHUNKS_PER_SOURCE = 5000;
    private static final int RAG_CHUNK_INSERT_BATCH_SIZE = 500;

    private final RagSourceMapper ragSourceMapper;
    private final RagChunkMapper ragChunkMapper;
    private final RagTaskMapper ragTaskMapper;
    private final CloudAiClient cloudAiClient;
    private final ObjectMapper objectMapper;
    private final SparseKnowledgeSupport sparseKnowledgeSupport;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(noRollbackFor = Exception.class)
    void replaceKbSource(Long ownerUserId,
                         Long projectId,
                         String sourceType,
                         Long sourceId,
                         String sourceTitle,
                         List<ChunkInput> chunks) {
        replaceSource(SCOPE_KB, ownerUserId, projectId, 0L, sourceType, sourceId, sourceTitle, chunks);
    }

    @Transactional(noRollbackFor = Exception.class)
    void replaceTempSource(Long ownerUserId,
                           Long projectId,
                           Long sessionId,
                           Long sourceId,
                           String sourceTitle,
                           List<ChunkInput> chunks) {
        replaceSource(SCOPE_TEMP, ownerUserId, projectId, sessionId, "TEMP_FILE", sourceId, sourceTitle, chunks);
    }

    @Transactional(noRollbackFor = Exception.class)
    void deleteKbSource(Long ownerUserId, Long projectId, String sourceType, Long sourceId) {
        deleteSource(SCOPE_KB, ownerUserId, projectId, 0L, sourceType, sourceId);
    }

    @Transactional(noRollbackFor = Exception.class)
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

    KnowledgeSyncStatusView kbSyncStatus(Long ownerUserId, String sourceType, Long sourceId) {
        RagSourceEntity source = findSource(SCOPE_KB, ownerUserId, sourceType, sourceId, 0L);
        return new KnowledgeSyncStatusView(
                "SKIPPED",
                mapIndexStatus(source == null ? null : source.getStatus()),
                source == null ? null : source.getLastError()
        );
    }

    @Transactional(noRollbackFor = Exception.class)
    VectorSyncResult upsertKbSourceVectorOnly(Long ownerUserId,
                                              Long projectId,
                                              String sourceType,
                                              Long sourceId,
                                              String sourceTitle,
                                              List<ChunkInput> chunks) {
        return upsertSourceVectorOnly(SCOPE_KB, ownerUserId, projectId, 0L, sourceType, sourceId, sourceTitle, chunks);
    }

    @Transactional(noRollbackFor = Exception.class)
    void syncKbSourceMetadata(Long ownerUserId,
                              Long projectId,
                              String sourceType,
                              Long sourceId,
                              String sourceTitle,
                              List<ChunkInput> chunks) {
        syncSourceMetadata(SCOPE_KB, ownerUserId, projectId, 0L, sourceType, sourceId, sourceTitle, chunks);
    }

    @Transactional(noRollbackFor = Exception.class)
    void updateKbSourceTitle(Long ownerUserId,
                             Long projectId,
                             String sourceType,
                             Long sourceId,
                             String sourceTitle,
                             Long actorUserId) {
        RagSourceEntity source = findSource(SCOPE_KB, ownerUserId, sourceType, sourceId, 0L);
        if (source == null) {
            return;
        }
        source.setProjectId(projectId);
        source.setSourceTitle(sourceTitle);
        source.setUpdatedBy(actorUserId == null ? ownerUserId : actorUserId);
        ragSourceMapper.updateById(source);
        List<RagChunkEntity> chunks = ragChunkMapper.selectList(new LambdaQueryWrapper<RagChunkEntity>()
                .eq(RagChunkEntity::getRagSourceId, source.getId())
                .orderByAsc(RagChunkEntity::getChunkNum));
        sparseKnowledgeSupport.rebuildSourceIndex(source, chunks, actorUserId == null ? ownerUserId : actorUserId);
    }

    private List<ChunkInput> normalizeChunks(List<ChunkInput> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        List<ChunkInput> normalized = new ArrayList<>();
        int nextChunkIndex = 0;
        for (ChunkInput chunk : chunks) {
            if (chunk == null || chunk.chunkText() == null || chunk.chunkText().isBlank()) {
                continue;
            }
            if (normalized.size() >= MAX_CHUNKS_PER_SOURCE) {
                break;
            }
            normalized.add(new ChunkInput(nextChunkIndex++, chunk.chunkText().trim(), chunk.pageNo(), chunk.sectionPath()));
        }
        return normalized;
    }

    private void replaceSource(String scopeType,
                               Long ownerUserId,
                               Long projectId,
                               Long sessionId,
                               String sourceType,
                               Long sourceId,
                               String sourceTitle,
                               List<ChunkInput> chunks) {
        Long normalizedSessionId = normalizeSessionId(sessionId);
        List<ChunkInput> normalizedChunks = normalizeChunks(chunks);
        RagSourceEntity existingSource = findSource(scopeType, ownerUserId, sourceType, sourceId, normalizedSessionId);
        Integer previousChunkCount = existingSource == null ? 0 : existingSource.getChunkCount();
        boolean keepExistingReadyDuringReindex = existingSource != null
                && STATUS_READY.equalsIgnoreCase(existingSource.getStatus())
                && existingSource.getChunkCount() != null
                && existingSource.getChunkCount() > 0;
        RagSourceEntity source = upsertSource(
                scopeType,
                ownerUserId,
                projectId,
                normalizedSessionId,
                sourceType,
                sourceId,
                sourceTitle,
                keepExistingReadyDuringReindex
        );
        RagTaskEntity task = createTask(source, ownerUserId, projectId, sessionId, sourceType, sourceId, TASK_TYPE_INDEX);
        try {
            CloudAiClient.RagIndexUpsertResponse response = cloudAiClient.upsertRagIndex(
                    ownerUserId,
                    new CloudAiClient.RagIndexUpsertRequest(
                            ownerUserId,
                            scopeType,
                            projectId,
                            normalizedSessionId,
                            sourceType,
                            sourceId,
                            sourceTitle,
                            normalizedChunks.stream()
                                    .map(item -> new CloudAiClient.RagChunkRequest(
                                            item.chunkIndex(),
                                            item.chunkText(),
                                            item.pageNo(),
                                            item.sectionPath()
                                    ))
                                    .toList(),
                            previousChunkCount == null ? 0 : previousChunkCount,
                            LocalDateTime.now()
                    ),
                    requestId(scopeType, sourceType, sourceId)
            );

            hardDeleteChunks(source.getId());
            String collectionName = response == null || response.collectionName() == null
                    ? defaultCollection(scopeType)
                    : response.collectionName();
            List<RagChunkEntity> insertedChunks = new ArrayList<>();
            for (ChunkInput chunk : normalizedChunks) {
                RagChunkEntity entity = new RagChunkEntity();
                entity.setRagSourceId(source.getId());
                entity.setOwnerUserId(ownerUserId);
                entity.setProjectId(projectId);
                entity.setSessionId(normalizedSessionId);
                entity.setSourceType(sourceType);
                entity.setSourceId(sourceId);
                entity.setChunkNum(chunk.chunkIndex());
                entity.setChunkText(chunk.chunkText());
                entity.setPageNo(chunk.pageNo());
                entity.setSectionPath(chunk.sectionPath());
                entity.setVectorCollection(collectionName);
                entity.setVectorPointId(pointId(scopeType, sourceType, sourceId, normalizedSessionId, chunk.chunkIndex()));
                entity.setEmbeddingModelCode(response == null ? null : response.embeddingModelCode());
                entity.setEmbeddingVersion(response == null ? null : response.embeddingVersion());
                entity.setEmbeddingDimension(response == null ? null : response.embeddingDimension());
                entity.setCreatedBy(ownerUserId);
                entity.setUpdatedBy(ownerUserId);
                insertedChunks.add(entity);
            }
            batchInsertChunks(insertedChunks);
            sparseKnowledgeSupport.rebuildSourceIndex(source, insertedChunks, ownerUserId);

            source.setStatus(STATUS_READY);
            source.setChunkCount(normalizedChunks.size());
            source.setLastError(null);
            source.setLastIndexedAt(LocalDateTime.now());
            source.setContentHash(contentHash(normalizedChunks));
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
            hardDeleteChunks(source.getId());
            source.setStatus(keepExistingReadyDuringReindex ? STATUS_READY : STATUS_FAILED);
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

    private VectorSyncResult upsertSourceVectorOnly(String scopeType,
                                                    Long ownerUserId,
                                                    Long projectId,
                                                    Long sessionId,
                                                    String sourceType,
                                                    Long sourceId,
                                                    String sourceTitle,
                                                    List<ChunkInput> chunks) {
        Long normalizedSessionId = normalizeSessionId(sessionId);
        List<ChunkInput> normalizedChunks = normalizeChunks(chunks);
        RagSourceEntity existingSource = findSource(scopeType, ownerUserId, sourceType, sourceId, normalizedSessionId);
        Integer previousChunkCount = existingSource == null ? 0 : existingSource.getChunkCount();
        boolean keepExistingReadyDuringReindex = existingSource != null
                && STATUS_READY.equalsIgnoreCase(existingSource.getStatus())
                && existingSource.getChunkCount() != null
                && existingSource.getChunkCount() > 0;
        RagSourceEntity source = upsertSource(
                scopeType,
                ownerUserId,
                projectId,
                normalizedSessionId,
                sourceType,
                sourceId,
                sourceTitle,
                keepExistingReadyDuringReindex
        );
        try {
            CloudAiClient.RagIndexUpsertResponse response = cloudAiClient.upsertRagIndex(
                    ownerUserId,
                    new CloudAiClient.RagIndexUpsertRequest(
                            ownerUserId,
                            scopeType,
                            projectId,
                            normalizedSessionId,
                            sourceType,
                            sourceId,
                            sourceTitle,
                            normalizedChunks.stream()
                                    .map(item -> new CloudAiClient.RagChunkRequest(
                                            item.chunkIndex(),
                                            item.chunkText(),
                                            item.pageNo(),
                                            item.sectionPath()
                                    ))
                                    .toList(),
                            previousChunkCount == null ? 0 : previousChunkCount,
                            LocalDateTime.now()
                    ),
                    requestId(scopeType, sourceType, sourceId)
            );
            source.setStatus(STATUS_READY);
            source.setChunkCount(normalizedChunks.size());
            source.setLastError(null);
            source.setLastIndexedAt(LocalDateTime.now());
            source.setContentHash(contentHash(normalizedChunks));
            source.setEmbeddingModelCode(response == null ? null : response.embeddingModelCode());
            source.setEmbeddingVersion(response == null ? null : response.embeddingVersion());
            source.setEmbeddingDimension(response == null ? null : response.embeddingDimension());
            source.setUpdatedBy(ownerUserId);
            ragSourceMapper.updateById(source);
            return new VectorSyncResult(
                    source.getId(),
                    normalizedChunks.size(),
                    response == null || response.collectionName() == null ? defaultCollection(scopeType) : response.collectionName(),
                    response == null ? null : response.embeddingModelCode(),
                    response == null ? null : response.embeddingVersion(),
                    response == null ? null : response.embeddingDimension(),
                    previousChunkCount == null ? 0 : previousChunkCount
            );
        } catch (Exception exception) {
            source.setStatus(keepExistingReadyDuringReindex ? STATUS_READY : STATUS_FAILED);
            source.setLastError(trimError(exception.getMessage()));
            source.setUpdatedBy(ownerUserId);
            ragSourceMapper.updateById(source);
            throw exception;
        }
    }

    private void syncSourceMetadata(String scopeType,
                                    Long ownerUserId,
                                    Long projectId,
                                    Long sessionId,
                                    String sourceType,
                                    Long sourceId,
                                    String sourceTitle,
                                    List<ChunkInput> chunks) {
        Long normalizedSessionId = normalizeSessionId(sessionId);
        List<ChunkInput> normalizedChunks = normalizeChunks(chunks);
        RagSourceEntity source = findSource(scopeType, ownerUserId, sourceType, sourceId, normalizedSessionId);
        if (source == null) {
            return;
        }
        source.setProjectId(projectId);
        source.setSourceTitle(sourceTitle);
        source.setUpdatedBy(ownerUserId);
        ragSourceMapper.updateById(source);

        hardDeleteChunks(source.getId());
        List<RagChunkEntity> insertedChunks = new ArrayList<>();
        for (ChunkInput chunk : normalizedChunks) {
            RagChunkEntity entity = new RagChunkEntity();
            entity.setRagSourceId(source.getId());
            entity.setOwnerUserId(ownerUserId);
            entity.setProjectId(projectId);
            entity.setSessionId(normalizedSessionId);
            entity.setSourceType(sourceType);
            entity.setSourceId(sourceId);
            entity.setChunkNum(chunk.chunkIndex());
            entity.setChunkText(chunk.chunkText());
            entity.setPageNo(chunk.pageNo());
            entity.setSectionPath(chunk.sectionPath());
            entity.setVectorCollection(defaultCollection(scopeType));
            entity.setVectorPointId(pointId(scopeType, sourceType, sourceId, normalizedSessionId, chunk.chunkIndex()));
            entity.setEmbeddingModelCode(source.getEmbeddingModelCode());
            entity.setEmbeddingVersion(source.getEmbeddingVersion());
            entity.setEmbeddingDimension(source.getEmbeddingDimension());
            entity.setCreatedBy(ownerUserId);
            entity.setUpdatedBy(ownerUserId);
            insertedChunks.add(entity);
        }
        batchInsertChunks(insertedChunks);
        sparseKnowledgeSupport.rebuildSourceIndex(source, insertedChunks, ownerUserId);
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
            hardDeleteChunks(source.getId());
            sparseKnowledgeSupport.deleteSourceIndex(source.getId());
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
                                         String sourceTitle,
                                         boolean keepExistingReadyDuringReindex) {
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
        if (!keepExistingReadyDuringReindex || source.getId() == null) {
            source.setStatus(STATUS_PROCESSING);
        }
        source.setLastError(null);
        source.setUpdatedBy(ownerUserId);
        if (source.getId() == null) {
            ragSourceMapper.insert(source);
        } else {
            ragSourceMapper.updateById(source);
        }
        return source;
    }

    private void hardDeleteChunks(Long ragSourceId) {
        if (ragSourceId == null) {
            return;
        }
        jdbcTemplate.update("delete from ea_rag_chunk where rag_source_id = ?", ragSourceId);
    }

    private void batchInsertChunks(List<RagChunkEntity> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        for (int start = 0; start < chunks.size(); start += RAG_CHUNK_INSERT_BATCH_SIZE) {
            List<RagChunkEntity> batch = chunks.subList(start, Math.min(start + RAG_CHUNK_INSERT_BATCH_SIZE, chunks.size()));
            ragChunkMapper.batchInsert(batch);
        }
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

    private String mapIndexStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase();
        return switch (normalized) {
            case STATUS_READY -> STATUS_READY;
            case STATUS_PROCESSING -> STATUS_PROCESSING;
            case STATUS_FAILED, STATUS_NEEDS_REPAIR -> STATUS_FAILED;
            case STATUS_DELETED -> STATUS_DELETED;
            default -> "PENDING";
        };
    }

    record ChunkInput(int chunkIndex, String chunkText, Integer pageNo, String sectionPath) {
    }

    record VectorSyncResult(
            Long ragSourceId,
            Integer chunkCount,
            String collectionName,
            String embeddingModelCode,
            String embeddingVersion,
            Integer embeddingDimension,
            Integer previousChunkCount
    ) {
    }

    record KnowledgeSyncStatusView(String parseStatus, String indexStatus, String errorMessage) {
    }
}

interface RagSourceMapper extends BaseMapper<RagSourceEntity> {
}

interface RagChunkMapper extends BaseMapper<RagChunkEntity> {

    int batchInsert(@Param("items") List<RagChunkEntity> items);
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
