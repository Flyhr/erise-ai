package com.erise.ai.backend.modules;

import com.erise.ai.backend.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
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
    private final AiService aiService;

    @PostMapping("/knowledge/retrieve")
    public ApiResponse<List<SearchResultView>> retrieveKnowledge(@Valid @RequestBody InternalKnowledgeRequest request) {
        return ApiResponse.success(searchService.retrieveKnowledge(request.userId(), request.projectId(), request.keyword(), request.limit()));
    }

    @GetMapping("/projects/{id}/context")
    public ApiResponse<ProjectDetailView> projectContext(@PathVariable Long id) {
        return ApiResponse.success(projectService.internalDetail(id));
    }

    @PostMapping("/ai/messages/persist")
    public ApiResponse<Void> persist(@Valid @RequestBody InternalPersistMessageRequest request) {
        aiService.persistAssistantMessage(request);
        return ApiResponse.success("success", null);
    }
}

record InternalKnowledgeRequest(@NotNull Long userId, @NotNull Long projectId, @NotBlank String keyword, int limit) {
}
