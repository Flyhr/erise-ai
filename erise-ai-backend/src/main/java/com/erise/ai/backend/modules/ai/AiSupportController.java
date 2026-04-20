package com.erise.ai.backend.modules;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erise.ai.backend.common.api.ApiResponse;
import com.erise.ai.backend.common.entity.AuditableEntity;
import com.erise.ai.backend.common.exception.BizException;
import com.erise.ai.backend.common.exception.ErrorCodes;
import com.erise.ai.backend.common.util.SecurityUtils;
import com.erise.ai.backend.integration.storage.MinioStorageClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiSupportController {

    private final AiTempFileService aiTempFileService;
    private final AiRetrievalSettingService aiRetrievalSettingService;

    @PostMapping(value = "/temp-files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AiTempFileView> uploadTempFile(@RequestPart MultipartFile file,
                                                      @RequestParam Long sessionId,
                                                      @RequestParam(required = false) Long projectId) {
        return ApiResponse.success(aiTempFileService.upload(file, sessionId, projectId));
    }

    @GetMapping("/temp-files")
    public ApiResponse<List<AiTempFileView>> tempFiles(@RequestParam Long sessionId) {
        return ApiResponse.success(aiTempFileService.list(sessionId));
    }

    @DeleteMapping("/temp-files/{id}")
    public ApiResponse<Void> deleteTempFile(@PathVariable Long id) {
        aiTempFileService.deleteCurrentUserTempFile(id);
        return ApiResponse.success("success", null);
    }

    @PostMapping("/temp-files/{id}/retry")
    public ApiResponse<AiTempFileView> retryTempFile(@PathVariable Long id) {
        return ApiResponse.success(aiTempFileService.retryCurrentUserTempFile(id));
    }

    @GetMapping("/settings/retrieval")
    public ApiResponse<AiRetrievalSettingView> retrievalSettings() {
        return ApiResponse.success(aiRetrievalSettingService.getCurrentUserSettings());
    }

    @PutMapping("/settings/retrieval")
    public ApiResponse<AiRetrievalSettingUpdateView> updateRetrievalSettings(@Valid @org.springframework.web.bind.annotation.RequestBody AiRetrievalSettingUpdateRequest request) {
        return ApiResponse.success(aiRetrievalSettingService.updateCurrentUserSettings(request));
    }
}

@Service
@RequiredArgsConstructor
class AiTempFileService {

    private static final String TEMP_FILE_PARSE_TASK_TYPE = "TEMP_FILE_PARSE";

    private final AiTempFileMapper aiTempFileMapper;
    private final TaskMapper taskMapper;
    private final ProjectService projectService;
    private final MinioStorageClient storageClient;
    private final StoredTextExtractionSupport storedTextExtractionSupport;
    private final RagKnowledgeService ragKnowledgeService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    AiTempFileView upload(MultipartFile file, Long sessionId, Long projectId) {
        if (sessionId == null || sessionId <= 0) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "Session id is required");
        }
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCodes.FILE_ERROR, "File is empty");
        }

        var currentUser = SecurityUtils.currentUser();
        if (projectId != null) {
            projectService.requireAccessibleProject(projectId);
        }

        String fileName = resolveFileName(file);
        AiTempFileEntity entity = new AiTempFileEntity();
        entity.setSessionId(sessionId);
        entity.setOwnerUserId(currentUser.userId());
        entity.setProjectId(projectId);
        entity.setFileName(fileName);
        entity.setFileExt(fileExtension(fileName));
        entity.setMimeType(file.getContentType() == null || file.getContentType().isBlank()
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : file.getContentType());
        entity.setFileSize(file.getSize());
        entity.setStorageBucket(storageClient.bucket());
        entity.setStorageKey("temp/raw/%d/%d/%s-%s".formatted(
                currentUser.userId(),
                sessionId,
                UUID.randomUUID(),
                fileName
        ));
        entity.setPlainText(null);
        entity.setParseStatus("PENDING");
        entity.setIndexStatus("PENDING");
        entity.setLastError(null);
        entity.setRetryCount(0);
        entity.setCreatedBy(currentUser.userId());
        entity.setUpdatedBy(currentUser.userId());
        aiTempFileMapper.insert(entity);
        try {
            storageClient.putObject(entity.getStorageKey(), file);
        } catch (RuntimeException exception) {
            aiTempFileMapper.deleteById(entity.getId());
            throw exception;
        }
        createTempParseTask(entity, currentUser.userId());
        return toView(entity);
    }

    List<AiTempFileView> list(Long sessionId) {
        var currentUser = SecurityUtils.currentUser();
        return aiTempFileMapper.selectList(new LambdaQueryWrapper<AiTempFileEntity>()
                        .eq(AiTempFileEntity::getOwnerUserId, currentUser.userId())
                        .eq(AiTempFileEntity::getSessionId, sessionId)
                        .orderByDesc(AiTempFileEntity::getCreatedAt))
                .stream()
                .map(this::toView)
                .toList();
    }

    AiTempFileView retryCurrentUserTempFile(Long id) {
        var currentUser = SecurityUtils.currentUser();
        AiTempFileEntity entity = requireOwnedTempFile(currentUser.userId(), id);
        return retryTempFile(entity, currentUser.userId());
    }

    AiTempFileView retryByAdmin(Long id) {
        var currentUser = SecurityUtils.currentUser();
        AiTempFileEntity entity = requireExistingTempFile(id);
        return retryTempFile(entity, currentUser.userId());
    }

    private AiTempFileView retryTempFile(AiTempFileEntity entity, Long operatorUserId) {
        if (!isFailedStatus(entity.getParseStatus()) && !isFailedStatus(entity.getIndexStatus())) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "Only failed temp files can be retried");
        }
        markTempFileParsePending(entity, operatorUserId);
        entity.setLastError(null);
        entity.setRetryCount(0);
        aiTempFileMapper.updateById(entity);
        createTempParseTask(entity, operatorUserId);
        return toView(entity);
    }

    InternalAiTempFileContextView internalContext(Long id) {
        AiTempFileEntity entity = requireExistingTempFile(id);
        return toInternalContext(entity);
    }

    List<ResolvedTempFileAttachment> resolveUsableTempFiles(Long sessionId, List<Long> tempFileIds) {
        if (tempFileIds == null || tempFileIds.isEmpty()) {
            return List.of();
        }
        if (sessionId == null || sessionId <= 0) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "sessionId is required when tempFileIds are provided");
        }
        var currentUser = SecurityUtils.currentUser();
        List<AiTempFileEntity> records = aiTempFileMapper.selectList(new LambdaQueryWrapper<AiTempFileEntity>()
                .eq(AiTempFileEntity::getOwnerUserId, currentUser.userId())
                .eq(AiTempFileEntity::getSessionId, sessionId)
                .in(AiTempFileEntity::getId, tempFileIds));
        Map<Long, AiTempFileEntity> lookup = new LinkedHashMap<>();
        records.forEach(item -> lookup.put(item.getId(), item));
        return tempFileIds.stream().map(id -> {
            AiTempFileEntity entity = lookup.get(id);
            if (entity == null) {
                throw new BizException(ErrorCodes.NOT_FOUND, "Temp file not found: " + id, HttpStatus.NOT_FOUND);
            }
            return new ResolvedTempFileAttachment(entity.getId(), entity.getProjectId(), entity.getFileName());
        }).toList();
    }

    void deleteCurrentUserTempFile(Long id) {
        var currentUser = SecurityUtils.currentUser();
        AiTempFileEntity entity = requireOwnedTempFile(currentUser.userId(), id);
        deleteStoredTempFile(entity);
    }

    void deleteSessionTempFiles(Long ownerUserId, Long sessionId) {
        aiTempFileMapper.selectList(new LambdaQueryWrapper<AiTempFileEntity>()
                        .eq(AiTempFileEntity::getOwnerUserId, ownerUserId)
                        .eq(AiTempFileEntity::getSessionId, sessionId))
                .forEach(this::deleteStoredTempFile);
    }

    long countOwnedSessionTempFiles(Long ownerUserId, Long sessionId) {
        if (ownerUserId == null || sessionId == null) {
            return 0L;
        }
        Long count = aiTempFileMapper.selectCount(new LambdaQueryWrapper<AiTempFileEntity>()
                .eq(AiTempFileEntity::getOwnerUserId, ownerUserId)
                .eq(AiTempFileEntity::getSessionId, sessionId));
        return count == null ? 0L : count;
    }

    void processPendingTempFiles() {
        List<AiTempFileEntity> pendingFiles = aiTempFileMapper.selectList(new LambdaQueryWrapper<AiTempFileEntity>()
                .and(query -> query
                        .eq(AiTempFileEntity::getParseStatus, "PENDING")
                        .or()
                        .eq(AiTempFileEntity::getIndexStatus, "PENDING"))
                .orderByAsc(AiTempFileEntity::getCreatedAt)
                .last("limit 5"));
        pendingFiles.forEach(this::processSingleTempFile);
    }

    private void processSingleTempFile(AiTempFileEntity entity) {
        AiTempFileEntity latest = aiTempFileMapper.selectById(entity.getId());
        if (latest == null || !isTempFilePending(latest)) {
            return;
        }
        boolean parseCompleted = "SUCCESS".equalsIgnoreCase(latest.getParseStatus());
        if (!(parseCompleted ? claimTempIndexing(latest.getId()) : claimTempParsing(latest.getId()))) {
            return;
        }
        latest = aiTempFileMapper.selectById(entity.getId());
        if (latest == null) {
            return;
        }
        TaskEntity task = ensureTempParseTask(latest);
        markTempTaskProcessing(task, latest.getOwnerUserId());
        latest.setLastError(null);
        if (parseCompleted) {
            markTempFileIndexing(latest, latest.getOwnerUserId());
        } else {
            markTempFileParsing(latest, latest.getOwnerUserId());
        }
        aiTempFileMapper.updateById(latest);
        try (InputStream stream = storageClient.getObject(latest.getStorageKey())) {
            StoredTextExtractionSupport.StructuredExtractionResult extraction = storedTextExtractionSupport.extractStructuredContent(
                    latest.getOwnerUserId(),
                    latest.getFileName(),
                    latest.getFileExt(),
                    stream
            );
            String plainText = extraction.plainText() == null ? "" : extraction.plainText().trim();
            if (plainText.isBlank() || extraction.chunks().isEmpty()) {
                throw new BizException(ErrorCodes.FILE_ERROR, "No readable text content was extracted");
            }
            parseCompleted = true;
            markTempFileParsed(latest, latest.getOwnerUserId());
            aiTempFileMapper.updateById(latest);
            markTempFileIndexing(latest, latest.getOwnerUserId());
            aiTempFileMapper.updateById(latest);
            ragKnowledgeService.replaceTempSource(
                    latest.getOwnerUserId(),
                    latest.getProjectId(),
                    latest.getSessionId(),
                    latest.getId(),
                    latest.getFileName(),
                    extraction.chunks()
            );
            latest.setPlainText(plainText);
            latest.setParseStatus("SUCCESS");
            latest.setIndexStatus("SUCCESS");
            latest.setLastError(null);
            latest.setRetryCount(0);
            latest.setUpdatedBy(latest.getOwnerUserId());
            aiTempFileMapper.updateById(latest);
            markTempTaskSuccess(task, latest);
        } catch (Exception exception) {
            String errorMessage = parseTempErrorMessage(exception);
            int nextRetryCount = (latest.getRetryCount() == null ? 0 : latest.getRetryCount()) + 1;
            boolean retryable = isRetryableTempParseError(errorMessage);
            boolean exhausted = nextRetryCount >= 3;
            latest.setRetryCount(nextRetryCount);
            latest.setLastError(errorMessage);
            if (retryable && !exhausted) {
                if (parseCompleted) {
                    markTempFileParsed(latest, latest.getOwnerUserId());
                } else {
                    markTempFileParsePending(latest, latest.getOwnerUserId());
                }
            } else {
                latest.setParseStatus("FAILED");
                latest.setIndexStatus("FAILED");
                latest.setUpdatedBy(latest.getOwnerUserId());
            }
            aiTempFileMapper.updateById(latest);
            markTempTaskFailure(task, latest.getOwnerUserId(), nextRetryCount, errorMessage, retryable && !exhausted);
        }
    }

    private String parseTempErrorMessage(Exception exception) {
        if (exception == null || exception.getMessage() == null || exception.getMessage().isBlank()) {
            return "Temp file parsing failed";
        }
        return exception.getMessage();
    }

    private boolean isRetryableTempParseError(String message) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return normalized.contains("service unavailable")
                || normalized.contains("temporarily unavailable")
                || normalized.contains("dependency unavailable")
                || normalized.contains("dependency is unavailable")
                || normalized.contains("ocr service unavailable")
                || normalized.contains("ocr engine is unavailable")
                || normalized.contains("ocr dependency is unavailable")
                || (normalized.contains("ocr") && normalized.contains("unavailable"))
                || normalized.contains("timeout")
                || normalized.contains("timed out")
                || normalized.contains("connection refused")
                || normalized.contains("connection reset")
                || normalized.contains("connection aborted")
                || normalized.contains("i/o error")
                || normalized.contains("network is unreachable");
    }

    private String currentParseErrorMessage(AiTempFileEntity entity) {
        boolean failed = "FAILED".equalsIgnoreCase(entity.getParseStatus()) || "FAILED".equalsIgnoreCase(entity.getIndexStatus());
        if (!failed || entity.getLastError() == null || entity.getLastError().isBlank()) {
            return null;
        }
        return entity.getLastError();
    }

    private boolean claimTempParsing(Long id) {
        if (id == null) {
            return false;
        }
        return aiTempFileMapper.update(
                null,
                new LambdaUpdateWrapper<AiTempFileEntity>()
                        .eq(AiTempFileEntity::getId, id)
                        .eq(AiTempFileEntity::getParseStatus, "PENDING")
                        .set(AiTempFileEntity::getParseStatus, "PROCESSING")
                        .set(AiTempFileEntity::getIndexStatus, "PENDING")
        ) > 0;
    }

    private boolean claimTempIndexing(Long id) {
        if (id == null) {
            return false;
        }
        return aiTempFileMapper.update(
                null,
                new LambdaUpdateWrapper<AiTempFileEntity>()
                        .eq(AiTempFileEntity::getId, id)
                        .eq(AiTempFileEntity::getParseStatus, "SUCCESS")
                        .eq(AiTempFileEntity::getIndexStatus, "PENDING")
                        .set(AiTempFileEntity::getIndexStatus, "PROCESSING")
        ) > 0;
    }

    private boolean isTempFilePending(AiTempFileEntity entity) {
        return "PENDING".equalsIgnoreCase(entity.getParseStatus())
                || "PENDING".equalsIgnoreCase(entity.getIndexStatus());
    }

    private void markTempFileParsePending(AiTempFileEntity entity, Long operatorUserId) {
        entity.setParseStatus("PENDING");
        entity.setIndexStatus("PENDING");
        entity.setUpdatedBy(operatorUserId);
    }

    private void markTempFileParsing(AiTempFileEntity entity, Long operatorUserId) {
        entity.setParseStatus("PROCESSING");
        entity.setIndexStatus("PENDING");
        entity.setUpdatedBy(operatorUserId);
    }

    private void markTempFileParsed(AiTempFileEntity entity, Long operatorUserId) {
        entity.setParseStatus("SUCCESS");
        entity.setIndexStatus("PENDING");
        entity.setUpdatedBy(operatorUserId);
    }

    private void markTempFileIndexing(AiTempFileEntity entity, Long operatorUserId) {
        entity.setParseStatus("SUCCESS");
        entity.setIndexStatus("PROCESSING");
        entity.setUpdatedBy(operatorUserId);
    }

    private boolean isFailedStatus(String status) {
        return "FAILED".equalsIgnoreCase(status) || "DELETED".equalsIgnoreCase(status);
    }

    private TaskEntity ensureTempParseTask(AiTempFileEntity entity) {
        TaskEntity task = latestTempParseTask(entity.getId());
        if (task != null) {
            return task;
        }
        Long taskId = createTempParseTask(entity, entity.getOwnerUserId());
        return taskMapper.selectById(taskId);
    }

    private Long createTempParseTask(AiTempFileEntity entity, Long operatorUserId) {
        TaskEntity task = new TaskEntity();
        task.setOwnerUserId(entity.getOwnerUserId());
        task.setTaskType(TEMP_FILE_PARSE_TASK_TYPE);
        task.setTaskStatus("PENDING");
        task.setRetryCount(entity.getRetryCount() == null ? 0 : entity.getRetryCount());
        task.setPayloadJson(writeTaskPayload(new TempFileParseTaskPayload(
                entity.getId(),
                entity.getSessionId(),
                entity.getProjectId(),
                entity.getFileName(),
                entity.getOwnerUserId()
        )));
        task.setCreatedBy(operatorUserId);
        task.setUpdatedBy(operatorUserId);
        taskMapper.insert(task);
        return task.getId();
    }

    private TaskEntity latestTempParseTask(Long tempFileId) {
        List<Long> ids = jdbcTemplate.query("""
                        select id
                        from ea_task
                        where deleted = 0
                          and task_type = ?
                          and cast(json_unquote(json_extract(payload_json, '$.tempFileId')) as unsigned) = ?
                        order by id desc
                        limit 1
                        """,
                (rs, rowNum) -> rs.getLong("id"),
                TEMP_FILE_PARSE_TASK_TYPE,
                tempFileId
        );
        return ids.isEmpty() ? null : taskMapper.selectById(ids.get(0));
    }

    private void markTempTaskProcessing(TaskEntity task, Long operatorUserId) {
        if (task == null) {
            return;
        }
        task.setTaskStatus("PROCESSING");
        task.setLastError(null);
        task.setUpdatedBy(operatorUserId);
        taskMapper.updateById(task);
    }

    private void markTempTaskSuccess(TaskEntity task, AiTempFileEntity entity) {
        if (task == null) {
            return;
        }
        task.setTaskStatus("SUCCESS");
        task.setRetryCount(entity.getRetryCount() == null ? 0 : entity.getRetryCount());
        task.setLastError(null);
        task.setResultJson(writeTaskPayload(Map.of(
                "tempFileId", entity.getId(),
                "parseStatus", entity.getParseStatus(),
                "indexStatus", entity.getIndexStatus()
        )));
        task.setUpdatedBy(entity.getOwnerUserId());
        taskMapper.updateById(task);
    }

    private void markTempTaskFailure(TaskEntity task,
                                     Long operatorUserId,
                                     int retryCount,
                                     String errorMessage,
                                     boolean pendingRetry) {
        if (task == null) {
            return;
        }
        task.setTaskStatus(pendingRetry ? "PENDING" : "FAILED");
        task.setRetryCount(retryCount);
        task.setLastError(errorMessage);
        task.setUpdatedBy(operatorUserId);
        taskMapper.updateById(task);
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

    private void deleteStoredTempFile(AiTempFileEntity entity) {
        try {
            ragKnowledgeService.deleteTempSource(entity.getOwnerUserId(), entity.getSessionId(), entity.getId());
        } catch (Exception ignored) {
        }
        storageClient.removeObject(entity.getStorageKey());
        aiTempFileMapper.deleteById(entity.getId());
    }

    private AiTempFileEntity requireOwnedTempFile(Long ownerUserId, Long id) {
        AiTempFileEntity entity = requireExistingTempFile(id);
        if (!ownerUserId.equals(entity.getOwnerUserId())) {
            throw new BizException(ErrorCodes.FORBIDDEN, "No permission", HttpStatus.FORBIDDEN);
        }
        return entity;
    }

    private AiTempFileEntity requireExistingTempFile(Long id) {
        AiTempFileEntity entity = aiTempFileMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCodes.NOT_FOUND, "Temp file not found", HttpStatus.NOT_FOUND);
        }
        return entity;
    }

    private String resolveFileName(MultipartFile file) {
        String candidate = file.getOriginalFilename();
        return (candidate == null || candidate.isBlank()) ? "temp-file" : candidate;
    }

    private String fileExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index > -1 ? fileName.substring(index + 1).toLowerCase() : "bin";
    }

    private AiTempFileView toView(AiTempFileEntity entity) {
        return new AiTempFileView(
                entity.getId(),
                entity.getSessionId(),
                entity.getProjectId(),
                entity.getFileName(),
                entity.getMimeType(),
                entity.getFileSize(),
                entity.getParseStatus(),
                entity.getIndexStatus(),
                currentParseErrorMessage(entity),
                entity.getCreatedAt()
        );
    }

    private InternalAiTempFileContextView toInternalContext(AiTempFileEntity entity) {
        return new InternalAiTempFileContextView(
                entity.getId(),
                entity.getSessionId(),
                entity.getProjectId(),
                entity.getFileName(),
                entity.getFileExt(),
                entity.getMimeType(),
                entity.getPlainText(),
                entity.getParseStatus(),
                entity.getIndexStatus(),
                currentParseErrorMessage(entity),
                entity.getUpdatedAt()
        );
    }
}

@Service
@RequiredArgsConstructor
class AiRetrievalSettingService {

    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.75D;
    private static final int DEFAULT_TOP_K = 5;

    private final AiUserSettingMapper aiUserSettingMapper;

    AiRetrievalSettingView getCurrentUserSettings() {
        var currentUser = SecurityUtils.currentUser();
        AiUserSettingEntity entity = aiUserSettingMapper.selectOne(new LambdaQueryWrapper<AiUserSettingEntity>()
                .eq(AiUserSettingEntity::getUserId, currentUser.userId())
                .last("limit 1"));
        return entity == null ? defaultView() : toView(entity);
    }

    AiRetrievalSettingUpdateView updateCurrentUserSettings(AiRetrievalSettingUpdateRequest request) {
        validate(request);
        var currentUser = SecurityUtils.currentUser();
        AiUserSettingEntity entity = aiUserSettingMapper.selectOne(new LambdaQueryWrapper<AiUserSettingEntity>()
                .eq(AiUserSettingEntity::getUserId, currentUser.userId())
                .last("limit 1"));
        if (entity == null) {
            entity = new AiUserSettingEntity();
            entity.setUserId(currentUser.userId());
            entity.setCreatedBy(currentUser.userId());
        }
        entity.setGeneralWebSearchEnabled(Boolean.TRUE.equals(request.webSearchEnabledDefault()) ? 1 : 0);
        entity.setKnowledgeSimilarityThreshold(request.similarityThreshold());
        entity.setKnowledgeTopK(request.topK());
        entity.setUpdatedBy(currentUser.userId());
        if (entity.getId() == null) {
            aiUserSettingMapper.insert(entity);
        } else {
            aiUserSettingMapper.updateById(entity);
        }
        return new AiRetrievalSettingUpdateView(true);
    }

    RetrievalSettings resolveEffectiveSettings(Boolean webSearchEnabled, Double similarityThreshold, Integer topK) {
        AiRetrievalSettingView current = getCurrentUserSettings();
        return new RetrievalSettings(
                webSearchEnabled != null ? webSearchEnabled : current.webSearchEnabledDefault(),
                similarityThreshold != null ? similarityThreshold : current.similarityThreshold(),
                topK != null ? topK : current.topK()
        );
    }

    private void validate(AiRetrievalSettingUpdateRequest request) {
        if (request.similarityThreshold() == null || request.similarityThreshold() < 0 || request.similarityThreshold() > 1) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "similarityThreshold must be between 0 and 1");
        }
        if (request.topK() == null || request.topK() < 1 || request.topK() > 10) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "topK must be between 1 and 10");
        }
    }

    private AiRetrievalSettingView defaultView() {
        return new AiRetrievalSettingView(DEFAULT_SIMILARITY_THRESHOLD, DEFAULT_TOP_K, false);
    }

    private AiRetrievalSettingView toView(AiUserSettingEntity entity) {
        return new AiRetrievalSettingView(
                entity.getKnowledgeSimilarityThreshold() == null ? DEFAULT_SIMILARITY_THRESHOLD : entity.getKnowledgeSimilarityThreshold(),
                entity.getKnowledgeTopK() == null ? DEFAULT_TOP_K : entity.getKnowledgeTopK(),
                entity.getGeneralWebSearchEnabled() != null && entity.getGeneralWebSearchEnabled() == 1
        );
    }
}

@Component
@RequiredArgsConstructor
class AiTempFileWorker {

    private final AiTempFileService aiTempFileService;

    @Scheduled(fixedDelay = 10000)
    public void poll() {
        aiTempFileService.processPendingTempFiles();
    }
}

interface AiTempFileMapper extends BaseMapper<AiTempFileEntity> {
}

interface AiUserSettingMapper extends BaseMapper<AiUserSettingEntity> {
}

@Data
@TableName("ea_ai_temp_file")
class AiTempFileEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private Long ownerUserId;
    private Long projectId;
    private String fileName;
    private String fileExt;
    private String mimeType;
    private Long fileSize;
    private String storageBucket;
    private String storageKey;
    private String plainText;
    private String parseStatus;
    private String indexStatus;
    private String lastError;
    private Integer retryCount;
}

@Data
@TableName("ea_ai_user_setting")
class AiUserSettingEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Integer generalWebSearchEnabled;
    private Double knowledgeSimilarityThreshold;
    private Integer knowledgeTopK;
}

record AiTempFileView(
        Long id,
        Long sessionId,
        Long projectId,
        String fileName,
        String mimeType,
        Long sizeBytes,
        String parseStatus,
        String indexStatus,
        String parseErrorMessage,
        LocalDateTime createdAt
) {
}

record TempFileParseTaskPayload(Long tempFileId,
                                Long sessionId,
                                Long projectId,
                                String fileName,
                                Long ownerUserId) {
}

record InternalAiTempFileContextView(
        Long id,
        Long sessionId,
        Long projectId,
        String fileName,
        String fileExt,
        String mimeType,
        String plainText,
        String parseStatus,
        String indexStatus,
        String parseErrorMessage,
        LocalDateTime updatedAt
) {
}

record ResolvedTempFileAttachment(Long id, Long projectId, String fileName) {
}

record AiRetrievalSettingView(Double similarityThreshold, Integer topK, Boolean webSearchEnabledDefault) {
}

record AiRetrievalSettingUpdateView(Boolean updated) {
}

record RetrievalSettings(Boolean webSearchEnabled, Double similarityThreshold, Integer topK) {
}

record AiRetrievalSettingUpdateRequest(
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double similarityThreshold,
        @NotNull @Min(1) @Max(10) Integer topK,
        @NotNull Boolean webSearchEnabledDefault
) {
}
