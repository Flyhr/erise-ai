package com.erise.ai.cloud;

import static org.assertj.core.api.Assertions.assertThat;

import com.erise.ai.cloud.integration.BackendInternalClient;
import com.erise.ai.cloud.provider.OpenAiCompatClient;
import com.erise.ai.cloud.service.RagChatService;
import java.util.List;
import org.junit.jupiter.api.Test;

class RagChatServiceTest {

    @Test
    void refusesWhenNoKnowledge() {
        RagChatService service = new RagChatService(
                new EmptyBackendClient(),
                new FakeOpenAiClient()
        );

        RagChatService.ChatResult result = service.chat(new RagChatService.ChatRequest(1L, "u", "USER", null, 1L, "问题"));

        assertThat(result.refusedReason()).isEqualTo("NO_EVIDENCE");
        assertThat(result.citations()).isEmpty();
    }

    @Test
    void returnsCitationsWhenKnowledgeExists() {
        RagChatService service = new RagChatService(
                new FixedBackendClient(),
                new FakeOpenAiClient()
        );

        RagChatService.ChatResult result = service.chat(new RagChatService.ChatRequest(1L, "u", "USER", null, 1L, "问题"));

        assertThat(result.refusedReason()).isNull();
        assertThat(result.citations()).hasSize(1);
    }

    static class EmptyBackendClient extends BackendInternalClient {
        EmptyBackendClient() {
            super(null, null, null);
        }

        @Override
        public List<KnowledgeChunk> retrieveKnowledge(Long userId, Long projectId, String keyword, int limit) {
            return List.of();
        }
    }

    static class FixedBackendClient extends BackendInternalClient {
        FixedBackendClient() {
            super(null, null, null);
        }

        @Override
        public List<KnowledgeChunk> retrieveKnowledge(Long userId, Long projectId, String keyword, int limit) {
            return List.of(new KnowledgeChunk("DOCUMENT", 2L, projectId, "设计文档", "DOCUMENT", "这里是证据片段", null));
        }
    }

    static class FakeOpenAiClient extends OpenAiCompatClient {
        FakeOpenAiClient() {
            super(null, null, null);
        }

        @Override
        public String chat(String systemPrompt, String userPrompt) {
            return "基于知识库的回答";
        }
    }
}
