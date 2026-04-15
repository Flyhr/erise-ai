package com.erise.ai.backend.modules;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erise.ai.backend.common.api.ApiResponse;
import com.erise.ai.backend.common.api.PageResponse;
import com.erise.ai.backend.common.entity.AuditableEntity;
import com.erise.ai.backend.common.exception.BizException;
import com.erise.ai.backend.common.exception.ErrorCodes;
import com.erise.ai.backend.common.util.SecurityUtils;
import com.erise.ai.backend.common.util.TextContentUtils;
import com.erise.ai.backend.integration.storage.MinioStorageClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.BodyElementType;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
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
    public ApiResponse<PageResponse<FileView>> page(@RequestParam(required = false) Long projectId,
                                                     @RequestParam(required = false) String q,
                                                     @RequestParam(defaultValue = "1") long pageNum,
                                                     @RequestParam(defaultValue = "10") long pageSize) {
        return ApiResponse.success(fileService.page(projectId, q, pageNum, pageSize));
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

    @PostMapping("/{id}/retry-parse")
    public ApiResponse<FileView> retryParse(@PathVariable Long id) {
        return ApiResponse.success(fileService.retryParse(id));
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

    private static final List<String> INDEXABLE_TYPES = List.of("pdf", "md", "markdown", "txt", "doc", "docx");
    private static final Parser MARKDOWN_PARSER = Parser.builder()
            .extensions(List.of(TablesExtension.create()))
            .build();
    private static final HtmlRenderer MARKDOWN_RENDERER = HtmlRenderer.builder()
            .extensions(List.of(TablesExtension.create()))
            .escapeHtml(true)
            .softbreak("<br />\n")
            .build();

    private final FileMapper fileMapper;
    private final FileParseTaskMapper fileParseTaskMapper;
    private final TagMapper tagMapper;
    private final FileTagRelMapper fileTagRelMapper;
    private final FileEditContentMapper fileEditContentMapper;
    private final ProjectService projectService;
    private final MinioStorageClient storageClient;
    private final AuditLogService auditLogService;
    private final RagKnowledgeService ragKnowledgeService;
    private final StoredTextExtractionSupport storedTextExtractionSupport;

    PageResponse<FileView> page(Long projectId, String keyword, long pageNum, long pageSize) {
        var currentUser = SecurityUtils.currentUser();
        if (projectId != null) {
            projectService.requireAccessibleProject(projectId);
        }
        LambdaQueryWrapper<FileEntity> wrapper = new LambdaQueryWrapper<FileEntity>()
                .eq(projectId != null, FileEntity::getProjectId, projectId)
                .eq(!currentUser.isAdmin(), FileEntity::getOwnerUserId, currentUser.userId())
                .like(keyword != null && !keyword.isBlank(), FileEntity::getFileName, keyword == null ? null : keyword.trim())
                .orderByDesc(FileEntity::getUpdatedAt);
        Page<FileEntity> page = fileMapper.selectPage(Page.of(pageNum, pageSize), wrapper);
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
        entity.setReviewStatus("APPROVED");
        entity.setIndexStatus("PENDING");
        entity.setArchived(0);
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
            enqueueParseTask(entity, currentUser.userId());
        }
        return toView(entity);
    }

    FileView detail(Long fileId) {
        return toView(requireAccessibleFile(fileId));
    }

    FileView retryParse(Long fileId) {
        var currentUser = SecurityUtils.currentUser();
        FileEntity entity = requireAccessibleFile(fileId);
        if (!indexable(entity)) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "This file type does not support knowledge parsing");
        }
        if (!isFailedStatus(entity.getParseStatus()) && !isFailedStatus(entity.getIndexStatus())) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "Only failed files can be retried");
        }
        FileParseTaskEntity latestTask = fileParseTaskMapper.selectOne(new LambdaQueryWrapper<FileParseTaskEntity>()
                .eq(FileParseTaskEntity::getFileId, fileId)
                .orderByDesc(FileParseTaskEntity::getUpdatedAt)
                .orderByDesc(FileParseTaskEntity::getId)
                .last("limit 1"));
        if (latestTask == null) {
            enqueueParseTask(entity, currentUser.userId());
        } else {
            latestTask.setTaskStatus("PENDING");
            latestTask.setRetryCount(0);
            latestTask.setLastError(null);
            latestTask.setUpdatedBy(currentUser.userId());
            fileParseTaskMapper.updateById(latestTask);
        }
        entity.setParseStatus("PENDING");
        entity.setIndexStatus("PENDING");
        entity.setUpdatedBy(currentUser.userId());
        fileMapper.updateById(entity);
        return toView(entity);
    }

    InternalFileContextView internalContext(Long fileId) {
        FileEntity entity = requireExistingFile(fileId);
        return new InternalFileContextView(
                entity.getId(),
                entity.getProjectId(),
                entity.getFileName(),
                entity.getFileExt(),
                entity.getMimeType(),
                loadPlainTextForContext(fileId, entity),
                entity.getParseStatus(),
                entity.getIndexStatus(),
                latestParseError(entity.getId(), entity.getParseStatus(), entity.getIndexStatus()),
                entity.getArchived(),
                entity.getUpdatedAt()
        );
    }

    InternalFileContextView internalArchive(Long fileId, Long actorUserId) {
        FileEntity entity = requireAccessibleFile(fileId, actorUserId);
        entity.setArchived(1);
        entity.setUpdatedBy(actorUserId);
        fileMapper.updateById(entity);
        try {
            ragKnowledgeService.deleteKbSource(entity.getOwnerUserId(), entity.getProjectId(), "FILE", fileId);
        } catch (RuntimeException ignored) {
        }
        auditLogService.log(actorUserId, "FILE_ARCHIVE_BY_AI", "FILE", fileId, null);
        return internalContext(fileId);
    }

    ResponseEntity<InputStreamResource> stream(Long fileId, boolean inline) {
        var currentUser = SecurityUtils.currentUser();
        FileEntity entity = requireAccessibleFile(fileId);
        auditLogService.log(currentUser, inline ? "FILE_PREVIEW" : "FILE_DOWNLOAD", "FILE", fileId, null);
        String extension = entity.getFileExt() == null ? "" : entity.getFileExt().toLowerCase(Locale.ROOT);
        if (inline) {
            if ("docx".equals(extension)) {
                return docxPreview(entity);
            }
            if ("txt".equals(extension)) {
                return txtPreview(entity);
            }
            if ("md".equals(extension) || "markdown".equals(extension)) {
                return markdownPreview(entity);
            }
        }
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
        FileEntity entity = requireAccessibleFile(fileId);
        if (storageClient.objectExists(entity.getStorageKey())) {
            storageClient.moveObject(entity.getStorageKey(), buildTrashStorageKey(entity));
        }
        fileMapper.deleteById(fileId);
        fileEditContentMapper.delete(new LambdaQueryWrapper<FileEditContentEntity>().eq(FileEditContentEntity::getFileId, fileId));
        ragKnowledgeService.deleteKbSource(entity.getOwnerUserId(), entity.getProjectId(), "FILE", fileId);
        auditLogService.log(currentUser, "FILE_DELETE", "FILE", fileId, null);
    }

    FileEntity requireExistingFile(Long fileId) {
        FileEntity entity = fileMapper.selectById(fileId);
        if (entity == null) {
            throw new BizException(ErrorCodes.NOT_FOUND, "File not found");
        }
        return entity;
    }

    FileEntity requireAccessibleFile(Long fileId) {
        var currentUser = SecurityUtils.currentUser();
        FileEntity entity = requireExistingFile(fileId);
        projectService.requireAccessibleProject(entity.getProjectId());
        if (!currentUser.isAdmin() && !currentUser.userId().equals(entity.getOwnerUserId())) {
            throw new BizException(ErrorCodes.FORBIDDEN, "No permission");
        }
        return entity;
    }

    FileEntity requireAccessibleFile(Long fileId, Long actorUserId) {
        FileEntity entity = requireExistingFile(fileId);
        projectService.requireAccessibleProject(entity.getProjectId(), actorUserId);
        if (actorUserId == null || !actorUserId.equals(entity.getOwnerUserId())) {
            throw new BizException(ErrorCodes.FORBIDDEN, "No permission");
        }
        return entity;
    }

    FileView toView(FileEntity entity) {
        String parseErrorMessage = latestParseError(entity.getId(), entity.getParseStatus(), entity.getIndexStatus());
        return new FileView(
                entity.getId(),
                entity.getProjectId(),
                entity.getFileName(),
                entity.getFileExt(),
                entity.getMimeType(),
                entity.getFileSize(),
                entity.getUploadStatus(),
                entity.getParseStatus(),
                entity.getReviewStatus(),
                entity.getIndexStatus(),
                parseErrorMessage,
                entity.getReviewComment(),
                entity.getArchived(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String latestParseError(Long fileId, String parseStatus, String indexStatus) {
        boolean failed = "FAILED".equalsIgnoreCase(parseStatus) || "FAILED".equalsIgnoreCase(indexStatus);
        if (!failed) {
            return null;
        }
        FileParseTaskEntity task = fileParseTaskMapper.selectOne(new LambdaQueryWrapper<FileParseTaskEntity>()
                .eq(FileParseTaskEntity::getFileId, fileId)
                .orderByDesc(FileParseTaskEntity::getUpdatedAt)
                .orderByDesc(FileParseTaskEntity::getId)
                .last("limit 1"));
        if (task == null || task.getLastError() == null || task.getLastError().isBlank()) {
            return null;
        }
        return task.getLastError();
    }

    private boolean isFailedStatus(String status) {
        return "FAILED".equalsIgnoreCase(status) || "DELETED".equalsIgnoreCase(status);
    }

    private String loadPlainTextForContext(Long fileId, FileEntity entity) {
        FileEditContentEntity stored = fileEditContentMapper.selectOne(new LambdaQueryWrapper<FileEditContentEntity>()
                .eq(FileEditContentEntity::getFileId, fileId)
                .last("limit 1"));
        if (stored != null && stored.getPlainText() != null && !stored.getPlainText().isBlank()) {
            return stored.getPlainText();
        }
        try (InputStream stream = storageClient.getObject(entity.getStorageKey())) {
            return storedTextExtractionSupport.extractPlainText(entity.getOwnerUserId(), entity.getFileName(), entity.getFileExt(), stream);
        } catch (Exception exception) {
            return "";
        }
    }

    void handleParseTask(FileParseTaskEntity task) {
        if (!claimParseTask(task.getId())) {
            return;
        }
        task.setTaskStatus("PROCESSING");
        FileEntity file = fileMapper.selectById(task.getFileId());
        if (file == null) {
            task.setTaskStatus("FAILED");
            task.setLastError("File missing");
            fileParseTaskMapper.updateById(task);
            return;
        }
        markFileProcessing(file);
        try (InputStream stream = storageClient.getObject(file.getStorageKey())) {
            List<RagKnowledgeService.ChunkInput> chunks = extractChunks(file, stream);
            if (chunks.isEmpty()) {
                throw new BizException(ErrorCodes.FILE_ERROR, "No readable text content was extracted");
            }
            ragKnowledgeService.replaceKbSource(file.getOwnerUserId(), file.getProjectId(), "FILE", file.getId(), file.getFileName(), chunks);
            file.setParseStatus("SUCCESS");
            file.setIndexStatus("SUCCESS");
            file.setUpdatedBy(file.getOwnerUserId());
            fileMapper.updateById(file);
            task.setTaskStatus("SUCCESS");
            task.setLastError(null);
            task.setUpdatedBy(file.getOwnerUserId());
            fileParseTaskMapper.updateById(task);
        } catch (Exception exception) {
            int nextRetryCount = (task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1;
            String errorMessage = parseTaskErrorMessage(exception);
            boolean retryable = isRetryableParseError(errorMessage);
            boolean exhausted = nextRetryCount >= 3;
            task.setRetryCount(nextRetryCount);
            task.setLastError(errorMessage);
            task.setTaskStatus(!retryable || exhausted ? "FAILED" : "PENDING");
            task.setUpdatedBy(file.getOwnerUserId());
            fileParseTaskMapper.updateById(task);
            if (!retryable || exhausted) {
                markFileFailed(file, errorMessage);
            } else {
                markFileRetryPending(file);
            }
        }
    }

    private void markFileProcessing(FileEntity file) {
        file.setParseStatus("PROCESSING");
        file.setIndexStatus("PROCESSING");
        file.setUpdatedBy(file.getOwnerUserId());
        fileMapper.updateById(file);
    }

    private void markFileRetryPending(FileEntity file) {
        file.setParseStatus("PENDING");
        file.setIndexStatus("PENDING");
        file.setUpdatedBy(file.getOwnerUserId());
        fileMapper.updateById(file);
    }

    private void markFileFailed(FileEntity file, String errorMessage) {
        file.setParseStatus("FAILED");
        file.setIndexStatus("FAILED");
        file.setUpdatedBy(file.getOwnerUserId());
        fileMapper.updateById(file);
    }

    private String parseTaskErrorMessage(Exception exception) {
        if (exception == null || exception.getMessage() == null || exception.getMessage().isBlank()) {
            return "File parsing failed";
        }
        return exception.getMessage();
    }

    private boolean isRetryableParseError(String message) {
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
                || normalized.contains("network is unreachable")
                || normalized.contains("rate limit")
                || normalized.contains("too many requests")
                || normalized.contains("gateway timeout")
                || normalized.contains("bad gateway")
                || normalized.contains("temporarily overloaded");
    }

    private boolean indexable(FileEntity entity) {
        return entity.getFileExt() != null && INDEXABLE_TYPES.contains(entity.getFileExt().toLowerCase(Locale.ROOT));
    }

    private boolean claimParseTask(Long taskId) {
        if (taskId == null) {
            return false;
        }
        return fileParseTaskMapper.update(
                null,
                new LambdaUpdateWrapper<FileParseTaskEntity>()
                        .eq(FileParseTaskEntity::getId, taskId)
                        .eq(FileParseTaskEntity::getTaskStatus, "PENDING")
                        .set(FileParseTaskEntity::getTaskStatus, "PROCESSING")
        ) > 0;
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

    private String buildTrashStorageKey(FileEntity entity) {
        String safeFileName = entity.getFileName() == null
                ? "deleted-file"
                : entity.getFileName().replace('\\', '_').replace('/', '_');
        return "projects/%d/trash/%d-%d-%s".formatted(
                entity.getProjectId(),
                System.currentTimeMillis(),
                entity.getId(),
                safeFileName
        );
    }

    private void enqueueParseTask(FileEntity entity, Long operatorUserId) {
        Long existingCount = fileParseTaskMapper.selectCount(new LambdaQueryWrapper<FileParseTaskEntity>()
                .eq(FileParseTaskEntity::getFileId, entity.getId())
                .in(FileParseTaskEntity::getTaskStatus, List.of("PENDING", "PROCESSING", "SUCCESS")));
        if (existingCount != null && existingCount > 0) {
            return;
        }
        FileParseTaskEntity task = new FileParseTaskEntity();
        task.setFileId(entity.getId());
        task.setOwnerUserId(entity.getOwnerUserId());
        task.setProjectId(entity.getProjectId());
        task.setTaskStatus("PENDING");
        task.setRetryCount(0);
        task.setCreatedBy(operatorUserId);
        task.setUpdatedBy(operatorUserId);
        fileParseTaskMapper.insert(task);
    }

    private ResponseEntity<InputStreamResource> docxPreview(FileEntity entity) {
        try (InputStream stream = storageClient.getObject(entity.getStorageKey())) {
            byte[] bytes = stream.readAllBytes();
            String html = renderDocxPreview(bytes, entity.getFileName());
            ContentDisposition disposition = ContentDisposition.inline()
                    .filename(stripExtension(entity.getFileName()) + ".html", StandardCharsets.UTF_8)
                    .build();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .contentType(MediaType.parseMediaType("text/html;charset=UTF-8"))
                    .body(new InputStreamResource(new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8))));
        } catch (IOException exception) {
            throw new BizException(ErrorCodes.FILE_ERROR, "Docx preview failed: " + exception.getMessage());
        }
    }

    private ResponseEntity<InputStreamResource> txtPreview(FileEntity entity) {
        try (InputStream stream = storageClient.getObject(entity.getStorageKey())) {
            String html = wrapDocumentHtml(entity.getFileName(), "TXT 在线预览", escapeHtml(TextContentUtils.decodeText(stream.readAllBytes())));
            ContentDisposition disposition = ContentDisposition.inline()
                    .filename(stripExtension(entity.getFileName()) + ".html", StandardCharsets.UTF_8)
                    .build();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .contentType(MediaType.parseMediaType("text/html;charset=UTF-8"))
                    .body(new InputStreamResource(new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8))));
        } catch (IOException exception) {
            throw new BizException(ErrorCodes.FILE_ERROR, "TXT preview failed: " + exception.getMessage());
        }
    }

    private ResponseEntity<InputStreamResource> markdownPreview(FileEntity entity) {
        try (InputStream stream = storageClient.getObject(entity.getStorageKey())) {
            String markdown = TextContentUtils.decodeText(stream.readAllBytes());
            String html = renderMarkdownPreview(entity.getFileName(), markdown);
            ContentDisposition disposition = ContentDisposition.inline()
                    .filename(stripExtension(entity.getFileName()) + ".html", StandardCharsets.UTF_8)
                    .build();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .contentType(MediaType.parseMediaType("text/html;charset=UTF-8"))
                    .body(new InputStreamResource(new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8))));
        } catch (IOException exception) {
            throw new BizException(ErrorCodes.FILE_ERROR, "Markdown preview failed: " + exception.getMessage());
        }
    }

    private List<RagKnowledgeService.ChunkInput> extractChunks(FileEntity file, InputStream stream) throws IOException {
        return storedTextExtractionSupport.extractChunks(file.getOwnerUserId(), file.getFileName(), file.getFileExt(), stream);
    }

    private String renderDocxPreview(byte[] bytes, String fileName) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\" />")
                    .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />")
                    .append("<title>").append(escapeHtml(stripExtension(fileName))).append("</title>")
                    .append("<style>")
                    .append("body{margin:0;background:#f5f1e8;font-family:'Segoe UI','PingFang SC',sans-serif;color:#1c242b;}")
                    .append("main{max-width:920px;margin:0 auto;padding:32px 20px 56px;}")
                    .append("article{background:#fff;border:1px solid rgba(34,41,47,.12);border-radius:24px;box-shadow:0 18px 60px rgba(21,31,45,.08);padding:32px;line-height:1.8;}")
                    .append("h1,h2,h3,h4,h5,h6{margin:1.2em 0 .6em;line-height:1.3;}")
                    .append("p{margin:0 0 1em;}")
                    .append("table{width:100%;border-collapse:collapse;margin:1.2em 0;}")
                    .append("td,th{border:1px solid rgba(34,41,47,.12);padding:10px 12px;vertical-align:top;}")
                    .append("img{max-width:100%;height:auto;border-radius:12px;margin:12px 0;display:block;}")
                    .append(".docx-meta{color:#66707a;font-size:14px;margin-bottom:18px;}")
                    .append(".docx-bullet{display:inline-block;min-width:1.25em;color:#14532d;font-weight:700;}")
                    .append("</style></head><body><main><article>")
                    .append("<div class=\"docx-meta\">DOCX 在线预览</div>");
            for (IBodyElement element : document.getBodyElements()) {
                if (element.getElementType() == BodyElementType.PARAGRAPH) {
                    appendParagraphHtml(html, (XWPFParagraph) element);
                } else if (element.getElementType() == BodyElementType.TABLE) {
                    appendTableHtml(html, (XWPFTable) element);
                }
            }
            html.append("</article></main></body></html>");
            return html.toString();
        }
    }

    private void appendParagraphHtml(StringBuilder html, XWPFParagraph paragraph) {
        String content = renderParagraphContent(paragraph);
        if (content.isBlank()) {
            return;
        }
        String tag = resolveParagraphTag(paragraph);
        html.append('<').append(tag).append('>');
        if (paragraph.getNumID() != null) {
            html.append("<span class=\"docx-bullet\">•</span>");
        }
        html.append(content);
        html.append("</").append(tag).append('>');
    }

    private void appendTableHtml(StringBuilder html, XWPFTable table) {
        html.append("<table>");
        for (XWPFTableRow row : table.getRows()) {
            html.append("<tr>");
            for (XWPFTableCell cell : row.getTableCells()) {
                html.append("<td>");
                for (XWPFParagraph paragraph : cell.getParagraphs()) {
                    appendParagraphHtml(html, paragraph);
                }
                html.append("</td>");
            }
            html.append("</tr>");
        }
        html.append("</table>");
    }

    private String renderParagraphContent(XWPFParagraph paragraph) {
        StringBuilder content = new StringBuilder();
        for (XWPFRun run : paragraph.getRuns()) {
            content.append(renderRunHtml(run));
        }
        if (content.isEmpty()) {
            String fallback = paragraph.getText();
            return fallback == null ? "" : escapeHtml(fallback);
        }
        return content.toString();
    }

    private String renderRunHtml(XWPFRun run) {
        StringBuilder chunk = new StringBuilder();
        String text = run.text();
        if (text != null && !text.isBlank()) {
            chunk.append(escapeHtml(text).replace("\n", "<br />"));
        }
        for (XWPFPicture picture : run.getEmbeddedPictures()) {
            XWPFPictureData pictureData = picture.getPictureData();
            if (pictureData == null) {
                continue;
            }
            chunk.append("<img src=\"")
                    .append(pictureDataToDataUrl(pictureData))
                    .append("\" alt=\"")
                    .append("插图")
                    .append("\" />");
        }
        String content = chunk.toString();
        if (content.isBlank()) {
            return "";
        }
        if (run.isBold()) {
            content = "<strong>" + content + "</strong>";
        }
        if (run.isItalic()) {
            content = "<em>" + content + "</em>";
        }
        if (run.getUnderline() != UnderlinePatterns.NONE) {
            content = "<u>" + content + "</u>";
        }
        StringBuilder style = new StringBuilder();
        if (run.getColor() != null && !run.getColor().isBlank()) {
            style.append("color:#").append(run.getColor()).append(';');
        }
        if (run.getFontSize() > 0) {
            style.append("font-size:").append(run.getFontSize()).append("pt;");
        }
        if (run.getFontFamily() != null && !run.getFontFamily().isBlank()) {
            style.append("font-family:'").append(escapeHtml(run.getFontFamily())).append("';");
        }
        if (!style.isEmpty()) {
            content = "<span style=\"" + style + "\">" + content + "</span>";
        }
        return content;
    }

    private String resolveParagraphTag(XWPFParagraph paragraph) {
        String style = paragraph.getStyle();
        if (style != null) {
            String normalized = style.toLowerCase(Locale.ROOT);
            if (normalized.startsWith("heading")) {
                int level = normalized.chars().filter(Character::isDigit).findFirst().orElse('1') - '0';
                return "h" + Math.max(1, Math.min(level, 6));
            }
        }
        return "p";
    }

    private String paragraphPlainText(XWPFParagraph paragraph) {
        String text = paragraph.getText() == null ? "" : paragraph.getText().trim();
        if (text.isBlank()) {
            return "";
        }
        return paragraph.getNumID() == null ? text : "- " + text;
    }

    private String pictureDataToDataUrl(XWPFPictureData pictureData) {
        String contentType = pictureData.getPackagePart().getContentType();
        return "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(pictureData.getData());
    }

    private String stripExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index > 0 ? fileName.substring(0, index) : fileName;
    }

    private String wrapDocumentHtml(String title, String eyebrow, String bodyText) {
        return "<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\" />"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />"
                + "<title>" + escapeHtml(stripExtension(title)) + "</title>"
                + "<style>"
                + "body{margin:0;background:#eef3fb;font-family:'Segoe UI','PingFang SC','Microsoft YaHei',sans-serif;color:#1c2536;}"
                + "main{max-width:960px;margin:0 auto;padding:32px 20px 56px;}"
                + "article{background:#fff;border:1px solid rgba(66,92,145,.16);border-radius:16px;box-shadow:0 12px 32px rgba(15,23,42,.08);padding:32px;line-height:1.8;}"
                + ".eyebrow{color:#667085;font-size:13px;font-weight:600;letter-spacing:.08em;text-transform:uppercase;margin-bottom:16px;}"
                + "pre{margin:0;white-space:pre-wrap;word-break:break-word;font-family:'Consolas','Cascadia Code','Microsoft YaHei',monospace;font-size:14px;}"
                + "</style></head><body><main><article><div class=\"eyebrow\">" + eyebrow + "</div><pre>"
                + bodyText
                + "</pre></article></main></body></html>";
    }

    private String renderMarkdownPreview(String fileName, String markdown) {
        String bodyHtml = MARKDOWN_RENDERER.render(MARKDOWN_PARSER.parse(markdown == null ? "" : markdown));
        return "<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\" />"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />"
                + "<title>" + escapeHtml(stripExtension(fileName)) + "</title>"
                + "<style>"
                + "body{margin:0;background:#f4f7fb;font-family:'Segoe UI','PingFang SC','Microsoft YaHei',sans-serif;color:#1c2536;}"
                + "main{max-width:960px;margin:0 auto;padding:32px 20px 56px;}"
                + "article{background:#fff;border:1px solid rgba(66,92,145,.16);border-radius:18px;box-shadow:0 12px 32px rgba(15,23,42,.08);padding:32px;line-height:1.85;}"
                + ".eyebrow{color:#667085;font-size:13px;font-weight:600;letter-spacing:.08em;text-transform:uppercase;margin-bottom:16px;}"
                + "h1,h2,h3,h4,h5,h6{margin:1.2em 0 .6em;line-height:1.35;color:#101828;}"
                + "p,ul,ol,blockquote,pre,table{margin:0 0 1em;}"
                + "blockquote{padding:12px 16px;border-left:4px solid rgba(0,96,169,.28);background:rgba(0,96,169,.06);border-radius:12px;}"
                + "code{padding:2px 6px;border-radius:6px;background:rgba(15,23,42,.06);font-family:'Cascadia Code','Consolas',monospace;font-size:.92em;}"
                + "pre{overflow:auto;padding:16px;border-radius:14px;background:#0f172a;color:#e2e8f0;}"
                + "pre code{padding:0;background:transparent;color:inherit;}"
                + "table{width:100%;border-collapse:collapse;}"
                + "td,th{border:1px solid rgba(66,92,145,.16);padding:10px 12px;vertical-align:top;}"
                + "img{max-width:100%;height:auto;border-radius:12px;}"
                + "a{color:#005ea6;text-decoration:none;}"
                + "a:hover{text-decoration:underline;}"
                + "</style></head><body><main><article><div class=\"eyebrow\">Markdown 在线预览</div>"
                + (bodyHtml.isBlank() ? "<p>暂无内容</p>" : bodyHtml)
                + "</article></main></body></html>";
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

    private String escapeHtml(String raw) {
        return raw
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}

@Component
@RequiredArgsConstructor
class FileParseWorker {

    private final FileParseTaskMapper fileParseTaskMapper;
    private final FileService fileService;
    private final FileMapper fileMapper;

    @Scheduled(fixedDelay = 15000)
    public void poll() {
        cleanupStaleUploads();
        queueDocxBackfillTasks();
        List<FileParseTaskEntity> tasks = fileParseTaskMapper.selectList(new LambdaQueryWrapper<FileParseTaskEntity>()
                .eq(FileParseTaskEntity::getTaskStatus, "PENDING")
                .orderByAsc(FileParseTaskEntity::getCreatedAt)
                .last("limit 20"));
        tasks.stream()
                .filter(this::retryReady)
                .limit(5)
                .forEach(fileService::handleParseTask);
    }

    private boolean retryReady(FileParseTaskEntity task) {
        if (task == null) {
            return false;
        }
        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        if (retryCount <= 0) {
            return true;
        }
        LocalDateTime updatedAt = task.getUpdatedAt();
        if (updatedAt == null) {
            return true;
        }
        return !updatedAt.plusSeconds(retryDelaySeconds(retryCount)).isAfter(LocalDateTime.now());
    }

    private long retryDelaySeconds(int retryCount) {
        return switch (Math.max(retryCount, 0)) {
            case 0, 1 -> 30L;
            case 2 -> 90L;
            default -> 180L;
        };
    }

    private void cleanupStaleUploads() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(30);
        List<FileEntity> staleFiles = fileMapper.selectList(new LambdaQueryWrapper<FileEntity>()
                .eq(FileEntity::getUploadStatus, "INIT")
                .lt(FileEntity::getCreatedAt, threshold)
                .last("limit 20"));
        for (FileEntity file : staleFiles) {
            fileMapper.deleteById(file.getId());
        }
    }

    private void queueDocxBackfillTasks() {
        List<FileEntity> files = fileMapper.selectList(new LambdaQueryWrapper<FileEntity>()
                .eq(FileEntity::getUploadStatus, "READY")
                .eq(FileEntity::getFileExt, "docx")
                .eq(FileEntity::getParseStatus, "SKIPPED")
                .orderByAsc(FileEntity::getUpdatedAt)
                .last("limit 5"));
        for (FileEntity file : files) {
            Long existingCount = fileParseTaskMapper.selectCount(new LambdaQueryWrapper<FileParseTaskEntity>()
                    .eq(FileParseTaskEntity::getFileId, file.getId())
                    .in(FileParseTaskEntity::getTaskStatus, List.of("PENDING", "PROCESSING", "SUCCESS")));
            if (existingCount != null && existingCount > 0) {
                continue;
            }
            file.setParseStatus("PENDING");
            file.setIndexStatus("PENDING");
            file.setUpdatedBy(file.getOwnerUserId());
            fileMapper.updateById(file);
            FileParseTaskEntity task = new FileParseTaskEntity();
            task.setFileId(file.getId());
            task.setOwnerUserId(file.getOwnerUserId());
            task.setProjectId(file.getProjectId());
            task.setTaskStatus("PENDING");
            task.setRetryCount(0);
            task.setCreatedBy(file.getOwnerUserId());
            task.setUpdatedBy(file.getOwnerUserId());
            fileParseTaskMapper.insert(task);
        }
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
    private String reviewStatus;
    private String reviewComment;
    private Long reviewedByUserId;
    private java.time.LocalDateTime reviewedAt;
    private String indexStatus;
    private Integer archived;
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
        String reviewStatus,
        String indexStatus,
        String parseErrorMessage,
        String reviewComment,
        Integer archived,
        java.time.LocalDateTime createdAt,
        java.time.LocalDateTime updatedAt
) {
}

record TagBindRequest(@NotNull List<String> tags) {
}

record TagView(Long id, String name, String color) {
}

record InternalFileContextView(
        Long id,
        Long projectId,
        String fileName,
        String fileExt,
        String mimeType,
        String plainText,
        String parseStatus,
        String indexStatus,
        String parseErrorMessage,
        Integer archived,
        java.time.LocalDateTime updatedAt
) {
}


