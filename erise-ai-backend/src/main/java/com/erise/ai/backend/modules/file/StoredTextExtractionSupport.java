package com.erise.ai.backend.modules;

import com.erise.ai.backend.integration.ai.CloudAiClient;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class StoredTextExtractionSupport {

    private final CloudAiClient cloudAiClient;

    StoredTextExtractionSupport(CloudAiClient cloudAiClient) {
        this.cloudAiClient = cloudAiClient;
    }

    String extractPlainText(Long ownerUserId, String fileName, String fileExt, InputStream stream) throws IOException {
        return extractStructuredContent(ownerUserId, fileName, fileExt, stream).plainText();
    }

    List<RagKnowledgeService.ChunkInput> extractChunks(Long ownerUserId, String fileName, String fileExt, InputStream stream) throws IOException {
        return extractStructuredContent(ownerUserId, fileName, fileExt, stream).chunks();
    }

    StructuredExtractionResult extractStructuredContent(Long ownerUserId, String fileName, String fileExt, InputStream stream) throws IOException {
        byte[] bytes = stream.readAllBytes();
        if (bytes.length == 0) {
            return StructuredExtractionResult.empty();
        }
        CloudAiClient.FileExtractResponse response = cloudAiClient.extractFileText(
                ownerUserId,
                resolveFileName(fileName, fileExt),
                normalizeExtension(fileExt, fileName),
                bytes,
                UUID.randomUUID().toString()
        );
        return toStructuredResult(response);
    }

    private String normalizeExtension(String fileExt, String fileName) {
        String explicitExtension = fileExt == null ? "" : fileExt.trim().toLowerCase(Locale.ROOT);
        if (!explicitExtension.isBlank()) {
            return explicitExtension.startsWith(".") ? explicitExtension.substring(1) : explicitExtension;
        }
        if (fileName == null || fileName.isBlank() || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).trim().toLowerCase(Locale.ROOT);
    }

    private String resolveFileName(String fileName, String fileExt) {
        if (fileName != null && !fileName.isBlank()) {
            return fileName;
        }
        String normalizedExtension = normalizeExtension(fileExt, null);
        return normalizedExtension.isBlank() ? "upload" : "upload." + normalizedExtension;
    }

    private StructuredExtractionResult toStructuredResult(CloudAiClient.FileExtractResponse response) {
        if (response == null) {
            return StructuredExtractionResult.empty();
        }
        List<RagKnowledgeService.ChunkInput> chunks = new ArrayList<>();
        if (response.chunks() != null) {
            int nextChunkIndex = 0;
            for (CloudAiClient.FileExtractChunkResponse item : response.chunks()) {
                if (item == null || item.chunkText() == null || item.chunkText().isBlank()) {
                    continue;
                }
                Integer chunkNum = item.chunkNum() == null ? nextChunkIndex : item.chunkNum();
                chunks.add(new RagKnowledgeService.ChunkInput(
                        chunkNum,
                        item.chunkText().trim(),
                        item.pageNo(),
                        item.sectionPath()
                ));
                nextChunkIndex = chunkNum + 1;
            }
        }
        String plainText = response.plainText() == null ? "" : response.plainText().trim();
        return new StructuredExtractionResult(plainText, chunks);
    }

    record StructuredExtractionResult(String plainText, List<RagKnowledgeService.ChunkInput> chunks) {

        private static StructuredExtractionResult empty() {
            return new StructuredExtractionResult("", List.of());
        }
    }
}
