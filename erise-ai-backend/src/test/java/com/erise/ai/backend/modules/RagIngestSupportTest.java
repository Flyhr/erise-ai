package com.erise.ai.backend.modules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erise.ai.backend.common.exception.BizException;
import com.erise.ai.backend.common.exception.ErrorCodes;
import com.erise.ai.backend.integration.ai.CloudAiClient;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class RagIngestSupportTest {

    @Test
    void chunkTextDelegatesToPythonChunkingService() {
        CloudAiClient cloudAiClient = mock(CloudAiClient.class);
        when(cloudAiClient.chunkText(eq(1L), any(CloudAiClient.TextChunkRequest.class), anyString()))
                .thenReturn(new CloudAiClient.TextChunkResponse(
                        List.of(
                                new CloudAiClient.FileExtractChunkResponse(0, "1. Overview\nParagraph one.", 1, "1. Overview"),
                                new CloudAiClient.FileExtractChunkResponse(1, "2. Appendix\nParagraph two.", 1, "1. Overview > 2. Appendix")
                        )
                ));
        TextChunkingSupport support = new TextChunkingSupport(cloudAiClient);

        List<RagKnowledgeService.ChunkInput> chunks = support.chunkText(
                1L,
                "document-101",
                "1. Overview\n\nParagraph one.\n\n2. Appendix\n\nParagraph two.",
                1
        );

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).sectionPath()).isEqualTo("1. Overview");
        assertThat(chunks.get(1).sectionPath()).isEqualTo("1. Overview > 2. Appendix");
        verify(cloudAiClient).chunkText(eq(1L), any(CloudAiClient.TextChunkRequest.class), anyString());
    }

    @Test
    void chunkTextFallsBackToMinimalJavaChunkingWhenPythonIsUnavailable() {
        CloudAiClient cloudAiClient = mock(CloudAiClient.class);
        when(cloudAiClient.chunkText(eq(1L), any(CloudAiClient.TextChunkRequest.class), anyString()))
                .thenThrow(new BizException(ErrorCodes.AI_ERROR, "AI service unavailable"));
        TextChunkingSupport support = new TextChunkingSupport(cloudAiClient);

        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < 20; index++) {
            builder.append("Paragraph ").append(index).append(" validates the minimal Java fallback chunking path.\n\n");
        }

        List<RagKnowledgeService.ChunkInput> chunks = support.chunkText(1L, "document-102", builder.toString(), null);

        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.chunkText()).isNotBlank();
            assertThat(chunk.chunkText().length()).isLessThanOrEqualTo(500);
            assertThat(chunk.sectionPath()).isNull();
        });
    }

    @Test
    void extractPlainTextDelegatesToPythonExtractionService() throws IOException {
        CloudAiClient cloudAiClient = mock(CloudAiClient.class);
        when(cloudAiClient.extractFileText(eq(1L), eq("kb.pdf"), eq("pdf"), any(byte[].class), anyString()))
                .thenReturn(new CloudAiClient.FileExtractResponse(
                        "This page body verifies that repeated headers and footers are removed from indexing.",
                        List.of(
                                new CloudAiClient.FileExtractChunkResponse(0, "Chunk A", 1, "1. Overview")
                        ),
                        "pymupdf-text",
                        false,
                        1
                ));
        StoredTextExtractionSupport support = new StoredTextExtractionSupport(cloudAiClient);

        String plainText = support.extractPlainText(
                1L,
                "kb.pdf",
                "pdf",
                new ByteArrayInputStream("fake-pdf".getBytes(StandardCharsets.UTF_8))
        );

        assertThat(plainText).contains("repeated headers and footers are removed");
        verify(cloudAiClient).extractFileText(eq(1L), eq("kb.pdf"), eq("pdf"), any(byte[].class), anyString());
    }

    @Test
    void extractChunksPreservesPageNumbersAndSectionPathFromPythonExtraction() throws IOException {
        CloudAiClient cloudAiClient = mock(CloudAiClient.class);
        when(cloudAiClient.extractFileText(eq(1L), eq("kb.docx"), eq("docx"), any(byte[].class), anyString()))
                .thenReturn(new CloudAiClient.FileExtractResponse(
                        "1. Overview\n\nParagraph one.\n\n2. Appendix\n\nParagraph two.",
                        List.of(
                                new CloudAiClient.FileExtractChunkResponse(0, "1. Overview\nParagraph one.", 1, "1. Overview"),
                                new CloudAiClient.FileExtractChunkResponse(1, "2. Appendix\nParagraph two.", 2, "1. Overview > 2. Appendix")
                        ),
                        "python-docx",
                        false,
                        0
                ));
        StoredTextExtractionSupport support = new StoredTextExtractionSupport(cloudAiClient);

        List<RagKnowledgeService.ChunkInput> chunks = support.extractChunks(
                1L,
                "kb.docx",
                "docx",
                new ByteArrayInputStream("fake-docx".getBytes(StandardCharsets.UTF_8))
        );

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).pageNo()).isEqualTo(1);
        assertThat(chunks.get(1).pageNo()).isEqualTo(2);
        assertThat(chunks.get(1).sectionPath()).isEqualTo("1. Overview > 2. Appendix");
        verify(cloudAiClient).extractFileText(eq(1L), eq("kb.docx"), eq("docx"), any(byte[].class), anyString());
    }
}
