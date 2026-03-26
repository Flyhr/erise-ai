package com.erise.ai.backend.modules;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erise.ai.backend.common.api.ApiResponse;
import com.erise.ai.backend.common.api.PageResponse;
import com.erise.ai.backend.common.entity.AuditableEntity;
import com.erise.ai.backend.common.exception.BizException;
import com.erise.ai.backend.common.exception.ErrorCodes;
import com.erise.ai.backend.common.util.SecurityUtils;
import com.erise.ai.backend.integration.storage.MinioStorageClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @GetMapping
    public ApiResponse<PageResponse<FileView>> page(@RequestParam Long projectId,
                                                    @RequestParam(defaultValue = "1") long pageNum,
                                                    @RequestParam(defaultValue = "10") long pageSize) {
        return ApiResponse.success(fileService.page(projectId, pageNum, pageSize));
    }

    @PostMapping("/init-upload")
    public ApiResponse<InitUploadResponse> initUpload(@Valid @RequestBody InitUploadRequest request) {
        return ApiResponse.success(fileService.initUpload(request));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileView> upload(@RequestParam Long fileId, @RequestParam MultipartFile file) {
        return ApiResponse.success(fileService.upload(fileId, file));
    }

    @PostMapping("/complete-upload")
    public ApiResponse<FileView> completeUpload(@Valid @RequestBody CompleteUploadRequest request) {
        return ApiResponse.success(fileService.completeUpload(request.fileId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<FileView> detail(@PathVariable Long id) {
        return ApiResponse.success(fileService.detail(id));
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<InputStreamResource> preview(@PathVariable Long id) {
        return fileService.stream(id, true);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id) {
        return fileService.stream(id, false);
    }

    @PostMapping("/{id}/tags")
    public ApiResponse<List<TagView>> bindTags(@PathVariable Long id, @Valid @RequestBody TagBindRequest request) {
        return ApiResponse.success(fileService.bindTags(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        fileService.delete(id);
        return ApiResponse.success("success", null);
    }
}

@Service
@RequiredArgsConstructor
class FileService {

    private static final List<String> INDEXABLE_TYPES = List.of("pdf", "md", "markdown", "txt");

    private final FileMapper fileMapper;
    private final FileParseTaskMapper fileParseTaskMapper;
    private final TagMapper tagMapper;
    private final FileTagRelMapper fileTagRelMapper;
    private final ProjectService projectService;
    private final MinioStorageClient storageClient;
    private final AuditLogService auditLogService;
    private final KnowledgeService knowledgeService;

    PageResponse<FileView> page(Long projectId, long pageNum, long pageSize) {
        projectService.requireAccessibleProject(projectId);
        Page<FileEntity> page = fileMapper.selectPage(Page.of(pageNum, pageSize),
                new LambdaQueryWrapper<FileEntity>()
                        .eq(FileEntity::getProjectId, projectId)
                        .orderByDesc(FileEntity::getUpdatedAt));
        return PageResponse.of(page.getRecords().stream().map(this::toView).toList(), pageNum, pageSize, page.getTotal());
    }

    InitUploadResponse initUpload(InitUploadRequest request) {
        var currentUser = SecurityUtils.currentUser();
        projectService.requireAccessibleProject(request.projectId());
        String extension = fileExtension(request.fileName());
        FileEntity entity = new FileEntity();
        entity.setOwnerUserId(currentUser.userId());
        entity.setProjectId(request.projectId());
        entity.setFileName(request.fileName());
        entity.setFileExt(extension);
        entity.setMimeType(request.mimeType());
        entity.setFileSize(request.fileSize());
        entity.setStorageProvider("MINIO");
        entity.setStorageBucket(storageClient.bucket());
        entity.setStorageKey("projects/%d/%s-%s".formatted(request.projectId(), UUID.randomUUID(), request.fileName()));
        entity.setUploadStatus("INIT");
        entity.setParseStatus("PENDING");
        entity.setPreviewStatus("PENDING");
        entity.setIndexStatus("PENDING");
        entity.setCreatedBy(currentUser.userId());
        entity.setUpdatedBy(currentUser.userId());
        fileMapper.insert(entity);
        return new InitUploadResponse(entity.getId(), entity.getStorageKey(), "/api/v1/files/upload?fileId=" + entity.getId());
    }

    FileView upload(Long fileId, MultipartFile file) {
        var currentUser = SecurityUtils.currentUser();
        FileEntity entity = requireAccessibleFile(fileId);
        validateUpload(file, entity);
        storageClient.putObject(entity.getStorageKey(), file);
        entity.setUploadStatus("UPLOADED");
        entity.setPreviewStatus("READY");
        entity.setUpdatedBy(currentUser.userId());
        fileMapper.updateById(entity);
        auditLogService.log(currentUser, "FILE_UPLOAD", "FILE", fileId, entity.getFileName());
        return toView(entity);
    }

    FileView completeUpload(Long fileId) {
        var currentUser = SecurityUtils.currentUser();
        FileEntity entity = requireAccessibleFile(fileId);
        if (!storageClient.objectExists(entity.getStorageKey())) {
            throw new BizException(ErrorCodes.FILE_ERROR, "Uploaded object not found");
        }
        entity.setUploadStatus("READY");
        entity.setParseStatus(indexable(entity) ? "PENDING" : "SKIPPED");
        entity.setIndexStatus(indexable(entity) ? "PENDING" : "SKIPPED");
        entity.setUpdatedBy(currentUser.userId());
        fileMapper.updateById(entity);
        if (indexable(entity)) {
            FileParseTaskEntity task = new FileParseTaskEntity();
            task.setFileId(fileId);
            task.setOwnerUserId(entity.getOwnerUserId());
            task.setProjectId(entity.getProjectId());
            task.setTaskStatus("PENDING");
            task.setRetryCount(0);
            task.setCreatedBy(currentUser.userId());
            task.setUpdatedBy(currentUser.userId());
            fileParseTaskMapper.insert(task);
        }
        return toView(entity);
    }

    FileView detail(Long fileId) {
        return toView(requireAccessibleFile(fileId));
    }

    ResponseEntity<InputStreamResource> stream(Long fileId, boolean inline) {
        var currentUser = SecurityUtils.currentUser();
        FileEntity entity = requireAccessibleFile(fileId);
        auditLogService.log(currentUser, inline ? "FILE_PREVIEW" : "FILE_DOWNLOAD", "FILE", fileId, null);
        InputStream stream = storageClient.getObject(entity.getStorageKey());
        ContentDisposition disposition = inline
                ? ContentDisposition.inline().filename(entity.getFileName(), StandardCharsets.UTF_8).build()
                : ContentDisposition.attachment().filename(entity.getFileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(entity.getMimeType() == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(entity.getMimeType()))
                .body(new InputStreamResource(stream));
    }

    List<TagView> bindTags(Long fileId, TagBindRequest request) {
        var currentUser = SecurityUtils.currentUser();
        requireAccessibleFile(fileId);
        fileTagRelMapper.delete(new LambdaQueryWrapper<FileTagRelEntity>().eq(FileTagRelEntity::getFileId, fileId));
        List<TagView> result = new ArrayList<>();
        for (String rawName : request.tags()) {
            String name = rawName.trim();
            if (name.isBlank()) {
                continue;
            }
            TagEntity tag = tagMapper.selectOne(new LambdaQueryWrapper<TagEntity>()
                    .eq(TagEntity::getOwnerUserId, currentUser.userId())
                    .eq(TagEntity::getName, name)
                    .last("limit 1"));
            if (tag == null) {
                tag = new TagEntity();
                tag.setOwnerUserId(currentUser.userId());
                tag.setName(name);
                tag.setCreatedBy(currentUser.userId());
                tag.setUpdatedBy(currentUser.userId());
                tagMapper.insert(tag);
            }
            FileTagRelEntity rel = new FileTagRelEntity();
            rel.setFileId(fileId);
            rel.setTagId(tag.getId());
            rel.setCreatedBy(currentUser.userId());
            rel.setUpdatedBy(currentUser.userId());
            fileTagRelMapper.insert(rel);
            result.add(new TagView(tag.getId(), tag.getName(), tag.getColor()));
        }
        auditLogService.log(currentUser, "FILE_TAG_BIND", "FILE", fileId, request.tags());
        return result;
    }

    void delete(Long fileId) {
        var currentUser = SecurityUtils.currentUser();
        requireAccessibleFile(fileId);
        fileMapper.deleteById(fileId);
        auditLogService.log(currentUser, "FILE_DELETE", "FILE", fileId, null);
    }

    FileEntity requireAccessibleFile(Long fileId) {
        var currentUser = SecurityUtils.currentUser();
        FileEntity entity = fileMapper.selectById(fileId);
        if (entity == null) {
            throw new BizException(ErrorCodes.NOT_FOUND, "File not found");
        }
        projectService.requireAccessibleProject(entity.getProjectId());
        if (!currentUser.isAdmin() && !currentUser.userId().equals(entity.getOwnerUserId())) {
            throw new BizException(ErrorCodes.FORBIDDEN, "No permission");
        }
        return entity;
    }

    FileView toView(FileEntity entity) {
        return new FileView(
                entity.getId(),
                entity.getProjectId(),
                entity.getFileName(),
                entity.getFileExt(),
                entity.getMimeType(),
                entity.getFileSize(),
                entity.getUploadStatus(),
                entity.getParseStatus(),
                entity.getIndexStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private boolean indexable(FileEntity entity) {
        return INDEXABLE_TYPES.contains(entity.getFileExt().toLowerCase(Locale.ROOT));
    }

    private void validateUpload(MultipartFile file, FileEntity entity) {
        if (file.isEmpty()) {
            throw new BizException(ErrorCodes.FILE_ERROR, "File is empty");
        }
        if (entity.getFileSize() != null && entity.getFileSize() > 0 && !entity.getFileSize().equals(file.getSize())) {
            entity.setFileSize(file.getSize());
        }
    }

    private String fileExtension(String name) {
        int index = name.lastIndexOf('.');
        return index > -1 ? name.substring(index + 1).toLowerCase(Locale.ROOT) : "bin";
    }

    void handleParseTask(FileParseTaskEntity task) {
        FileEntity file = fileMapper.selectById(task.getFileId());
        if (file == null) {
            task.setTaskStatus("FAILED");
            task.setLastError("File missing");
            fileParseTaskMapper.updateById(task);
            return;
        }
        try (InputStream stream = storageClient.getObject(file.getStorageKey())) {
            List<KnowledgeService.ChunkInput> chunks = extractChunks(file, stream);
            knowledgeService.replaceForSource(file.getOwnerUserId(), file.getProjectId(), "FILE", file.getId(), file.getFileName(), chunks);
            file.setParseStatus("SUCCESS");
            file.setIndexStatus("SUCCESS");
            file.setUpdatedBy(file.getOwnerUserId());
            fileMapper.updateById(file);
            task.setTaskStatus("SUCCESS");
            task.setUpdatedBy(file.getOwnerUserId());
            fileParseTaskMapper.updateById(task);
        } catch (Exception exception) {
            task.setRetryCount(task.getRetryCount() + 1);
            task.setLastError(exception.getMessage());
            task.setTaskStatus(task.getRetryCount() >= 3 ? "FAILED" : "PENDING");
            task.setUpdatedBy(file.getOwnerUserId());
            fileParseTaskMapper.updateById(task);
            file.setParseStatus("FAILED");
            file.setIndexStatus("FAILED");
            file.setUpdatedBy(file.getOwnerUserId());
            fileMapper.updateById(file);
        }
    }

    private List<KnowledgeService.ChunkInput> extractChunks(FileEntity file, InputStream stream) throws IOException {
        return switch (file.getFileExt()) {
            case "txt" -> knowledgeService.splitText(new String(stream.readAllBytes(), StandardCharsets.UTF_8), null);
            case "md", "markdown" -> knowledgeService.splitText(stripMarkdown(new String(stream.readAllBytes(), StandardCharsets.UTF_8)), null);
            case "pdf" -> extractPdf(stream);
            default -> List.of();
        };
    }

    private List<KnowledgeService.ChunkInput> extractPdf(InputStream stream) throws IOException {
        byte[] bytes = stream.readAllBytes();
        try (PDDocument document = Loader.loadPDF(bytes)) {
            List<KnowledgeService.ChunkInput> chunks = new ArrayList<>();
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document);
                chunks.addAll(knowledgeService.splitText(text, page));
            }
            return chunks;
        }
    }

    private String stripMarkdown(String markdown) {
        return markdown
                .replaceAll("```[\\s\\S]*?```", " ")
                .replaceAll("!\\[[^\\]]*]\\([^)]*\\)", " ")
                .replaceAll("\\[[^\\]]*]\\([^)]*\\)", " ")
                .replaceAll("[#>*`_\\-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}

@Component
@RequiredArgsConstructor
class FileParseWorker {

    private final FileParseTaskMapper fileParseTaskMapper;
    private final FileService fileService;

    @Scheduled(fixedDelay = 15000)
    public void poll() {
        List<FileParseTaskEntity> tasks = fileParseTaskMapper.selectList(new LambdaQueryWrapper<FileParseTaskEntity>()
                .eq(FileParseTaskEntity::getTaskStatus, "PENDING")
                .orderByAsc(FileParseTaskEntity::getCreatedAt)
                .last("limit 5"));
        tasks.forEach(fileService::handleParseTask);
    }
}

interface FileMapper extends BaseMapper<FileEntity> {
}

interface FileParseTaskMapper extends BaseMapper<FileParseTaskEntity> {
}

interface TagMapper extends BaseMapper<TagEntity> {
}

interface FileTagRelMapper extends BaseMapper<FileTagRelEntity> {
}

@Data
@TableName("ea_file")
class FileEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerUserId;
    private Long projectId;
    private String fileName;
    private String fileExt;
    private String mimeType;
    private Long fileSize;
    private String storageProvider;
    private String storageBucket;
    private String storageKey;
    private String checksumMd5;
    private String checksumSha256;
    private String uploadStatus;
    private String parseStatus;
    private String previewStatus;
    private String indexStatus;
}

@Data
@TableName("ea_file_parse_task")
class FileParseTaskEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long fileId;
    private Long ownerUserId;
    private Long projectId;
    private String taskStatus;
    private Integer retryCount;
    private String lastError;
}

@Data
@TableName("ea_tag")
class TagEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerUserId;
    private String name;
    private String color;
}

@Data
@TableName("ea_file_tag_rel")
class FileTagRelEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long fileId;
    private Long tagId;
}

record InitUploadRequest(
        @NotNull Long projectId,
        @NotBlank String fileName,
        @NotNull Long fileSize,
        @NotBlank String mimeType
) {
}

record InitUploadResponse(Long fileId, String storageKey, String uploadUrl) {
}

record CompleteUploadRequest(@NotNull Long fileId) {
}

record FileView(
        Long id,
        Long projectId,
        String fileName,
        String fileExt,
        String mimeType,
        Long fileSize,
        String uploadStatus,
        String parseStatus,
        String indexStatus,
        java.time.LocalDateTime createdAt,
        java.time.LocalDateTime updatedAt
) {
}

record TagBindRequest(@NotNull List<String> tags) {
}

record TagView(Long id, String name, String color) {
}
