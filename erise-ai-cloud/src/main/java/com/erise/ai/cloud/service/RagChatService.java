package com.erise.ai.cloud.service;

import com.erise.ai.cloud.provider.DeepSeekClient;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

@Service
public class RagChatService {

    private static final String PROVIDER_UNAVAILABLE_MESSAGE = "当前未配置可用的 DeepSeek 服务，请先设置 DEEPSEEK_API_KEY 后再发起聊天。";
    private static final String GENERAL_SYSTEM_PROMPT =
            "You are Erise-AI's DeepSeek assistant. Answer in Chinese, be direct, and clearly mark guesses as inferences rather than facts.";

    private final DeepSeekClient deepSeekClient;

    public RagChatService(DeepSeekClient deepSeekClient) {
        this.deepSeekClient = deepSeekClient;
    }

    public ChatResult chat(ChatRequest request) {
        if (!deepSeekClient.isConfigured()) {
            return unavailableResult();
        }

        try {
            String answer = deepSeekClient.chat(buildMessages(request)).trim();
            if (!StringUtils.hasText(answer)) {
                return unavailableResult();
            }
            return new ChatResult(answer, List.of(), List.of("DEEPSEEK_CHAT"), 0.82, null);
        } catch (RuntimeException exception) {
            return unavailableResult();
        }
    }

    public Flux<String> stream(ChatRequest request) {
        if (!deepSeekClient.isConfigured()) {
            return chunkText(PROVIDER_UNAVAILABLE_MESSAGE);
        }

        try {
            return deepSeekClient.stream(buildMessages(request))
                    .switchIfEmpty(chunkText(PROVIDER_UNAVAILABLE_MESSAGE));
        } catch (RuntimeException exception) {
            return chunkText(PROVIDER_UNAVAILABLE_MESSAGE);
        }
    }

    private List<DeepSeekClient.ChatMessage> buildMessages(ChatRequest request) {
        List<DeepSeekClient.ChatMessage> messages = new ArrayList<>();
        messages.add(new DeepSeekClient.ChatMessage("system", GENERAL_SYSTEM_PROMPT));
        if (request.messages() == null || request.messages().isEmpty()) {
            messages.add(new DeepSeekClient.ChatMessage("user", request.question()));
            return messages;
        }
        for (PromptMessage item : request.messages()) {
            if (!StringUtils.hasText(item.content())) {
                continue;
            }
            messages.add(new DeepSeekClient.ChatMessage(normalizeRole(item.role()), item.content()));
        }
        return messages;
    }

    private String normalizeRole(String role) {
        if ("assistant".equalsIgnoreCase(role)) {
            return "assistant";
        }
        return "user";
    }

    private ChatResult unavailableResult() {
        return new ChatResult(PROVIDER_UNAVAILABLE_MESSAGE, List.of(), List.of("PROVIDER_UNAVAILABLE"), 0.15, "PROVIDER_UNAVAILABLE");
    }

    private Flux<String> chunkText(String answer) {
        if (!StringUtils.hasText(answer)) {
            return Flux.just("");
        }
        List<String> pieces = new ArrayList<>();
        int chunkSize = Math.max(12, answer.length() / 8);
        for (int i = 0; i < answer.length(); i += chunkSize) {
            pieces.add(answer.substring(i, Math.min(answer.length(), i + chunkSize)));
        }
        return Flux.fromIterable(pieces);
    }

    public record ChatRequest(Long userId, String username, String roleCode, Long sessionId, Long projectId,
                              String question, List<PromptMessage> messages) {
    }

    public record PromptMessage(String role, String content) {
    }

    public record Citation(String sourceType, Long sourceId, String sourceTitle, String snippet, Integer pageNo) {
    }

    public record ChatResult(String answer, List<Citation> citations, List<String> usedTools, Double confidence,
                             String refusedReason) {
    }
}