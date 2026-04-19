package com.erise.ai.backend.modules;

import com.erise.ai.backend.integration.ai.CloudAiClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
class TextChunkingSupport {

    private static final Logger log = LoggerFactory.getLogger(TextChunkingSupport.class);
    private static final int MAX_FALLBACK_CHUNK_SIZE = 500;

    private final CloudAiClient cloudAiClient;

    TextChunkingSupport(CloudAiClient cloudAiClient) {
        this.cloudAiClient = cloudAiClient;
    }

    List<RagKnowledgeService.ChunkInput> chunkText(Long ownerUserId, String sourceHint, String plainText, Integer pageNo) {
        String normalized = normalizeText(plainText);
        if (normalized.isBlank()) {
            return List.of();
        }
        try {
            CloudAiClient.TextChunkResponse response = cloudAiClient.chunkText(
                    ownerUserId,
                    new CloudAiClient.TextChunkRequest(normalized, pageNo),
                    requestId(sourceHint)
            );
            List<RagKnowledgeService.ChunkInput> chunks = toChunkInputs(response);
            if (!chunks.isEmpty()) {
                return chunks;
            }
            log.warn("Python chunking returned no chunks for source {}", sourceHint);
        } catch (RuntimeException exception) {
            log.warn("Falling back to minimal Java chunking for source {}: {}", sourceHint, exception.getMessage());
        }
        return fallbackChunks(normalized, pageNo);
    }

    private List<RagKnowledgeService.ChunkInput> toChunkInputs(CloudAiClient.TextChunkResponse response) {
        if (response == null || response.chunks() == null || response.chunks().isEmpty()) {
            return List.of();
        }
        List<RagKnowledgeService.ChunkInput> chunks = new ArrayList<>();
        int nextChunkIndex = 0;
        for (CloudAiClient.FileExtractChunkResponse item : response.chunks()) {
            if (item == null || item.chunkText() == null || item.chunkText().isBlank()) {
                continue;
            }
            int chunkIndex = item.chunkNum() == null ? nextChunkIndex : item.chunkNum();
            chunks.add(new RagKnowledgeService.ChunkInput(
                    chunkIndex,
                    item.chunkText().trim(),
                    item.pageNo(),
                    item.sectionPath()
            ));
            nextChunkIndex = chunkIndex + 1;
        }
        return chunks;
    }

    private List<RagKnowledgeService.ChunkInput> fallbackChunks(String plainText, Integer pageNo) {
        List<RagKnowledgeService.ChunkInput> chunks = new ArrayList<>();
        String[] paragraphs = plainText.split("\\n\\s*\\n+");
        StringBuilder buffer = new StringBuilder();
        for (String paragraph : paragraphs) {
            String normalizedParagraph = normalizeText(paragraph);
            if (normalizedParagraph.isBlank()) {
                continue;
            }
            if (normalizedParagraph.length() > MAX_FALLBACK_CHUNK_SIZE) {
                appendFallbackChunk(chunks, buffer, pageNo);
                sliceLongParagraph(chunks, normalizedParagraph, pageNo);
                continue;
            }
            if (buffer.length() == 0) {
                buffer.append(normalizedParagraph);
                continue;
            }
            if (buffer.length() + 2 + normalizedParagraph.length() <= MAX_FALLBACK_CHUNK_SIZE) {
                buffer.append("\n\n").append(normalizedParagraph);
                continue;
            }
            appendFallbackChunk(chunks, buffer, pageNo);
            buffer.append(normalizedParagraph);
        }
        appendFallbackChunk(chunks, buffer, pageNo);
        return chunks;
    }

    private void sliceLongParagraph(List<RagKnowledgeService.ChunkInput> chunks, String paragraph, Integer pageNo) {
        int start = 0;
        while (start < paragraph.length()) {
            int end = Math.min(start + MAX_FALLBACK_CHUNK_SIZE, paragraph.length());
            String piece = paragraph.substring(start, end).trim();
            if (!piece.isBlank()) {
                chunks.add(new RagKnowledgeService.ChunkInput(chunks.size(), piece, pageNo, null));
            }
            start = end;
        }
    }

    private void appendFallbackChunk(List<RagKnowledgeService.ChunkInput> chunks, StringBuilder buffer, Integer pageNo) {
        String value = buffer.toString().trim();
        if (!value.isBlank()) {
            chunks.add(new RagKnowledgeService.ChunkInput(chunks.size(), value, pageNo, null));
        }
        buffer.setLength(0);
    }

    private String normalizeText(String text) {
        return text == null
                ? ""
                : text.replace("\r", "")
                .replace('\u00A0', ' ')
                .replaceAll("[ \\t\\x0B\\f]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String requestId(String sourceHint) {
        String normalizedHint = sourceHint == null ? "" : sourceHint.trim().toLowerCase(Locale.ROOT);
        normalizedHint = normalizedHint.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (normalizedHint.isBlank()) {
            normalizedHint = "text";
        }
        return "chunk-" + normalizedHint + "-" + System.currentTimeMillis();
    }
}
