package com.erise.ai.backend.modules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.erise.ai.backend.integration.ai.CloudAiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
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
    void extractPlainTextDropsRepeatedPdfHeadersAndFooters() throws IOException {
        CloudAiClient cloudAiClient = mock(CloudAiClient.class);
        RagKnowledgeService service = new RagKnowledgeService(
                null,
                null,
                null,
                cloudAiClient,
                new ObjectMapper()
        );
        StoredTextExtractionSupport support = new StoredTextExtractionSupport(service, cloudAiClient);

        String plainText = support.extractPlainText(1L, "kb.pdf", "pdf", new ByteArrayInputStream(samplePdf()));

        assertThat(plainText).contains("This page body verifies that repeated headers and footers are removed");
        assertThat(plainText).doesNotContain("JAVA KB MANUAL");
        assertThat(plainText).doesNotContain("Page 1");
        assertThat(plainText).doesNotContain("Page 2");
        assertThat(plainText).doesNotContain("Page 3");
        verifyNoInteractions(cloudAiClient);
    }

    private byte[] samplePdf() throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (int pageNo = 1; pageNo <= 3; pageNo++) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(font, 12);
                    stream.newLineAtOffset(72, 760);
                    stream.showText("JAVA KB MANUAL");
                    stream.newLineAtOffset(0, -36);
                    stream.showText("This page body verifies that repeated headers and footers are removed from indexing.");
                    stream.newLineAtOffset(0, -18);
                    stream.showText("This page includes enough body text to stay above the PDF fallback threshold.");
                    stream.newLineAtOffset(0, -18);
                    stream.showText("Chunking should keep semantic continuity and avoid sending noisy layout text to vectors.");
                    stream.newLineAtOffset(0, -560);
                    stream.showText("Page " + pageNo);
                    stream.endText();
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
