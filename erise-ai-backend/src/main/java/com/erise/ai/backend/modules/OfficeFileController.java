package com.erise.ai.backend.modules;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erise.ai.backend.common.api.ApiResponse;
import com.erise.ai.backend.common.exception.BizException;
import com.erise.ai.backend.common.exception.ErrorCodes;
import com.erise.ai.backend.common.util.SecurityUtils;
import com.erise.ai.backend.common.util.TextContentUtils;
import com.erise.ai.backend.integration.storage.MinioStorageClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.converter.WordToHtmlConverter;
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
import org.jsoup.Jsoup;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class OfficeFileController {

    private final OfficeFileService officeFileService;

    @GetMapping("/{id}/office")
    public ApiResponse<EditableOfficeFileView> detail(@PathVariable Long id) {
        return ApiResponse.success(officeFileService.detail(id));
    }

    @PutMapping("/{id}/office")
    public ApiResponse<EditableOfficeFileView> save(@PathVariable Long id, @Valid @RequestBody OfficeFileUpdateRequest request) {
        return ApiResponse.success(officeFileService.save(id, request));
    }

    @GetMapping("/{id}/office/preview")
    public ResponseEntity<InputStreamResource> preview(@PathVariable Long id) {
        return officeFileService.preview(id);
    }
}

@Service
@RequiredArgsConstructor
class OfficeFileService {

    private final FileMapper fileMapper;
    private final FileEditContentMapper fileEditContentMapper;
    private final ProjectService projectService;
    private final MinioStorageClient storageClient;
    private final TextChunkingSupport textChunkingSupport;
    private final RagKnowledgeService ragKnowledgeService;
    private final AuditLogService auditLogService;

    EditableOfficeFileView detail(Long fileId) {
        var currentUser = SecurityUtils.currentUser();
        FileEntity file = requireEditableFile(fileId);
        FileEditContentEntity stored = fileEditContentMapper.selectOne(new LambdaQueryWrapper<FileEditContentEntity>()
                .eq(FileEditContentEntity::getFileId, fileId)
                .last("limit 1"));
        if (stored != null && stored.getContentHtmlSnapshot() != null && !stored.getContentHtmlSnapshot().isBlank()) {
            auditLogService.log(currentUser, "FILE_EDIT_OPEN", "FILE", fileId, file.getFileName());
            return new EditableOfficeFileView(file.getId(), file.getProjectId(), file.getFileName(), file.getFileExt(),
                    stored.getEditorType(), stored.getContentHtmlSnapshot(), stored.getPlainText(), file.getUpdatedAt());
        }
        OriginalOfficeContent original = loadOriginalContent(file);
        syncKnowledge(file, original.plainText(), currentUser.userId());
        auditLogService.log(currentUser, "FILE_EDIT_OPEN", "FILE", fileId, file.getFileName());
        return new EditableOfficeFileView(file.getId(), file.getProjectId(), file.getFileName(), file.getFileExt(),
                "OFFICE_HTML", original.html(), original.plainText(), file.getUpdatedAt());
    }

    EditableOfficeFileView save(Long fileId, OfficeFileUpdateRequest request) {
        var currentUser = SecurityUtils.currentUser();
        FileEntity file = requireEditableFile(fileId);
        FileEditContentEntity stored = fileEditContentMapper.selectOne(new LambdaQueryWrapper<FileEditContentEntity>()
                .eq(FileEditContentEntity::getFileId, fileId)
                .last("limit 1"));
        if (stored == null) {
            stored = new FileEditContentEntity();
            stored.setFileId(fileId);
            stored.setEditorType("OFFICE_HTML");
            stored.setCreatedBy(currentUser.userId());
            stored.setUpdatedBy(currentUser.userId());
            stored.setContentHtmlSnapshot(request.contentHtmlSnapshot());
            stored.setPlainText(request.plainText());
            fileEditContentMapper.insert(stored);
        } else {
            stored.setContentHtmlSnapshot(request.contentHtmlSnapshot());
            stored.setPlainText(request.plainText());
            stored.setUpdatedBy(currentUser.userId());
            fileEditContentMapper.updateById(stored);
        }
        syncKnowledge(file, request.plainText(), currentUser.userId());
        auditLogService.log(currentUser, "FILE_EDIT_SAVE", "FILE", fileId, file.getFileName());
        return new EditableOfficeFileView(file.getId(), file.getProjectId(), file.getFileName(), file.getFileExt(),
                stored.getEditorType(), stored.getContentHtmlSnapshot(), stored.getPlainText(), file.getUpdatedAt());
    }

    ResponseEntity<InputStreamResource> preview(Long fileId) {
        FileEntity file = requireEditableFile(fileId);
        EditableOfficeFileView detail = detail(fileId);
        String snapshotHtml = detail.contentHtmlSnapshot() == null ? "" : detail.contentHtmlSnapshot();
        var parsed = Jsoup.parse(snapshotHtml);
        var article = parsed.selectFirst("main > article");
        if (article != null) {
            article.select(".docx-meta, .eyebrow").remove();
        }
        String previewBody = article != null ? article.html() : parsed.body().html();
        String previewHtml = wrapOfficeDocumentStart(stripExtension(file.getFileName()), "文件在线预览")
                + (previewBody == null || previewBody.isBlank() ? "<p>暂无正文内容</p>" : previewBody)
                + "</article></main></body></html>";
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(stripExtension(file.getFileName()) + ".html", StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType("text/html;charset=UTF-8"))
                .body(new InputStreamResource(new ByteArrayInputStream(previewHtml.getBytes(StandardCharsets.UTF_8))));
    }

    private void syncKnowledge(FileEntity file, String plainText, Long operatorUserId) {
        try {
            ragKnowledgeService.replaceKbSource(
                    file.getOwnerUserId(),
                    file.getProjectId(),
                    "FILE",
                    file.getId(),
                    file.getFileName(),
                    textChunkingSupport.chunkText(file.getOwnerUserId(), "file-" + file.getId(), plainText, null)
            );
        } catch (RuntimeException ignored) {
        }
        file.setParseStatus("SUCCESS");
        file.setIndexStatus("SUCCESS");
        file.setUpdatedBy(operatorUserId);
        fileMapper.updateById(file);
    }

    private FileEntity requireEditableFile(Long fileId) {
        var currentUser = SecurityUtils.currentUser();
        FileEntity file = fileMapper.selectById(fileId);
        if (file == null) {
            throw new BizException(ErrorCodes.NOT_FOUND, "File not found", HttpStatus.NOT_FOUND);
        }
        projectService.requireAccessibleProject(file.getProjectId());
        if (!currentUser.isAdmin() && !currentUser.userId().equals(file.getOwnerUserId())) {
            throw new BizException(ErrorCodes.FORBIDDEN, "No permission", HttpStatus.FORBIDDEN);
        }
        String extension = file.getFileExt() == null ? "" : file.getFileExt().toLowerCase(Locale.ROOT);
        if (!"doc".equals(extension) && !"docx".equals(extension) && !"txt".equals(extension)) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "Only doc/docx/txt files support online editing", HttpStatus.BAD_REQUEST);
        }
        return file;
    }

    private OriginalOfficeContent loadOriginalContent(FileEntity file) {
        try (InputStream stream = storageClient.getObject(file.getStorageKey())) {
            byte[] bytes = stream.readAllBytes();
            String html = switch (file.getFileExt().toLowerCase(Locale.ROOT)) {
                case "docx" -> renderDocxHtml(bytes, file.getFileName());
                case "doc" -> renderDocHtml(bytes, file.getFileName());
                case "txt" -> renderTxtHtml(bytes, file.getFileName());
                default -> throw new BizException(ErrorCodes.BAD_REQUEST, "Unsupported office file type", HttpStatus.BAD_REQUEST);
            };
            return new OriginalOfficeContent(html, Jsoup.parse(html).text());
        } catch (IOException exception) {
            throw new BizException(ErrorCodes.FILE_ERROR, "Office file parse failed: " + exception.getMessage());
        }
    }

    private String renderDocxHtml(byte[] bytes, String fileName) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            StringBuilder html = new StringBuilder();
            html.append(wrapOfficeDocumentStart(stripExtension(fileName), "DOCX 在线编辑预览"));
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

    private String renderDocHtml(byte[] bytes, String fileName) {
        try (HWPFDocument document = new HWPFDocument(new ByteArrayInputStream(bytes))) {
            Document target = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            WordToHtmlConverter converter = new WordToHtmlConverter(target);
            converter.processDocument(document);
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.METHOD, "html");
            transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(converter.getDocument()), new StreamResult(writer));
            String body = Jsoup.parse(writer.toString()).body().html();
            return wrapOfficeDocumentStart(stripExtension(fileName), "DOC Preview") + body + "</article></main></body></html>";
        } catch (Exception exception) {
            throw new BizException(ErrorCodes.FILE_ERROR, "DOC preview failed: " + exception.getMessage());
        }
    }

    private String renderTxtHtml(byte[] bytes, String fileName) {
        String textValue = TextContentUtils.decodeText(bytes);
        return wrapOfficeDocumentStart(stripExtension(fileName), "TXT Preview")
                + "<pre style=\"white-space:pre-wrap;word-break:break-word;font-family:'Consolas','Cascadia Code','Microsoft YaHei',monospace;font-size:14px;\">"
                + escapeHtml(textValue)
                + "</pre></article></main></body></html>";
    }

    private String wrapOfficeDocumentStart(String title, String eyebrow) {
        return "<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\" />"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />"
                + "<title>" + escapeHtml(title) + "</title>"
                + "<style>"
                + "body{margin:0;background:#eef4ef;font-family:'Segoe UI','PingFang SC',sans-serif;color:#1c242b;}"
                + "main{max-width:960px;margin:0 auto;padding:32px 20px 56px;}"
                + "article{background:#fff;border:1px solid rgba(34,41,47,.12);border-radius:24px;box-shadow:0 18px 60px rgba(21,31,45,.08);padding:32px;line-height:1.8;}"
                + "h1,h2,h3,h4,h5,h6{margin:1.2em 0 .6em;line-height:1.3;}"
                + "p{margin:0 0 1em;}"
                + "table{width:100%;border-collapse:collapse;margin:1.2em 0;}"
                + "td,th{border:1px solid rgba(34,41,47,.12);padding:10px 12px;vertical-align:top;}"
                + "img{max-width:100%;height:auto;border-radius:12px;margin:12px 0;display:block;}"
                + ".docx-meta{color:#66707a;font-size:14px;margin-bottom:18px;}"
                + ".docx-bullet{display:inline-block;min-width:1.25em;color:#14532d;font-weight:700;}"
                + "</style></head><body><main><article><div class=\"docx-meta\">" + eyebrow + "</div>";
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
                    .append("\" alt=\"插图\" />");
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

    private String pictureDataToDataUrl(XWPFPictureData pictureData) {
        String contentType = pictureData.getPackagePart().getContentType();
        return "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(pictureData.getData());
    }

    private String stripExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index > 0 ? fileName.substring(0, index) : fileName;
    }

    private String escapeHtml(String raw) {
        return raw
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    record OriginalOfficeContent(String html, String plainText) {
    }
}

interface FileEditContentMapper extends BaseMapper<FileEditContentEntity> {
}

@Data
@TableName("ea_file_edit_content")
class FileEditContentEntity extends com.erise.ai.backend.common.entity.AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long fileId;
    private String contentHtmlSnapshot;
    private String plainText;
    private String editorType;
}

record OfficeFileUpdateRequest(@NotNull String contentHtmlSnapshot, @NotNull String plainText) {
}

record EditableOfficeFileView(
        Long id,
        Long projectId,
        String fileName,
        String fileExt,
        String editorType,
        String contentHtmlSnapshot,
        String plainText,
        LocalDateTime updatedAt
) {
}
