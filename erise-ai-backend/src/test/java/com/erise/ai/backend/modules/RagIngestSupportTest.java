package com.erise.ai.backend.modules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erise.ai.backend.integration.ai.CloudAiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class RagIngestSupportTest {

    @Test
    void splitTextUsesOverlapAndSectionAwareChunking() {
        RagKnowledgeService service = new RagKnowledgeService(
                null,
                null,
                null,
                mock(CloudAiClient.class),
                new ObjectMapper()
        );

        StringBuilder builder = new StringBuilder("1. Overview\n\n");
        for (int index = 0; index < 40; index++) {
            builder.append("Sentence ")
                    .append(index)
                    .append(" validates recursive chunking, overlap windows, and section-aware indexing. ");
        }

        List<RagKnowledgeService.ChunkInput> chunks = service.splitText(builder.toString(), 1);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.chunkText()).isNotBlank();
            assertThat(chunk.chunkText().length()).isLessThanOrEqualTo(500);
            assertThat(chunk.pageNo()).isEqualTo(1);
            assertThat(chunk.sectionPath()).isEqualTo("1. Overview");
        });

        String firstChunkSuffix = chunks.getFirst().chunkText();
        firstChunkSuffix = firstChunkSuffix.substring(firstChunkSuffix.length() - 16);
        assertThat(chunks.get(1).chunkText()).contains(firstChunkSuffix);
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
