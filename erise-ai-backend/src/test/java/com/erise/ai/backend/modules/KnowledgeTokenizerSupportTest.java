package com.erise.ai.backend.modules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KnowledgeTokenizerSupportTest {

    private final KnowledgeTokenizerSupport support = new KnowledgeTokenizerSupport();

    @Test
    void tokenizeQueryKeepsMeaningfulChineseEnglishAndNumbers() {
        List<String> tokens = support.tokenizeQuery("Erise-AI 知识搜索 优化方案 2026 的 and the");

        assertThat(tokens).contains("erise-ai", "2026");
        assertThat(tokens).anyMatch(token -> token.contains("知识"));
        assertThat(tokens).anyMatch(token -> token.contains("搜索"));
        assertThat(tokens).anyMatch(token -> token.contains("优化"));
        assertThat(tokens).doesNotContain("的", "and", "the");
    }

    @Test
    void countTermsAccumulatesRepeatedTermsAndFiltersNoise() {
        Map<String, Integer> frequencies = support.countTerms("搜索 搜索 search search 的 A", 16);

        assertThat(frequencies).containsEntry("search", 2);
        assertThat(frequencies.entrySet()).anyMatch(entry -> entry.getKey().contains("搜索") && entry.getValue() >= 2);
        assertThat(frequencies).doesNotContainKeys("的", "a");
    }

    @Test
    void countTermsSkipsOverlongFallbackNoise() {
        Map<String, Integer> frequencies = support.countTerms("@".repeat(160), 16);

        assertThat(frequencies).isEmpty();
    }
}
