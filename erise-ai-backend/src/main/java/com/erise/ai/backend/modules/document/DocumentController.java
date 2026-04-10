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
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @GetMapping
    public ApiResponse<PageResponse<DocumentSummaryView>> page(@RequestParam(required = false) Long projectId,
                                                               @RequestParam(required = false) String q,
                                                               @RequestParam(defaultValue = "1") long pageNum,
                                                               @RequestParam(defaultValue = "10") long pageSize) {
        return ApiResponse.success(documentService.page(projectId, q, pageNum, pageSize));
    }

    @PostMapping
    public ApiResponse<DocumentDetailView> create(@Valid @RequestBody DocumentCreateRequest request) {
        return ApiResponse.success(documentService.create(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<DocumentDetailView> detail(@PathVariable Long id) {
        return ApiResponse.success(documentService.detail(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<DocumentDetailView> update(@PathVariable Long id, @Valid @RequestBody DocumentUpdateRequest request) {
        return ApiResponse.success(documentService.update(id, request));
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<DocumentDetailView> publish(@PathVariable Long id) {
        return ApiResponse.success(documentService.publish(id));
    }

    @PostMapping("/publish-new")
    public ApiResponse<DocumentDetailView> publishNew(@Valid @RequestBody DocumentPublishNewRequest request) {
        return ApiResponse.success(documentService.publishNew(request));
    }

    @PostMapping("/{id}/retry-index")
    public ApiResponse<DocumentDetailView> retryIndex(@PathVariable Long id) {
        return ApiResponse.success(documentService.retryIndex(id));
    }

    @GetMapping("/{id}/versions")
    public ApiResponse<PageResponse<DocumentVersionView>> versions(@PathVariable Long id,
                                                                  @RequestParam(defaultValue = "1") long pageNum,
                                                                  @RequestParam(defaultValue = "10") long pageSize) {
        return ApiResponse.success(documentService.versions(id, pageNum, pageSize));
    }

    @GetMapping("/{id}/versions/{versionNo}")
    public ApiResponse<DocumentVersionView> version(@PathVariable Long id, @PathVariable Integer versionNo) {
        return ApiResponse.success(documentService.version(id, versionNo));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return ApiResponse.success("success", null);
    }
}

@Service
@RequiredArgsConstructor
class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentMapper documentMapper;
    private final DocumentContentMapper documentContentMapper;
    private final DocumentVersionMapper documentVersionMapper;
    private final ProjectService projectService;
    private final TextChunkingSupport textChunkingSupport;
    private final RagKnowledgeService ragKnowledgeService;
    private final AuditLogService auditLogService;

    PageResponse<DocumentSummaryView> page(Long projectId, String keyword, long pageNum, long pageSize) {
        var currentUser = SecurityUtils.currentUser();
        if (projectId != null) {
            projectService.requireAccessibleProject(projectId);
        }
        LambdaQueryWrapper<DocumentEntity> wrapper = new LambdaQueryWrapper<DocumentEntity>()
                .eq(projectId != null, DocumentEntity::getProjectId, projectId)
                .eq(!currentUser.isAdmin(), DocumentEntity::getOwnerUserId, currentUser.userId())
                .and(keyword != null && !keyword.isBlank(), query -> query
                        .like(DocumentEntity::getTitle, keyword.trim())
                        .or()
                        .like(DocumentEntity::getSummary, keyword.trim()))
                .orderByDesc(DocumentEntity::getUpdatedAt);
        Page<DocumentEntity> page = documentMapper.selectPage(Page.of(pageNum, pageSize), wrapper);
        return PageResponse.of(page.getRecords().stream().map(this::toSummary).toList(), pageNum, pageSize, page.getTotal());
    }

    DocumentDetailView create(DocumentCreateRequest request) {
        var currentUser = SecurityUtils.currentUser();
        projectService.requireAccessibleProject(request.projectId());
        DocumentEntity document = new DocumentEntity();
        document.setOwnerUserId(currentUser.userId());
        document.setProjectId(request.projectId());
        document.setTitle(request.title());
        document.setSummary(request.summary());
        document.setDocStatus("DRAFT");
        document.setLatestVersionNo(0);
        document.setEditorType("TIPTAP");
        document.setCreatedBy(currentUser.userId());
        document.setUpdatedBy(currentUser.userId());
        documentMapper.insert(document);

        DocumentContentEntity content = new DocumentContentEntity();
        content.setDocumentId(document.getId());
        content.setContentJson("{}");
        content.setContentHtmlSnapshot("<p></p>");
        content.setPlainText("");
        content.setCreatedBy(currentUser.userId());
        content.setUpdatedBy(currentUser.userId());
        documentContentMapper.insert(content);

        auditLogService.log(currentUser, "DOCUMENT_CREATE", "DOCUMENT", document.getId(), request);
        return detail(document.getId());
    }

    DocumentDetailView detail(Long id) {
        DocumentEntity document = requireAccessibleDocument(id);
        DocumentContentEntity content = contentByDocumentId(document.getId());
        return toDetail(document, content);
    }

    DocumentDetailView internalDetail(Long id) {
        DocumentEntity document = requireExistingDocument(id);
        DocumentContentEntity content = contentByDocumentId(document.getId());
        return toDetail(document, content);
    }

    InternalDocumentContextView internalContext(Long id) {
        DocumentEntity document = requireExistingDocument(id);
        DocumentContentEntity content = contentByDocumentId(document.getId());
        return toInternalContext(document, content);
    }

    InternalDocumentContextView internalUpdateTitle(Long id, String title) {
        DocumentEntity document = requireExistingDocument(id);
        DocumentContentEntity content = contentByDocumentId(document.getId());
        document.setTitle(title);
        document.setUpdatedBy(document.getOwnerUserId());
        documentMapper.updateById(document);
        syncRagKnowledge(document, content.getPlainText());
        return toInternalContext(document, content);
    }

    DocumentDetailView update(Long id, DocumentUpdateRequest request) {
        var currentUser = SecurityUtils.currentUser();
        DocumentEntity document = requireAccessibleDocument(id);
        document.setTitle(request.title());
        document.setSummary(request.summary());
        document.setUpdatedBy(currentUser.userId());
        documentMapper.updateById(document);

        DocumentContentEntity content = contentByDocumentId(document.getId());
        content.setContentJson(request.contentJson());
        content.setContentHtmlSnapshot(request.contentHtmlSnapshot());
        content.setPlainText(request.plainText());
        content.setUpdatedBy(currentUser.userId());
        documentContentMapper.updateById(content);

        syncRagKnowledge(document, request.plainText());
        auditLogService.log(currentUser, "DOCUMENT_SAVE", "DOCUMENT", id, request);
        return toDetail(document, content);
    }

    DocumentDetailView publish(Long id) {
        var currentUser = SecurityUtils.currentUser();
        DocumentEntity document = requireAccessibleDocument(id);
        DocumentContentEntity content = contentByDocumentId(id);
        int nextVersion = document.getLatestVersionNo() + 1;

        DocumentVersionEntity version = new DocumentVersionEntity();
        version.setDocumentId(id);
        version.setVersionNo(nextVersion);
        version.setTitle(document.getTitle());
        version.setContentJson(content.getContentJson());
        version.setContentHtmlSnapshot(content.getContentHtmlSnapshot());
        version.setPlainText(content.getPlainText());
        version.setPublishedBy(currentUser.userId());
        version.setCreatedBy(currentUser.userId());
        version.setUpdatedBy(currentUser.userId());
        documentVersionMapper.insert(version);

        document.setDocStatus("PUBLISHED");
        document.setLatestVersionNo(nextVersion);
        document.setUpdatedBy(currentUser.userId());
        documentMapper.updateById(document);

        syncRagKnowledge(document, content.getPlainText());
        auditLogService.log(currentUser, "DOCUMENT_PUBLISH", "DOCUMENT", id, java.util.Map.of("versionNo", nextVersion));
        return detail(id);
    }

    DocumentDetailView retryIndex(Long id) {
        var currentUser = SecurityUtils.currentUser();
        DocumentEntity document = requireAccessibleDocument(id);
        DocumentContentEntity content = contentByDocumentId(id);
        syncRagKnowledge(document, content.getPlainText());
        auditLogService.log(currentUser, "DOCUMENT_INDEX_RETRY", "DOCUMENT", id, null);
        return detail(id);
    }

    @Transactional
    DocumentDetailView publishNew(DocumentPublishNewRequest request) {
        var currentUser = SecurityUtils.currentUser();
        projectService.requireAccessibleProject(request.projectId());

        DocumentEntity document = new DocumentEntity();
        document.setOwnerUserId(currentUser.userId());
        document.setProjectId(request.projectId());
        document.setTitle(request.title());
        document.setSummary(request.summary());
        document.setDocStatus("PUBLISHED");
        document.setLatestVersionNo(1);
        document.setEditorType("TIPTAP");
        document.setCreatedBy(currentUser.userId());
        document.setUpdatedBy(currentUser.userId());
        documentMapper.insert(document);

        DocumentContentEntity content = new DocumentContentEntity();
        content.setDocumentId(document.getId());
        content.setContentJson(request.contentJson());
        content.setContentHtmlSnapshot(request.contentHtmlSnapshot());
        content.setPlainText(request.plainText());
        content.setCreatedBy(currentUser.userId());
        content.setUpdatedBy(currentUser.userId());
        documentContentMapper.insert(content);

        DocumentVersionEntity version = new DocumentVersionEntity();
        version.setDocumentId(document.getId());
        version.setVersionNo(1);
        version.setTitle(document.getTitle());
        version.setContentJson(content.getContentJson());
        version.setContentHtmlSnapshot(content.getContentHtmlSnapshot());
        version.setPlainText(content.getPlainText());
        version.setPublishedBy(currentUser.userId());
        version.setCreatedBy(currentUser.userId());
        version.setUpdatedBy(currentUser.userId());
        documentVersionMapper.insert(version);

        syncRagKnowledge(document, content.getPlainText());
        auditLogService.log(currentUser, "DOCUMENT_PUBLISH_NEW", "DOCUMENT", document.getId(), request);
        return detail(document.getId());
    }

    PageResponse<DocumentVersionView> versions(Long id, long pageNum, long pageSize) {
        requireAccessibleDocument(id);
        Page<DocumentVersionEntity> page = documentVersionMapper.selectPage(Page.of(pageNum, pageSize),
                new LambdaQueryWrapper<DocumentVersionEntity>()
                        .eq(DocumentVersionEntity::getDocumentId, id)
                        .orderByDesc(DocumentVersionEntity::getVersionNo));
        return PageResponse.of(page.getRecords().stream().map(this::toVersion).toList(), pageNum, pageSize, page.getTotal());
    }

    DocumentVersionView version(Long id, Integer versionNo) {
        requireAccessibleDocument(id);
        DocumentVersionEntity entity = documentVersionMapper.selectOne(new LambdaQueryWrapper<DocumentVersionEntity>()
                .eq(DocumentVersionEntity::getDocumentId, id)
                .eq(DocumentVersionEntity::getVersionNo, versionNo)
                .last("limit 1"));
        if (entity == null) {
            throw new BizException(ErrorCodes.NOT_FOUND, "Version not found", HttpStatus.NOT_FOUND);
        }
        return toVersion(entity);
    }

    void delete(Long id) {
        var currentUser = SecurityUtils.currentUser();
        DocumentEntity document = requireAccessibleDocument(id);
        documentMapper.deleteById(id);
        documentContentMapper.delete(new LambdaQueryWrapper<DocumentContentEntity>().eq(DocumentContentEntity::getDocumentId, id));
        documentVersionMapper.delete(new LambdaQueryWrapper<DocumentVersionEntity>().eq(DocumentVersionEntity::getDocumentId, id));
        ragKnowledgeService.deleteKbSource(document.getOwnerUserId(), document.getProjectId(), "DOCUMENT", id);
        auditLogService.log(currentUser, "DOCUMENT_DELETE", "DOCUMENT", id, null);
    }

    private void syncRagKnowledge(DocumentEntity document, String plainText) {
        try {
            ragKnowledgeService.replaceKbSource(
                    document.getOwnerUserId(),
                    document.getProjectId(),
                    "DOCUMENT",
                    document.getId(),
                    document.getTitle(),
                    textChunkingSupport.chunkText(document.getOwnerUserId(), "document-" + document.getId(), plainText, null)
            );
        } catch (RuntimeException exception) {
            log.warn("Failed to sync document knowledge index, documentId={}", document.getId(), exception);
        }
    }

    DocumentEntity requireExistingDocument(Long id) {
        DocumentEntity document = documentMapper.selectById(id);
        if (document == null) {
            throw new BizException(ErrorCodes.NOT_FOUND, "Document not found", HttpStatus.NOT_FOUND);
        }
        return document;
    }

    DocumentEntity requireAccessibleDocument(Long id) {
        DocumentEntity document = requireExistingDocument(id);
        projectService.requireAccessibleProject(document.getProjectId());
        return document;
    }

    private DocumentContentEntity contentByDocumentId(Long documentId) {
        DocumentContentEntity content = documentContentMapper.selectOne(new LambdaQueryWrapper<DocumentContentEntity>()
                .eq(DocumentContentEntity::getDocumentId, documentId)
                .last("limit 1"));
        if (content == null) {
            throw new BizException(ErrorCodes.NOT_FOUND, "Document content not found", HttpStatus.NOT_FOUND);
        }
        return content;
    }

    private DocumentSummaryView toSummary(DocumentEntity document) {
        return new DocumentSummaryView(document.getId(), document.getProjectId(), document.getTitle(), document.getSummary(),
                document.getDocStatus(), document.getLatestVersionNo(), document.getCreatedAt(), document.getUpdatedAt());
    }

    private DocumentDetailView toDetail(DocumentEntity document, DocumentContentEntity content) {
        RagKnowledgeService.KnowledgeSyncStatusView syncStatus =
                ragKnowledgeService.kbSyncStatus(document.getOwnerUserId(), "DOCUMENT", document.getId());
        return new DocumentDetailView(
                document.getId(),
                document.getProjectId(),
                document.getTitle(),
                document.getSummary(),
                document.getDocStatus(),
                document.getLatestVersionNo(),
                content.getContentJson(),
                content.getContentHtmlSnapshot(),
                content.getPlainText(),
                syncStatus.parseStatus(),
                syncStatus.indexStatus(),
                syncStatus.errorMessage(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }

    private InternalDocumentContextView toInternalContext(DocumentEntity document, DocumentContentEntity content) {
        return new InternalDocumentContextView(
                document.getId(),
                document.getProjectId(),
                document.getTitle(),
                document.getSummary(),
                content.getPlainText(),
                document.getUpdatedAt()
        );
    }

    private DocumentVersionView toVersion(DocumentVersionEntity entity) {
        return new DocumentVersionView(entity.getVersionNo(), entity.getTitle(), entity.getContentJson(),
                entity.getContentHtmlSnapshot(), entity.getPlainText(), entity.getCreatedAt());
    }
}

interface DocumentMapper extends BaseMapper<DocumentEntity> {
}

interface DocumentContentMapper extends BaseMapper<DocumentContentEntity> {
}

interface DocumentVersionMapper extends BaseMapper<DocumentVersionEntity> {
}

@Data
@TableName("ea_document")
class DocumentEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerUserId;
    private Long projectId;
    private String title;
    private String summary;
    private String docStatus;
    private Integer latestVersionNo;
    private String editorType;
}

@Data
@TableName("ea_document_content")
class DocumentContentEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long documentId;
    private String contentJson;
    private String contentHtmlSnapshot;
    private String plainText;
}

@Data
@TableName("ea_document_version")
class DocumentVersionEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long documentId;
    private Integer versionNo;
    private String title;
    private String contentJson;
    private String contentHtmlSnapshot;
    private String plainText;
    private Long publishedBy;
}

record DocumentCreateRequest(@NotNull Long projectId, @NotBlank String title, String summary) {
}

record DocumentUpdateRequest(
        @NotBlank String title,
        String summary,
        @NotNull String contentJson,
        @NotNull String contentHtmlSnapshot,
        @NotNull String plainText
) {
}

record DocumentPublishNewRequest(
        @NotNull Long projectId,
        @NotBlank String title,
        String summary,
        @NotNull String contentJson,
        @NotNull String contentHtmlSnapshot,
        @NotNull String plainText
) {
}

record DocumentSummaryView(
        Long id,
        Long projectId,
        String title,
        String summary,
        String docStatus,
        Integer latestVersionNo,
        java.time.LocalDateTime createdAt,
        java.time.LocalDateTime updatedAt
) {
}

record DocumentDetailView(
        Long id,
        Long projectId,
        String title,
        String summary,
        String docStatus,
        Integer latestVersionNo,
        String contentJson,
        String contentHtmlSnapshot,
        String plainText,
        String parseStatus,
        String indexStatus,
        String parseErrorMessage,
        java.time.LocalDateTime createdAt,
        java.time.LocalDateTime updatedAt
) {
}

record InternalDocumentContextView(
        Long id,
        Long projectId,
        String title,
        String summary,
        String plainText,
        java.time.LocalDateTime updatedAt
) {
}

record DocumentVersionView(
        Integer versionNo,
        String title,
        String contentJson,
        String contentHtmlSnapshot,
        String plainText,
        java.time.LocalDateTime createdAt
) {
}
