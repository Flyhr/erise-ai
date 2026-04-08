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
    private final AiTempFileService aiTempFileService;

    @PostMapping("/knowledge/retrieve")
    public ApiResponse<java.util.List<SearchResultView>> retrieveKnowledge(@Valid @RequestBody InternalKnowledgeRequest request) {
        return ApiResponse.success(searchService.retrieveKnowledge(
                request.userId(),
                request.projectScopeIds(),
                request.attachments(),
                request.keyword(),
                request.limit()
        ));
    }

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
        return ApiResponse.success(documentService.internalUpdateTitle(id, request.title()));
    }

    @GetMapping("/files/{id}/context")
    public ApiResponse<InternalFileContextView> fileContext(@PathVariable Long id) {
        return ApiResponse.success(fileService.internalContext(id));
    }

    @GetMapping("/ai/temp-files/{id}/context")
    public ApiResponse<InternalAiTempFileContextView> tempFileContext(@PathVariable Long id) {
        return ApiResponse.success(aiTempFileService.internalContext(id));
    }
}

record InternalKnowledgeRequest(@NotNull Long userId,
                                java.util.List<Long> projectScopeIds,
                                java.util.List<InternalKnowledgeAttachment> attachments,
                                @NotBlank String keyword,
                                int limit) {
}

record InternalKnowledgeAttachment(@NotBlank String attachmentType, @NotNull Long sourceId, Long sessionId) {
}

record InternalDocumentTitleUpdateRequest(@NotBlank String title) {
}
