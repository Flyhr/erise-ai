package com.erise.ai.cloud;

import static org.assertj.core.api.Assertions.assertThat;

import com.erise.ai.cloud.provider.DeepSeekClient;
import com.erise.ai.cloud.service.RagChatService;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

class RagChatServiceTest {

    @Test
    void supportsGeneralChatWithoutProjectContext() {
        RagChatService service = new RagChatService(new WorkingDeepSeekClient());

        RagChatService.ChatResult result = service.chat(new RagChatService.ChatRequest(
                1L, "u", "USER", null, null, "问题", List.of(new RagChatService.PromptMessage("user", "问题"))
        ));

        assertThat(result.refusedReason()).isNull();
        assertThat(result.citations()).isEmpty();
        assertThat(result.usedTools()).contains("DEEPSEEK_CHAT");
    }

    @Test
    void preservesConversationHistoryWhenCallingDeepSeek() {
        WorkingDeepSeekClient client = new WorkingDeepSeekClient();
        RagChatService service = new RagChatService(client);

        service.chat(new RagChatService.ChatRequest(
                1L,
                "u",
                "USER",
                99L,
                null,
                "继续",
                List.of(
                        new RagChatService.PromptMessage("user", "第一句"),
                        new RagChatService.PromptMessage("assistant", "第一条回答"),
                        new RagChatService.PromptMessage("user", "继续")
                )
        ));

        assertThat(client.lastMessages()).extracting(DeepSeekClient.ChatMessage::content)
                .contains("第一句", "第一条回答", "继续");
    }

    @Test
    void returnsProviderUnavailableWhenDeepSeekMissing() {
        RagChatService service = new RagChatService(new BrokenDeepSeekClient());

        RagChatService.ChatResult result = service.chat(new RagChatService.ChatRequest(
                1L, "u", "USER", null, null, "问题", List.of(new RagChatService.PromptMessage("user", "问题"))
        ));

        assertThat(result.refusedReason()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(result.answer()).contains("DEEPSEEK_API_KEY");
    }

    static class WorkingDeepSeekClient extends DeepSeekClient {
        private List<ChatMessage> lastMessages = List.of();

        WorkingDeepSeekClient() {
            super(null, null, null);
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public String chat(List<ChatMessage> messages) {
            lastMessages = messages;
            return "基于当前上下文的回答";
        }

        @Override
        public Flux<String> stream(List<ChatMessage> messages) {
            lastMessages = messages;
            return Flux.just("基于", "当前", "上下文的回答");
        }

        List<ChatMessage> lastMessages() {
            return lastMessages;
        }
    }

    static class BrokenDeepSeekClient extends DeepSeekClient {
        BrokenDeepSeekClient() {
            super(null, null, null);
        }

        @Override
        public boolean isConfigured() {
            return false;
        }
    }
}