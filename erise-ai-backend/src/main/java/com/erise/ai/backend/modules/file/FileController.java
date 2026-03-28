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
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.Data;
import lombok.RequiredArgsConstructor;
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

    private static final List<String> INDEXABLE_TYPES = List.of("pdf", "md", "markdown", "txt", "doc", "docx");

    private final FileMapper fileMapper;
    private final FileParseTaskMapper fileParseTaskMapper;
    private final TagMapper tagMapper;
    private final FileTagRelMapper fileTagRelMapper;
    private final FileEditContentMapper fileEditContentMapper;
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
            enqueueParseTask(entity, currentUser.userId());
        }
        return toView(entity);
    }

    FileView detail(Long fileId) {
        return toView(requireAccessibleFile(fileId));
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
                entity.getUpdatedAt()
        );
    }

    ResponseEntity<InputStreamResource> stream(Long fileId, boolean inline) {
        var currentUser = SecurityUtils.currentUser();
        FileEntity entity = requireAccessibleFile(fileId);
        auditLogService.log(currentUser, inline ? "FILE_PREVIEW" : "FILE_DOWNLOAD", "FILE", fileId, null);
        if (inline && "docx".equalsIgnoreCase(entity.getFileExt())) {
            return docxPreview(entity);
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
        fileMapper.deleteById(fileId);
        knowledgeService.deleteForSource(entity.getProjectId(), "FILE", fileId);
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

    private String loadPlainTextForContext(Long fileId, FileEntity entity) {
        FileEditContentEntity stored = fileEditContentMapper.selectOne(new LambdaQueryWrapper<FileEditContentEntity>()
                .eq(FileEditContentEntity::getFileId, fileId)
                .last("limit 1"));
        if (stored != null && stored.getPlainText() != null && !stored.getPlainText().isBlank()) {
            return stored.getPlainText();
        }
        try (InputStream stream = storageClient.getObject(entity.getStorageKey())) {
            return switch (entity.getFileExt() == null ? "" : entity.getFileExt().toLowerCase(Locale.ROOT)) {
                case "txt" -> new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                case "md", "markdown" -> stripMarkdown(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
                case "pdf" -> extractPdfText(stream);
                case "doc" -> extractDocText(stream);
                case "docx" -> extractDocxText(stream);
                default -> "";
            };
        } catch (IOException exception) {
            throw new BizException(ErrorCodes.FILE_ERROR, "File context load failed: " + exception.getMessage());
        }
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

    private boolean indexable(FileEntity entity) {
        return entity.getFileExt() != null && INDEXABLE_TYPES.contains(entity.getFileExt().toLowerCase(Locale.ROOT));
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

    private void enqueueParseTask(FileEntity entity, Long operatorUserId) {
        Long existingCount = fileParseTaskMapper.selectCount(new LambdaQueryWrapper<FileParseTaskEntity>()
                .eq(FileParseTaskEntity::getFileId, entity.getId())
                .in(FileParseTaskEntity::getTaskStatus, List.of("PENDING", "SUCCESS")));
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
                    .contentType(MediaType.TEXT_HTML)
                    .body(new InputStreamResource(new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8))));
        } catch (IOException exception) {
            throw new BizException(ErrorCodes.FILE_ERROR, "Docx preview failed: " + exception.getMessage());
        }
    }

    private List<KnowledgeService.ChunkInput> extractChunks(FileEntity file, InputStream stream) throws IOException {
        return switch (file.getFileExt()) {
            case "txt" -> knowledgeService.splitText(new String(stream.readAllBytes(), StandardCharsets.UTF_8), null);
            case "md", "markdown" -> knowledgeService.splitText(stripMarkdown(new String(stream.readAllBytes(), StandardCharsets.UTF_8)), null);
            case "pdf" -> extractPdf(stream);
            case "doc" -> extractDoc(stream);
            case "docx" -> extractDocx(stream);
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

    private List<KnowledgeService.ChunkInput> extractDoc(InputStream stream) throws IOException {
        try (HWPFDocument document = new HWPFDocument(stream); WordExtractor extractor = new WordExtractor(document)) {
            return knowledgeService.splitText(extractor.getText(), null);
        }
    }

    private List<KnowledgeService.ChunkInput> extractDocx(InputStream stream) throws IOException {
        try (XWPFDocument document = new XWPFDocument(stream)) {
            List<KnowledgeService.ChunkInput> chunks = new ArrayList<>();
            for (String block : readDocxBlocks(document)) {
                chunks.addAll(knowledgeService.splitText(block, null));
            }
            return chunks;
        }
    }

    private List<String> readDocxBlocks(XWPFDocument document) {
        List<String> blocks = new ArrayList<>();
        for (IBodyElement element : document.getBodyElements()) {
            if (element.getElementType() == BodyElementType.PARAGRAPH) {
                String text = paragraphPlainText((XWPFParagraph) element);
                if (!text.isBlank()) {
                    blocks.add(text);
                }
                continue;
            }
            if (element.getElementType() == BodyElementType.TABLE) {
                XWPFTable table = (XWPFTable) element;
                for (XWPFTableRow row : table.getRows()) {
                    List<String> cells = new ArrayList<>();
                    for (XWPFTableCell cell : row.getTableCells()) {
                        String cellText = cell.getText() == null ? "" : cell.getText().replace("\n", " ").trim();
                        if (!cellText.isBlank()) {
                            cells.add(cellText);
                        }
                    }
                    if (!cells.isEmpty()) {
                        blocks.add(String.join(" | ", cells));
                    }
                }
            }
        }
        return blocks;
    }

    private String extractPdfText(InputStream stream) throws IOException {
        byte[] bytes = stream.readAllBytes();
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            StringBuilder content = new StringBuilder();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document).trim();
                if (!text.isBlank()) {
                    if (!content.isEmpty()) {
                        content.append("\n\n");
                    }
                    content.append(text);
                }
            }
            return content.toString();
        }
    }

    private String extractDocText(InputStream stream) throws IOException {
        try (HWPFDocument document = new HWPFDocument(stream); WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String extractDocxText(InputStream stream) throws IOException {
        try (XWPFDocument document = new XWPFDocument(stream)) {
            return String.join("\n\n", readDocxBlocks(document));
        }
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
                    .append("<div class=\"docx-meta\">DOCX 鍦ㄧ嚎棰勮</div>");
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
            html.append("<span class=\"docx-bullet\">鈥?/span>");
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
                    .append("鎻掑浘")
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
        queueDocxBackfillTasks();
        List<FileParseTaskEntity> tasks = fileParseTaskMapper.selectList(new LambdaQueryWrapper<FileParseTaskEntity>()
                .eq(FileParseTaskEntity::getTaskStatus, "PENDING")
                .orderByAsc(FileParseTaskEntity::getCreatedAt)
                .last("limit 5"));
        tasks.forEach(fileService::handleParseTask);
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
                    .in(FileParseTaskEntity::getTaskStatus, List.of("PENDING", "SUCCESS")));
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

record InternalFileContextView(
        Long id,
        Long projectId,
        String fileName,
        String fileExt,
        String mimeType,
        String plainText,
        String parseStatus,
        java.time.LocalDateTime updatedAt
) {
}


