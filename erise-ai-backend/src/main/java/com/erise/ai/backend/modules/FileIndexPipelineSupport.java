package com.erise.ai.backend.modules;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erise.ai.backend.common.entity.AuditableEntity;
import com.erise.ai.backend.common.exception.BizException;
import com.erise.ai.backend.common.exception.ErrorCodes;
import com.erise.ai.backend.modules.StoredTextExtractionSupport.StructuredExtractionResult;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class FileIndexPipelineService {

    private static final Logger log = LoggerFactory.getLogger(FileIndexPipelineService.class);
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String INDEX_TASK_TYPE = "INDEX";
    private static final String METADATA_TASK_TYPE = "FILE_INDEX_METADATA";
    private static final long STALE_PROCESSING_MINUTES = 10L;

    private final FileParseResultMapper fileParseResultMapper;
    private final FileEditContentMapper fileEditContentMapper;
    private final RagTaskMapper ragTaskMapper;
    private final TaskMapper taskMapper;
    private final RagKnowledgeService ragKnowledgeService;
    private final FileMapper fileMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    void stageParsedFile(FileEntity file, StructuredExtractionResult extraction) {
        if (file == null || extraction == null) {
            return;
        }
        upsertParseResult(file, extraction);
        upsertPlainTextSnapshot(file, extraction.plainText());
    }

    boolean hasStoredParseResult(Long fileId) {
        if (fileId == null) {
            return false;
        }
        Long count = fileParseResultMapper.selectCount(new LambdaQueryWrapper<FileParseResultEntity>()
                .eq(FileParseResultEntity::getFileId, fileId));
        return count != null && count > 0;
    }

    FileParseResultEntity latestParseResultForReuse(Long fileId) {
        return latestParseResult(fileId);
    }

    void cloneParseResult(FileEntity targetFile, FileParseResultEntity sourceResult, Long operatorUserId) {
        if (targetFile == null || sourceResult == null) {
            return;
        }
        FileParseResultEntity target = latestParseResult(targetFile.getId());
        if (target == null) {
            target = new FileParseResultEntity();
            target.setFileId(targetFile.getId());
            target.setOwnerUserId(targetFile.getOwnerUserId());
            target.setProjectId(targetFile.getProjectId());
            target.setFileName(targetFile.getFileName());
            target.setCreatedBy(operatorUserId == null ? targetFile.getOwnerUserId() : operatorUserId);
        }
        target.setChunkCount(sourceResult.getChunkCount());
        target.setChunkPayloadJson(sourceResult.getChunkPayloadJson());
        target.setPlainText(sourceResult.getPlainText());
        target.setUpdatedBy(operatorUserId == null ? targetFile.getOwnerUserId() : operatorUserId);
        if (target.getId() == null) {
            fileParseResultMapper.insert(target);
        } else {
            fileParseResultMapper.updateById(target);
        }

        upsertPlainTextSnapshot(targetFile, sourceResult.getPlainText());
    }

    void enqueueIndexTask(FileEntity file, Long operatorUserId) {
        if (file == null || file.getId() == null) {
            return;
        }
        RagTaskEntity latestTask = latestIndexTask(file.getId());
        if (latestTask != null && (STATUS_PENDING.equalsIgnoreCase(latestTask.getTaskStatus())
                || STATUS_PROCESSING.equalsIgnoreCase(latestTask.getTaskStatus()))) {
            return;
        }
        RagTaskEntity task = new RagTaskEntity();
        task.setOwnerUserId(file.getOwnerUserId());
        task.setProjectId(file.getProjectId());
        task.setSessionId(0L);
        task.setTaskType(INDEX_TASK_TYPE);
        task.setTaskStatus(STATUS_PENDING);
        task.setScopeType("KB");
        task.setSourceType("FILE");
        task.setSourceId(file.getId());
        task.setRetryCount(0);
        task.setPayloadJson(writeTaskPayload(new FileIndexTaskPayload(file.getId(), file.getProjectId(), file.getFileName(), file.getOwnerUserId())));
        task.setCreatedBy(operatorUserId == null ? file.getOwnerUserId() : operatorUserId);
        task.setUpdatedBy(operatorUserId == null ? file.getOwnerUserId() : operatorUserId);
        ragTaskMapper.insert(task);
    }

    void retryIndexFromStoredResult(FileEntity file, Long operatorUserId) {
        if (file == null) {
            return;
        }
        RagTaskEntity latestTask = latestIndexTask(file.getId());
        if (latestTask != null) {
            latestTask.setTaskStatus(STATUS_PENDING);
            latestTask.setRetryCount(0);
            latestTask.setLastError(null);
            latestTask.setUpdatedBy(operatorUserId == null ? file.getOwnerUserId() : operatorUserId);
            ragTaskMapper.updateById(latestTask);
            return;
        }
        enqueueIndexTask(file, operatorUserId);
    }

    void processIndexTask(RagTaskEntity task) {
        if (task == null || task.getId() == null) {
            return;
        }
        long startedAt = System.nanoTime();
        if (!claimIndexTask(task.getId())) {
            return;
        }
        RagTaskEntity latestTask = ragTaskMapper.selectById(task.getId());
        FileEntity file = fileMapper.selectById(task.getSourceId());
        if (file == null) {
            markTaskFailed(latestTask, "File missing", false, 0);
            return;
        }
        FileParseResultEntity parseResult = latestParseResult(file.getId());
        if (parseResult == null) {
            markTaskFailed(latestTask, "Parsed chunk staging result is missing", false, 0);
            markFileIndexFailed(file);
            return;
        }
        markFileIndexing(file);
        try {
            List<RagKnowledgeService.ChunkInput> chunks = readChunks(parseResult);
            if (chunks.isEmpty()) {
                throw new BizException(ErrorCodes.FILE_ERROR, "No parsed chunks available for indexing");
            }
            ragKnowledgeService.upsertKbSourceVectorOnly(
                    file.getOwnerUserId(),
                    file.getProjectId(),
                    "FILE",
                    file.getId(),
                    file.getFileName(),
                    chunks
            );
            markFileIndexed(file);
            enqueueMetadataTask(file, chunks.size());
            latestTask.setTaskStatus(STATUS_SUCCESS);
            latestTask.setLastError(null);
            latestTask.setResultJson(writeTaskPayload(new FileIndexTaskResult(file.getId(), chunks.size(), file.getUpdatedAt())));
            latestTask.setUpdatedBy(file.getOwnerUserId());
            ragTaskMapper.updateById(latestTask);
            log.info(
                    "file_index_task_complete fileId={} fileName={} chunkCount={} durationMs={}",
                    file.getId(),
                    file.getFileName(),
                    chunks.size(),
                    (System.nanoTime() - startedAt) / 1_000_000L
            );
        } catch (Exception exception) {
            String errorMessage = parseTaskErrorMessage(exception);
            int nextRetryCount = (latestTask.getRetryCount() == null ? 0 : latestTask.getRetryCount()) + 1;
            boolean retryable = isRetryableTaskError(errorMessage);
            boolean exhausted = nextRetryCount >= 3;
            if (retryable && !exhausted) {
                markFileIndexPending(file);
            } else {
                markFileIndexFailed(file);
            }
            markTaskFailed(latestTask, errorMessage, retryable && !exhausted, nextRetryCount);
            log.warn(
                    "file_index_task_failed fileId={} fileName={} retryable={} exhausted={} retryCount={} durationMs={} error={}",
                    file.getId(),
                    file.getFileName(),
                    retryable,
                    exhausted,
                    nextRetryCount,
                    (System.nanoTime() - startedAt) / 1_000_000L,
                    errorMessage
            );
        }
    }

    void processMetadataTask(TaskEntity task) {
        if (task == null || task.getId() == null) {
            return;
        }
        if (!claimMetadataTask(task.getId())) {
            return;
        }
        TaskEntity latestTask = taskMapper.selectById(task.getId());
        FileMetadataTaskPayload payload = readMetadataPayload(latestTask == null ? null : latestTask.getPayloadJson());
        if (payload == null || payload.fileId() == null) {
            markMetadataTaskFailed(latestTask, "Metadata payload is missing", false, 0);
            return;
        }
        FileEntity file = fileMapper.selectById(payload.fileId());
        if (file == null) {
            markMetadataTaskFailed(latestTask, "File missing", false, 0);
            return;
        }
        FileParseResultEntity parseResult = latestParseResult(file.getId());
        if (parseResult == null) {
            markMetadataTaskFailed(latestTask, "Parsed chunk staging result is missing", false, 0);
            return;
        }
        try {
            List<RagKnowledgeService.ChunkInput> chunks = readChunks(parseResult);
            ragKnowledgeService.syncKbSourceMetadata(
                    file.getOwnerUserId(),
                    file.getProjectId(),
                    "FILE",
                    file.getId(),
                    file.getFileName(),
                    chunks
            );
            latestTask.setTaskStatus(STATUS_SUCCESS);
            latestTask.setRetryCount(0);
            latestTask.setLastError(null);
            latestTask.setResultJson(writeTaskPayload(new FileMetadataTaskResult(file.getId(), chunks.size())));
            latestTask.setUpdatedBy(file.getOwnerUserId());
            taskMapper.updateById(latestTask);
        } catch (Exception exception) {
            String errorMessage = parseTaskErrorMessage(exception);
            int nextRetryCount = (latestTask.getRetryCount() == null ? 0 : latestTask.getRetryCount()) + 1;
            boolean retryable = isRetryableTaskError(errorMessage);
            boolean exhausted = nextRetryCount >= 3;
            markMetadataTaskFailed(latestTask, errorMessage, retryable && !exhausted, nextRetryCount);
        }
    }

    void recoverStaleProcessingTasks() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(STALE_PROCESSING_MINUTES);
        List<RagTaskEntity> staleIndexTasks = ragTaskMapper.selectList(new LambdaQueryWrapper<RagTaskEntity>()
                .eq(RagTaskEntity::getTaskType, INDEX_TASK_TYPE)
                .eq(RagTaskEntity::getSourceType, "FILE")
                .eq(RagTaskEntity::getTaskStatus, STATUS_PROCESSING)
                .lt(RagTaskEntity::getUpdatedAt, threshold)
                .orderByAsc(RagTaskEntity::getUpdatedAt)
                .last("limit 20"));
        for (RagTaskEntity task : staleIndexTasks) {
            task.setTaskStatus(STATUS_PENDING);
            task.setLastError("Previous index worker was interrupted before completion. Retrying automatically.");
            task.setUpdatedBy(task.getOwnerUserId());
            ragTaskMapper.updateById(task);
            fileMapper.update(
                    null,
                    new LambdaUpdateWrapper<FileEntity>()
                            .eq(FileEntity::getId, task.getSourceId())
                            .eq(FileEntity::getParseStatus, STATUS_SUCCESS)
                            .eq(FileEntity::getIndexStatus, STATUS_PROCESSING)
                            .set(FileEntity::getIndexStatus, STATUS_PENDING)
                            .set(FileEntity::getUpdatedBy, task.getOwnerUserId())
            );
        }

        List<TaskEntity> staleMetadataTasks = taskMapper.selectList(new LambdaQueryWrapper<TaskEntity>()
                .eq(TaskEntity::getTaskType, METADATA_TASK_TYPE)
                .eq(TaskEntity::getTaskStatus, STATUS_PROCESSING)
                .lt(TaskEntity::getUpdatedAt, threshold)
                .orderByAsc(TaskEntity::getUpdatedAt)
                .last("limit 20"));
        for (TaskEntity task : staleMetadataTasks) {
            task.setTaskStatus(STATUS_PENDING);
            task.setLastError("Previous metadata worker was interrupted before completion. Retrying automatically.");
            task.setUpdatedBy(task.getOwnerUserId());
            taskMapper.updateById(task);
        }
    }

    List<RagTaskEntity> loadPendingIndexTasks(int limit) {
        return ragTaskMapper.selectList(new LambdaQueryWrapper<RagTaskEntity>()
                .eq(RagTaskEntity::getTaskType, INDEX_TASK_TYPE)
                .eq(RagTaskEntity::getSourceType, "FILE")
                .eq(RagTaskEntity::getTaskStatus, STATUS_PENDING)
                .orderByAsc(RagTaskEntity::getCreatedAt)
                .last("limit " + Math.max(limit, 1)));
    }

    List<TaskEntity> loadPendingMetadataTasks(int limit) {
        List<Long> ids = jdbcTemplate.query("""
                        select id
                        from ea_task
                        where deleted = 0
                          and task_type = ?
                          and task_status = ?
                        order by created_at asc, id asc
                        limit ?
                        """,
                (rs, rowNum) -> rs.getLong("id"),
                METADATA_TASK_TYPE,
                STATUS_PENDING,
                Math.max(limit, 1)
        );
        return ids.stream()
                .map(taskMapper::selectById)
                .filter(item -> item != null)
                .toList();
    }

    void deleteParseResult(Long fileId) {
        if (fileId == null) {
            return;
        }
        fileParseResultMapper.delete(new LambdaQueryWrapper<FileParseResultEntity>()
                .eq(FileParseResultEntity::getFileId, fileId));
        taskMapper.delete(new LambdaQueryWrapper<TaskEntity>()
                .eq(TaskEntity::getTaskType, METADATA_TASK_TYPE)
                .apply("cast(json_unquote(json_extract(payload_json, '$.fileId')) as unsigned) = {0}", fileId));
    }

    private void enqueueMetadataTask(FileEntity file, int chunkCount) {
        if (file == null || file.getId() == null) {
            return;
        }
        TaskEntity latestTask = latestMetadataTask(file.getId());
        if (latestTask != null && (STATUS_PENDING.equalsIgnoreCase(latestTask.getTaskStatus())
                || STATUS_PROCESSING.equalsIgnoreCase(latestTask.getTaskStatus()))) {
            return;
        }
        TaskEntity task = new TaskEntity();
        task.setOwnerUserId(file.getOwnerUserId());
        task.setTaskType(METADATA_TASK_TYPE);
        task.setTaskStatus(STATUS_PENDING);
        task.setRetryCount(0);
        task.setPayloadJson(writeTaskPayload(new FileMetadataTaskPayload(
                file.getId(),
                file.getProjectId(),
                file.getFileName(),
                file.getOwnerUserId(),
                chunkCount
        )));
        task.setCreatedBy(file.getOwnerUserId());
        task.setUpdatedBy(file.getOwnerUserId());
        taskMapper.insert(task);
    }

    private void upsertParseResult(FileEntity file, StructuredExtractionResult extraction) {
        FileParseResultEntity entity = latestParseResult(file.getId());
        if (entity == null) {
            entity = new FileParseResultEntity();
            entity.setFileId(file.getId());
            entity.setOwnerUserId(file.getOwnerUserId());
            entity.setProjectId(file.getProjectId());
            entity.setFileName(file.getFileName());
            entity.setCreatedBy(file.getOwnerUserId());
        }
        entity.setChunkCount(extraction.chunks() == null ? 0 : extraction.chunks().size());
        entity.setPlainText(extraction.plainText());
        entity.setChunkPayloadJson(writeChunks(extraction.chunks()));
        entity.setUpdatedBy(file.getOwnerUserId());
        if (entity.getId() == null) {
            fileParseResultMapper.insert(entity);
        } else {
            fileParseResultMapper.updateById(entity);
        }
    }

    private void upsertPlainTextSnapshot(FileEntity file, String plainText) {
        FileEditContentEntity entity = fileEditContentMapper.selectOne(new LambdaQueryWrapper<FileEditContentEntity>()
                .eq(FileEditContentEntity::getFileId, file.getId())
                .last("limit 1"));
        if (entity == null) {
            entity = new FileEditContentEntity();
            entity.setFileId(file.getId());
            entity.setEditorType("OFFICE_HTML");
            entity.setCreatedBy(file.getOwnerUserId());
        }
        entity.setPlainText(plainText);
        entity.setUpdatedBy(file.getOwnerUserId());
        if (entity.getId() == null) {
            fileEditContentMapper.insert(entity);
        } else {
            fileEditContentMapper.updateById(entity);
        }
    }

    private FileParseResultEntity latestParseResult(Long fileId) {
        if (fileId == null) {
            return null;
        }
        return fileParseResultMapper.selectOne(new LambdaQueryWrapper<FileParseResultEntity>()
                .eq(FileParseResultEntity::getFileId, fileId)
                .orderByDesc(FileParseResultEntity::getUpdatedAt)
                .orderByDesc(FileParseResultEntity::getId)
                .last("limit 1"));
    }

    private RagTaskEntity latestIndexTask(Long fileId) {
        if (fileId == null) {
            return null;
        }
        return ragTaskMapper.selectOne(new LambdaQueryWrapper<RagTaskEntity>()
                .eq(RagTaskEntity::getTaskType, INDEX_TASK_TYPE)
                .eq(RagTaskEntity::getSourceType, "FILE")
                .eq(RagTaskEntity::getSourceId, fileId)
                .orderByDesc(RagTaskEntity::getUpdatedAt)
                .orderByDesc(RagTaskEntity::getId)
                .last("limit 1"));
    }

    private TaskEntity latestMetadataTask(Long fileId) {
        List<Long> ids = jdbcTemplate.query("""
                        select id
                        from ea_task
                        where deleted = 0
                          and task_type = ?
                          and cast(json_unquote(json_extract(payload_json, '$.fileId')) as unsigned) = ?
                        order by id desc
                        limit 1
                        """,
                (rs, rowNum) -> rs.getLong("id"),
                METADATA_TASK_TYPE,
                fileId
        );
        return ids.isEmpty() ? null : taskMapper.selectById(ids.get(0));
    }

    private boolean claimIndexTask(Long taskId) {
        return ragTaskMapper.update(
                null,
                new LambdaUpdateWrapper<RagTaskEntity>()
                        .eq(RagTaskEntity::getId, taskId)
                        .eq(RagTaskEntity::getTaskStatus, STATUS_PENDING)
                        .set(RagTaskEntity::getTaskStatus, STATUS_PROCESSING)
        ) > 0;
    }

    private boolean claimMetadataTask(Long taskId) {
        return taskMapper.update(
                null,
                new LambdaUpdateWrapper<TaskEntity>()
                        .eq(TaskEntity::getId, taskId)
                        .eq(TaskEntity::getTaskStatus, STATUS_PENDING)
                        .set(TaskEntity::getTaskStatus, STATUS_PROCESSING)
        ) > 0;
    }

    private void markTaskFailed(RagTaskEntity task, String errorMessage, boolean pendingRetry, int retryCount) {
        if (task == null) {
            return;
        }
        task.setTaskStatus(pendingRetry ? STATUS_PENDING : STATUS_FAILED);
        task.setRetryCount(retryCount);
        task.setLastError(trimError(errorMessage));
        task.setUpdatedBy(task.getOwnerUserId());
        ragTaskMapper.updateById(task);
    }

    private void markMetadataTaskFailed(TaskEntity task, String errorMessage, boolean pendingRetry, int retryCount) {
        if (task == null) {
            return;
        }
        task.setTaskStatus(pendingRetry ? STATUS_PENDING : STATUS_FAILED);
        task.setRetryCount(retryCount);
        task.setLastError(trimError(errorMessage));
        task.setUpdatedBy(task.getOwnerUserId());
        taskMapper.updateById(task);
    }

    private void markFileIndexPending(FileEntity file) {
        file.setParseStatus(STATUS_SUCCESS);
        file.setIndexStatus(STATUS_PENDING);
        file.setUpdatedBy(file.getOwnerUserId());
        fileMapper.updateById(file);
    }

    private void markFileIndexing(FileEntity file) {
        file.setParseStatus(STATUS_SUCCESS);
        file.setIndexStatus(STATUS_PROCESSING);
        file.setUpdatedBy(file.getOwnerUserId());
        fileMapper.updateById(file);
    }

    private void markFileIndexed(FileEntity file) {
        file.setParseStatus(STATUS_SUCCESS);
        file.setIndexStatus(STATUS_SUCCESS);
        file.setUpdatedBy(file.getOwnerUserId());
        fileMapper.updateById(file);
    }

    private void markFileIndexFailed(FileEntity file) {
        file.setParseStatus(STATUS_SUCCESS);
        file.setIndexStatus(STATUS_FAILED);
        file.setUpdatedBy(file.getOwnerUserId());
        fileMapper.updateById(file);
    }

    private List<RagKnowledgeService.ChunkInput> readChunks(FileParseResultEntity result) {
        if (result == null || result.getChunkPayloadJson() == null || result.getChunkPayloadJson().isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(result.getChunkPayloadJson(), new TypeReference<List<RagKnowledgeService.ChunkInput>>() {
            });
        } catch (JsonProcessingException exception) {
            throw new BizException(ErrorCodes.FILE_ERROR, "Failed to read parsed chunk staging payload");
        }
    }

    private FileMetadataTaskPayload readMetadataPayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(payloadJson, FileMetadataTaskPayload.class);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private String writeChunks(List<RagKnowledgeService.ChunkInput> chunks) {
        try {
            return objectMapper.writeValueAsString(chunks == null ? List.of() : chunks);
        } catch (JsonProcessingException exception) {
            throw new BizException(ErrorCodes.FILE_ERROR, "Failed to serialize parsed chunk staging payload");
        }
    }

    private String writeTaskPayload(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private String parseTaskErrorMessage(Exception exception) {
        if (exception == null || exception.getMessage() == null || exception.getMessage().isBlank()) {
            return "File pipeline task failed";
        }
        return exception.getMessage();
    }

    private boolean isRetryableTaskError(String message) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return normalized.contains("service unavailable")
                || normalized.contains("temporarily unavailable")
                || normalized.contains("dependency unavailable")
                || normalized.contains("timeout")
                || normalized.contains("timed out")
                || normalized.contains("connection refused")
                || normalized.contains("connection reset")
                || normalized.contains("connection aborted")
                || normalized.contains("network is unreachable")
                || normalized.contains("bad gateway")
                || normalized.contains("gateway timeout")
                || normalized.contains("rate limit")
                || normalized.contains("too many requests")
                || normalized.contains("qdrant");
    }

    private String trimError(String message) {
        if (message == null || message.isBlank()) {
            return "Unknown error";
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}

@Component
@RequiredArgsConstructor
class FileIndexWorker {

    private final FileIndexPipelineService fileIndexPipelineService;
    @Qualifier("fileIndexTaskExecutor")
    private final java.util.concurrent.Executor fileIndexTaskExecutor;

    @Scheduled(fixedDelay = 3000L)
    public void poll() {
        fileIndexPipelineService.recoverStaleProcessingTasks();
        fileIndexPipelineService.loadPendingIndexTasks(20).stream()
                .limit(5)
                .forEach(task -> fileIndexTaskExecutor.execute(() -> fileIndexPipelineService.processIndexTask(task)));
        fileIndexPipelineService.loadPendingMetadataTasks(20).stream()
                .limit(5)
                .forEach(task -> fileIndexTaskExecutor.execute(() -> fileIndexPipelineService.processMetadataTask(task)));
    }
}

interface FileParseResultMapper extends BaseMapper<FileParseResultEntity> {
}

@Data
@TableName("ea_file_parse_result")
class FileParseResultEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long fileId;
    private Long ownerUserId;
    private Long projectId;
    private String fileName;
    private Integer chunkCount;
    private String chunkPayloadJson;
    private String plainText;
}

record FileIndexTaskPayload(Long fileId, Long projectId, String fileName, Long ownerUserId) {
}

record FileIndexTaskResult(Long fileId, Integer chunkCount, LocalDateTime completedAt) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record FileMetadataTaskPayload(Long fileId, Long projectId, String fileName, Long ownerUserId, Integer chunkCount) {
}

record FileMetadataTaskResult(Long fileId, Integer chunkCount) {
}
