package com.erise.ai.cloud.controller;

import com.erise.ai.cloud.service.RagChatService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/internal/v1/ai")
@RequiredArgsConstructor
public class InternalAiController {

    private final RagChatService ragChatService;

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        RagChatService.ChatResult result = ragChatService.chat(
                new RagChatService.ChatRequest(request.userId(), request.username(), request.roleCode(),
                        request.sessionId(), request.projectId(), request.question())
        );
        return new ChatResponse(result.answer(), result.citations().stream()
                .map(item -> new Citation(item.sourceType(), item.sourceId(), item.sourceTitle(), item.snippet(), item.pageNo()))
                .toList(), result.usedTools(), result.confidence(), result.refusedReason());
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@Valid @RequestBody ChatRequest request) {
        return ragChatService.stream(new RagChatService.ChatRequest(
                request.userId(), request.username(), request.roleCode(), request.sessionId(), request.projectId(), request.question()
        ));
    }

    public record ChatRequest(
            @NotNull Long userId,
            @NotBlank String username,
            @NotBlank String roleCode,
            Long sessionId,
            @NotNull Long projectId,
            @NotBlank String question
    ) {
    }

    public record ChatResponse(
            String answer,
            List<Citation> citations,
            List<String> usedTools,
            Double confidence,
            String refusedReason
    ) {
    }

    public record Citation(String sourceType, Long sourceId, String sourceTitle, String snippet, Integer pageNo) {
    }
}
