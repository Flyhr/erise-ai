package com.erise.ai.backend.modules;

import com.erise.ai.backend.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1")
@RequiredArgsConstructor
public class InternalController {

    private final SearchService searchService;
    private final ProjectService projectService;
    private final DocumentService documentService;
    private final FileService fileService;
    private final OfficeFileService officeFileService;
    private final AiTempFileService aiTempFileService;

    /*
     * 废弃说明（2026-04）：
     * AI 助手检索已经迁移到 Python 侧直接访问 Qdrant dense+sparse 一体化索引，
     * 不再走 Java 侧的 `/internal/v1/knowledge/retrieve` BM25 检索入口。
     *
     * 这里保留注释仅用于回滚排障参考，避免后续继续把聊天检索重新耦合回旧接口。
     *
     * 历史签名如下：
     *
     * @PostMapping("/knowledge/retrieve")
     * public ApiResponse<java.util.List<SearchResultView>> retrieveKnowledge(
     *         @Valid @RequestBody InternalKnowledgeRequest request
     * ) { ... }
     */

    @GetMapping("/projects/{id}/context")
    public ApiResponse<ProjectDetailView> projectContext(@PathVariable Long id) {
        return ApiResponse.success(projectService.internalDetail(id));
    }

    @GetMapping("/documents/{id}/context")
    public ApiResponse<InternalDocumentContextView> documentContext(@PathVariable Long id) {
        return ApiResponse.success(documentService.internalContext(id));
    }

    @PostMapping("/documents/{id}/title")
    public ApiResponse<InternalDocumentContextView> updateDocumentTitle(@PathVariable Long id,
                                                                        @Valid @RequestBody InternalDocumentTitleUpdateRequest request) {
        return ApiResponse.success(documentService.internalUpdateTitle(id, request.actorUserId(), request.title()));
    }

    @PostMapping("/documents/{id}/summary")
    public ApiResponse<InternalDocumentContextView> updateDocumentSummary(@PathVariable Long id,
                                                                          @Valid @RequestBody InternalDocumentSummaryUpdateRequest request) {
        return ApiResponse.success(documentService.internalUpdateSummary(id, request.actorUserId(), request.summary()));
    }

    @PostMapping("/documents/{id}/content")
    public ApiResponse<InternalDocumentContextView> updateDocumentContent(@PathVariable Long id,
                                                                          @Valid @RequestBody InternalDocumentContentUpdateRequest request) {
        return ApiResponse.success(documentService.internalUpdateContent(id, request.actorUserId(), request.plainText()));
    }

    @PostMapping("/documents/{id}/tags")
    public ApiResponse<java.util.List<TagView>> updateDocumentTags(@PathVariable Long id,
                                                                   @Valid @RequestBody InternalDocumentTagsUpdateRequest request) {
        return ApiResponse.success(documentService.internalUpdateTags(id, request.actorUserId(), request.tags()));
    }

    @GetMapping("/files/{id}/context")
    public ApiResponse<InternalFileContextView> fileContext(@PathVariable Long id) {
        return ApiResponse.success(fileService.internalContext(id));
    }

    @PostMapping("/files/{id}/archive")
    public ApiResponse<InternalFileContextView> archiveFile(@PathVariable Long id,
                                                            @Valid @RequestBody InternalFileArchiveRequest request) {
        return ApiResponse.success(fileService.internalArchive(id, request.actorUserId()));
    }

    @PostMapping("/files/{id}/title")
    public ApiResponse<InternalFileContextView> updateFileTitle(@PathVariable Long id,
                                                                @Valid @RequestBody InternalFileTitleUpdateRequest request) {
        return ApiResponse.success(fileService.internalUpdateTitle(id, request.actorUserId(), request.title()));
    }

    @PostMapping("/files/{id}/content")
    public ApiResponse<InternalFileContextView> updateFileContent(@PathVariable Long id,
                                                                  @Valid @RequestBody InternalFileContentUpdateRequest request) {
        officeFileService.internalUpdateContent(id, request.actorUserId(), request.plainText());
        return ApiResponse.success(fileService.internalContext(id));
    }

    @PostMapping("/projects/{id}/weekly-report-draft")
    public ApiResponse<InternalDocumentContextView> createWeeklyReportDraft(@PathVariable Long id,
                                                                            @Valid @RequestBody InternalProjectWeeklyReportDraftRequest request) {
        return ApiResponse.success(documentService.internalCreateProjectWeeklyReportDraft(
                id,
                request.actorUserId(),
                request.title(),
                request.summary(),
                request.plainText()
        ));
    }

    @GetMapping("/ai/temp-files/{id}/context")
    public ApiResponse<InternalAiTempFileContextView> tempFileContext(@PathVariable Long id) {
        return ApiResponse.success(aiTempFileService.internalContext(id));
    }
}

/*
 * 废弃说明（2026-04）：
 * 下面这组 DTO 原本只服务于 Java 内部 BM25 检索入口。
 * 在 AI 检索迁移到 Qdrant dense+sparse 一体化之后，它们已经没有运行时用途，
 * 这里只保留文字说明，便于追溯历史接口形态。
 *
 * record InternalKnowledgeRequest(...)
 * record InternalKnowledgeAttachment(...)
 */

record InternalDocumentTitleUpdateRequest(@NotNull Long actorUserId, @NotBlank String title) {
}

record InternalDocumentSummaryUpdateRequest(@NotNull Long actorUserId, @NotBlank String summary) {
}

record InternalDocumentContentUpdateRequest(@NotNull Long actorUserId, @NotNull String plainText) {
}

record InternalDocumentTagsUpdateRequest(@NotNull Long actorUserId, @NotNull java.util.List<String> tags) {
}

record InternalFileArchiveRequest(@NotNull Long actorUserId) {
}

record InternalFileTitleUpdateRequest(@NotNull Long actorUserId, @NotBlank String title) {
}

record InternalFileContentUpdateRequest(@NotNull Long actorUserId, @NotNull String plainText) {
}

record InternalProjectWeeklyReportDraftRequest(
        @NotNull Long actorUserId,
        @NotBlank String title,
        String summary,
        @NotBlank String plainText
) {
}
