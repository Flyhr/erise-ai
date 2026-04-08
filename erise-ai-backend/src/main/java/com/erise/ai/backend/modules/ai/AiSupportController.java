package com.erise.ai.backend.modules;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erise.ai.backend.common.api.ApiResponse;
import com.erise.ai.backend.common.entity.AuditableEntity;
import com.erise.ai.backend.common.exception.BizException;
import com.erise.ai.backend.common.exception.ErrorCodes;
import com.erise.ai.backend.common.util.SecurityUtils;
import com.erise.ai.backend.integration.storage.MinioStorageClient;
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
import java.util.Map;
import java.util.UUID;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

    private final AiTempFileMapper aiTempFileMapper;
    private final ProjectService projectService;
    private final MinioStorageClient storageClient;
    private final StoredTextExtractionSupport storedTextExtractionSupport;
    private final RagKnowledgeService ragKnowledgeService;

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
        entity.setCreatedBy(currentUser.userId());
        entity.setUpdatedBy(currentUser.userId());
        aiTempFileMapper.insert(entity);
        try {
            storageClient.putObject(entity.getStorageKey(), file);
        } catch (RuntimeException exception) {
            aiTempFileMapper.deleteById(entity.getId());
            throw exception;
        }
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
            if (!"INDEXED".equalsIgnoreCase(entity.getIndexStatus())) {
                throw new BizException(ErrorCodes.CONFLICT, "Temp file is not ready for retrieval: " + entity.getFileName(), HttpStatus.CONFLICT);
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

    void processPendingTempFiles() {
        List<AiTempFileEntity> pendingFiles = aiTempFileMapper.selectList(new LambdaQueryWrapper<AiTempFileEntity>()
                .eq(AiTempFileEntity::getParseStatus, "PENDING")
                .orderByAsc(AiTempFileEntity::getCreatedAt)
                .last("limit 5"));
        pendingFiles.forEach(this::processSingleTempFile);
    }

    private void processSingleTempFile(AiTempFileEntity entity) {
        AiTempFileEntity latest = aiTempFileMapper.selectById(entity.getId());
        if (latest == null || !"PENDING".equalsIgnoreCase(latest.getParseStatus())) {
            return;
        }
        latest.setParseStatus("PROCESSING");
        latest.setIndexStatus("PROCESSING");
        latest.setUpdatedBy(latest.getOwnerUserId());
        aiTempFileMapper.updateById(latest);
        try (InputStream stream = storageClient.getObject(latest.getStorageKey())) {
            String plainText = storedTextExtractionSupport.extractPlainText(latest.getFileExt(), stream).trim();
            if (plainText.isBlank()) {
                throw new BizException(ErrorCodes.FILE_ERROR, "No readable text content was extracted");
            }
            ragKnowledgeService.replaceTempSource(
                    latest.getOwnerUserId(),
                    latest.getProjectId(),
                    latest.getSessionId(),
                    latest.getId(),
                    latest.getFileName(),
                    ragKnowledgeService.splitText(plainText, null)
            );
            latest.setPlainText(plainText);
            latest.setParseStatus("INDEXED");
            latest.setIndexStatus("INDEXED");
            latest.setUpdatedBy(latest.getOwnerUserId());
            aiTempFileMapper.updateById(latest);
        } catch (Exception exception) {
            latest.setParseStatus("FAILED");
            latest.setIndexStatus("FAILED");
            latest.setUpdatedBy(latest.getOwnerUserId());
            aiTempFileMapper.updateById(latest);
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
        LocalDateTime createdAt
) {
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
