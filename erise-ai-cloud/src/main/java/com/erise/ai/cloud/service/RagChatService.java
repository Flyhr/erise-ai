package com.erise.ai.cloud.service;

import com.erise.ai.cloud.integration.BackendInternalClient;
import com.erise.ai.cloud.provider.OpenAiCompatClient;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class RagChatService {

    private final BackendInternalClient backendInternalClient;
    private final OpenAiCompatClient openAiCompatClient;

    public ChatResult chat(ChatRequest request) {
        List<BackendInternalClient.KnowledgeChunk> chunks = backendInternalClient.retrieveKnowledge(
                request.userId(), request.projectId(), request.question(), 5
        );
        if (chunks.isEmpty()) {
            return new ChatResult(
                    "无法基于当前项目知识给出可靠回答。",
                    List.of(),
                    List.of(),
                    0.2,
                    "NO_EVIDENCE"
            );
        }
        String systemPrompt = buildSystemPrompt(chunks);
        String answer = openAiCompatClient.chat(systemPrompt, request.question());
        List<Citation> citations = chunks.stream()
                .map(chunk -> new Citation(chunk.sourceType(), chunk.sourceId(), chunk.title(), chunk.snippet(), null))
                .toList();
        return new ChatResult(answer, citations, List.of(), 0.78, null);
    }

    public Flux<String> stream(ChatRequest request) {
        ChatResult result = chat(request);
        String answer = result.answer();
        if (answer.isBlank()) {
            return Flux.just("");
        }
        List<String> pieces = new ArrayList<>();
        int chunkSize = Math.max(12, answer.length() / 8);
        for (int i = 0; i < answer.length(); i += chunkSize) {
            pieces.add(answer.substring(i, Math.min(answer.length(), i + chunkSize)));
        }
        return Flux.fromIterable(pieces);
    }

    private String buildSystemPrompt(List<BackendInternalClient.KnowledgeChunk> chunks) {
        StringBuilder builder = new StringBuilder();
        builder.append("You are the Erise-AI project assistant. ");
        builder.append("Answer only with grounded facts from the provided knowledge. ");
        builder.append("If the knowledge is insufficient, say you cannot answer reliably.\n\n");
        builder.append("Knowledge:\n");
        for (int i = 0; i < chunks.size(); i++) {
            BackendInternalClient.KnowledgeChunk chunk = chunks.get(i);
            builder.append("[").append(i + 1).append("] ")
                    .append(chunk.title()).append(": ")
                    .append(chunk.snippet()).append("\n");
        }
        builder.append("\nAnswer in Chinese and keep references grounded.");
        return builder.toString();
    }

    public record ChatRequest(Long userId, String username, String roleCode, Long sessionId, Long projectId, String question) {
    }

    public record Citation(String sourceType, Long sourceId, String sourceTitle, String snippet, Integer pageNo) {
    }

    public record ChatResult(String answer, List<Citation> citations, List<String> usedTools, Double confidence,
                             String refusedReason) {
    }
}
