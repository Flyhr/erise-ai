package com.erise.ai.backend.modules;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erise.ai.backend.common.entity.AuditableEntity;
import com.erise.ai.backend.integration.ai.CloudAiClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class RagKnowledgeService {

    private static final String SCOPE_KB = "KB";
    private static final String SCOPE_TEMP = "TEMP";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_READY = "READY";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_DELETED = "DELETED";
    private static final String STATUS_NEEDS_REPAIR = "NEEDS_REPAIR";
    private static final String TASK_TYPE_INDEX = "INDEX";
    private static final String TASK_TYPE_DELETE = "DELETE";
    private static final int TARGET_CHUNK_SIZE = 420;
    private static final int MAX_CHUNK_SIZE = 500;
    private static final int OVERLAP_SIZE = 60;
    private static final Pattern PARAGRAPH_SPLITTER = Pattern.compile("\\n\\s*\\n+");
    private static final Pattern LINE_SPLITTER = Pattern.compile("\\n+");
    private static final Pattern SENTENCE_SPLITTER = Pattern.compile("(?<=[。！？!?；;])");
    private static final Pattern CLAUSE_SPLITTER = Pattern.compile("(?<=[，,:：])");
    private static final Pattern WORD_SPLITTER = Pattern.compile("\\s+");

    private final RagSourceMapper ragSourceMapper;
    private final RagChunkMapper ragChunkMapper;
    private final RagTaskMapper ragTaskMapper;
    private final CloudAiClient cloudAiClient;
    private final ObjectMapper objectMapper;

    @Transactional
    void replaceKbSource(Long ownerUserId,
                         Long projectId,
                         String sourceType,
                         Long sourceId,
                         String sourceTitle,
                         List<ChunkInput> chunks) {
        replaceSource(SCOPE_KB, ownerUserId, projectId, 0L, sourceType, sourceId, sourceTitle, chunks);
    }

    @Transactional
    void replaceTempSource(Long ownerUserId,
                           Long projectId,
                           Long sessionId,
                           Long sourceId,
                           String sourceTitle,
                           List<ChunkInput> chunks) {
        replaceSource(SCOPE_TEMP, ownerUserId, projectId, sessionId, "TEMP_FILE", sourceId, sourceTitle, chunks);
    }

    @Transactional
    void deleteKbSource(Long ownerUserId, Long projectId, String sourceType, Long sourceId) {
        deleteSource(SCOPE_KB, ownerUserId, projectId, 0L, sourceType, sourceId);
    }

    @Transactional
    void deleteTempSource(Long ownerUserId, Long sessionId, Long sourceId) {
        deleteSource(SCOPE_TEMP, ownerUserId, null, sessionId, "TEMP_FILE", sourceId);
    }

    void deleteProjectSources(Long ownerUserId, Long projectId) {
        ragSourceMapper.selectList(new LambdaQueryWrapper<RagSourceEntity>()
                        .eq(RagSourceEntity::getOwnerUserId, ownerUserId)
                        .eq(RagSourceEntity::getProjectId, projectId))
                .forEach(source -> deleteSource(
                        source.getScopeType(),
                        ownerUserId,
                        projectId,
                        normalizeSessionId(source.getSessionId()),
                        source.getSourceType(),
                        source.getSourceId()
                ));
    }

    List<ChunkInput> splitText(String text, Integer pageNo) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalized = normalizeSourceText(text);
        if (normalized.isBlank()) {
            return List.of();
        }
        List<SemanticBlock> blocks = mergeHeadingBlocks(buildSemanticBlocks(normalized));
        if (blocks.isEmpty()) {
            return List.of(new ChunkInput(0, normalized, pageNo, null));
        }
        return buildChunks(blocks, pageNo);
    }

    private List<ChunkInput> normalizeChunks(List<ChunkInput> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        List<ChunkInput> normalized = new ArrayList<>();
        int nextChunkIndex = 0;
        for (ChunkInput chunk : chunks) {
            if (chunk == null || chunk.chunkText() == null || chunk.chunkText().isBlank()) {
                continue;
            }
            normalized.add(new ChunkInput(nextChunkIndex++, chunk.chunkText().trim(), chunk.pageNo(), chunk.sectionPath()));
        }
        return normalized;
    }

    private void replaceSource(String scopeType,
                               Long ownerUserId,
                               Long projectId,
                               Long sessionId,
                               String sourceType,
                               Long sourceId,
                               String sourceTitle,
                               List<ChunkInput> chunks) {
        Long normalizedSessionId = normalizeSessionId(sessionId);
        List<ChunkInput> normalizedChunks = normalizeChunks(chunks);
        RagSourceEntity source = upsertSource(scopeType, ownerUserId, projectId, normalizedSessionId, sourceType, sourceId, sourceTitle);
        RagTaskEntity task = createTask(source, ownerUserId, projectId, sessionId, sourceType, sourceId, TASK_TYPE_INDEX);
        try {
            CloudAiClient.RagIndexUpsertResponse response = cloudAiClient.upsertRagIndex(
                    ownerUserId,
                    new CloudAiClient.RagIndexUpsertRequest(
                            ownerUserId,
                            scopeType,
                            projectId,
                            normalizedSessionId,
                            sourceType,
                            sourceId,
                            sourceTitle,
                            normalizedChunks.stream()
                                    .map(item -> new CloudAiClient.RagChunkRequest(
                                            item.chunkIndex(),
                                            item.chunkText(),
                                            item.pageNo(),
                                            item.sectionPath()
                                    ))
                                    .toList(),
                            LocalDateTime.now()
                    ),
                    requestId(scopeType, sourceType, sourceId)
            );

            ragChunkMapper.delete(new LambdaQueryWrapper<RagChunkEntity>().eq(RagChunkEntity::getRagSourceId, source.getId()));
            String collectionName = response == null || response.collectionName() == null
                    ? defaultCollection(scopeType)
                    : response.collectionName();
            for (ChunkInput chunk : normalizedChunks) {
                RagChunkEntity entity = new RagChunkEntity();
                entity.setRagSourceId(source.getId());
                entity.setOwnerUserId(ownerUserId);
                entity.setProjectId(projectId);
                entity.setSessionId(normalizedSessionId);
                entity.setSourceType(sourceType);
                entity.setSourceId(sourceId);
                entity.setChunkNum(chunk.chunkIndex());
                entity.setChunkText(chunk.chunkText());
                entity.setPageNo(chunk.pageNo());
                entity.setSectionPath(chunk.sectionPath());
                entity.setVectorCollection(collectionName);
                entity.setVectorPointId(pointId(scopeType, sourceType, sourceId, normalizedSessionId, chunk.chunkIndex()));
                entity.setEmbeddingModelCode(response == null ? null : response.embeddingModelCode());
                entity.setEmbeddingVersion(response == null ? null : response.embeddingVersion());
                entity.setEmbeddingDimension(response == null ? null : response.embeddingDimension());
                entity.setCreatedBy(ownerUserId);
                entity.setUpdatedBy(ownerUserId);
                ragChunkMapper.insert(entity);
            }

            source.setStatus(STATUS_READY);
            source.setChunkCount(normalizedChunks.size());
            source.setLastError(null);
            source.setLastIndexedAt(LocalDateTime.now());
            source.setContentHash(contentHash(normalizedChunks));
            source.setEmbeddingModelCode(response == null ? null : response.embeddingModelCode());
            source.setEmbeddingVersion(response == null ? null : response.embeddingVersion());
            source.setEmbeddingDimension(response == null ? null : response.embeddingDimension());
            source.setUpdatedBy(ownerUserId);
            ragSourceMapper.updateById(source);

            task.setTaskStatus(STATUS_READY);
            task.setResultJson(toJson(response));
            task.setLastError(null);
            task.setUpdatedBy(ownerUserId);
            ragTaskMapper.updateById(task);
        } catch (Exception exception) {
            ragChunkMapper.delete(new LambdaQueryWrapper<RagChunkEntity>().eq(RagChunkEntity::getRagSourceId, source.getId()));
            source.setStatus(STATUS_FAILED);
            source.setChunkCount(0);
            source.setLastError(trimError(exception.getMessage()));
            source.setUpdatedBy(ownerUserId);
            ragSourceMapper.updateById(source);

            task.setTaskStatus(STATUS_FAILED);
            task.setLastError(trimError(exception.getMessage()));
            task.setUpdatedBy(ownerUserId);
            ragTaskMapper.updateById(task);
            throw exception;
        }
    }

    private void deleteSource(String scopeType,
                              Long ownerUserId,
                              Long projectId,
                              Long sessionId,
                              String sourceType,
                              Long sourceId) {
        RagSourceEntity source = findSource(scopeType, ownerUserId, sourceType, sourceId, sessionId);
        if (source == null) {
            return;
        }
        RagTaskEntity task = createTask(source, ownerUserId, projectId, sessionId, sourceType, sourceId, TASK_TYPE_DELETE);
        try {
            CloudAiClient.RagIndexDeleteResponse response = cloudAiClient.deleteRagIndex(
                    ownerUserId,
                    new CloudAiClient.RagIndexDeleteRequest(
                            ownerUserId,
                            scopeType,
                            projectId,
                            sessionId,
                            sourceType,
                            sourceId
                    ),
                    requestId(scopeType, sourceType, sourceId)
            );
            ragChunkMapper.delete(new LambdaQueryWrapper<RagChunkEntity>().eq(RagChunkEntity::getRagSourceId, source.getId()));
            source.setStatus(STATUS_DELETED);
            source.setChunkCount(0);
            source.setLastError(null);
            source.setUpdatedBy(ownerUserId);
            ragSourceMapper.updateById(source);

            task.setTaskStatus(STATUS_READY);
            task.setResultJson(toJson(response));
            task.setLastError(null);
            task.setUpdatedBy(ownerUserId);
            ragTaskMapper.updateById(task);
        } catch (Exception exception) {
            source.setStatus(STATUS_NEEDS_REPAIR);
            source.setLastError(trimError(exception.getMessage()));
            source.setUpdatedBy(ownerUserId);
            ragSourceMapper.updateById(source);

            task.setTaskStatus(STATUS_NEEDS_REPAIR);
            task.setLastError(trimError(exception.getMessage()));
            task.setUpdatedBy(ownerUserId);
            ragTaskMapper.updateById(task);
            throw exception;
        }
    }

    private RagSourceEntity upsertSource(String scopeType,
                                         Long ownerUserId,
                                         Long projectId,
                                         Long sessionId,
                                         String sourceType,
                                         Long sourceId,
                                         String sourceTitle) {
        Long normalizedSessionId = normalizeSessionId(sessionId);
        RagSourceEntity source = findSource(scopeType, ownerUserId, sourceType, sourceId, normalizedSessionId);
        if (source == null) {
            source = new RagSourceEntity();
            source.setOwnerUserId(ownerUserId);
            source.setScopeType(scopeType);
            source.setSourceType(sourceType);
            source.setSourceId(sourceId);
            source.setSessionId(normalizedSessionId);
            source.setCreatedBy(ownerUserId);
        }
        source.setProjectId(projectId);
        source.setSourceTitle(sourceTitle);
        source.setStatus(STATUS_PROCESSING);
        source.setLastError(null);
        source.setUpdatedBy(ownerUserId);
        if (source.getId() == null) {
            ragSourceMapper.insert(source);
        } else {
            ragSourceMapper.updateById(source);
        }
        return source;
    }

    private RagTaskEntity createTask(RagSourceEntity source,
                                     Long ownerUserId,
                                     Long projectId,
                                     Long sessionId,
                                     String sourceType,
                                     Long sourceId,
                                     String taskType) {
        RagTaskEntity task = new RagTaskEntity();
        task.setOwnerUserId(ownerUserId);
        task.setProjectId(projectId);
        task.setSessionId(normalizeSessionId(sessionId));
        task.setRagSourceId(source.getId());
        task.setTaskType(taskType);
        task.setTaskStatus(STATUS_PROCESSING);
        task.setScopeType(source.getScopeType());
        task.setSourceType(sourceType);
        task.setSourceId(sourceId);
        task.setRetryCount(0);
        task.setPayloadJson(toJson(source));
        task.setCreatedBy(ownerUserId);
        task.setUpdatedBy(ownerUserId);
        ragTaskMapper.insert(task);
        return task;
    }

    private RagSourceEntity findSource(String scopeType, Long ownerUserId, String sourceType, Long sourceId, Long sessionId) {
        LambdaQueryWrapper<RagSourceEntity> wrapper = new LambdaQueryWrapper<RagSourceEntity>()
                .eq(RagSourceEntity::getOwnerUserId, ownerUserId)
                .eq(RagSourceEntity::getScopeType, scopeType)
                .eq(RagSourceEntity::getSourceType, sourceType)
                .eq(RagSourceEntity::getSourceId, sourceId);
        wrapper.eq(RagSourceEntity::getSessionId, normalizeSessionId(sessionId));
        return ragSourceMapper.selectOne(wrapper.last("limit 1"));
    }

    private Long normalizeSessionId(Long sessionId) {
        return sessionId == null ? 0L : sessionId;
    }

    private String normalizeSourceText(String text) {
        return text
                .replace("\r", "")
                .replace('\u00A0', ' ')
                .replaceAll("[ \\t\\x0B\\f]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private List<SemanticBlock> buildSemanticBlocks(String normalized) {
        List<SemanticBlock> blocks = new ArrayList<>();
        String currentSection = null;
        for (String rawParagraph : PARAGRAPH_SPLITTER.split(normalized)) {
            String paragraph = rawParagraph == null ? "" : rawParagraph.trim();
            if (paragraph.isBlank()) {
                continue;
            }
            if (isLikelyHeading(paragraph)) {
                currentSection = paragraph;
                blocks.add(new SemanticBlock(paragraph, currentSection));
                continue;
            }
            splitRecursively(paragraph, currentSection, 0, blocks);
        }
        if (blocks.isEmpty() && !normalized.isBlank()) {
            blocks.add(new SemanticBlock(normalized, null));
        }
        return blocks;
    }

    private List<SemanticBlock> mergeHeadingBlocks(List<SemanticBlock> blocks) {
        if (blocks.isEmpty()) {
            return blocks;
        }
        List<SemanticBlock> merged = new ArrayList<>();
        for (int index = 0; index < blocks.size(); index++) {
            SemanticBlock current = blocks.get(index);
            if (isLikelyHeading(current.text())
                    && current.text().length() <= 80
                    && index + 1 < blocks.size()) {
                SemanticBlock next = blocks.get(index + 1);
                if ((current.sectionPath() == null && next.sectionPath() == null)
                        || (current.sectionPath() != null && current.sectionPath().equals(next.sectionPath()))) {
                    merged.add(new SemanticBlock(current.text() + "\n" + next.text(), next.sectionPath()));
                    index++;
                    continue;
                }
            }
            merged.add(current);
        }
        return merged;
    }

    private void splitRecursively(String text, String sectionPath, int level, List<SemanticBlock> output) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.isBlank()) {
            return;
        }
        if (normalized.length() <= MAX_CHUNK_SIZE) {
            output.add(new SemanticBlock(normalized, sectionPath));
            return;
        }
        SplitPlan plan = splitPlan(level);
        if (plan == null) {
            hardSplit(normalized, sectionPath, output);
            return;
        }
        List<String> parts = splitWithPattern(normalized, plan.pattern());
        if (parts.size() <= 1) {
            splitRecursively(normalized, sectionPath, level + 1, output);
            return;
        }
        StringBuilder buffer = new StringBuilder();
        for (String part : parts) {
            if (part.length() > MAX_CHUNK_SIZE) {
                flushSemanticBuffer(output, buffer, sectionPath);
                splitRecursively(part, sectionPath, level + 1, output);
                continue;
            }
            if (buffer.length() == 0) {
                buffer.append(part);
                continue;
            }
            if (buffer.length() + plan.joiner().length() + part.length() <= MAX_CHUNK_SIZE) {
                buffer.append(plan.joiner()).append(part);
                continue;
            }
            output.add(new SemanticBlock(buffer.toString().trim(), sectionPath));
            buffer.setLength(0);
            buffer.append(part);
        }
        flushSemanticBuffer(output, buffer, sectionPath);
    }

    private void flushSemanticBuffer(List<SemanticBlock> output, StringBuilder buffer, String sectionPath) {
        String value = buffer.toString().trim();
        if (!value.isBlank()) {
            output.add(new SemanticBlock(value, sectionPath));
        }
        buffer.setLength(0);
    }

    private List<String> splitWithPattern(String value, Pattern pattern) {
        List<String> parts = new ArrayList<>();
        for (String rawPart : pattern.split(value)) {
            String trimmed = rawPart == null ? "" : rawPart.trim();
            if (!trimmed.isBlank()) {
                parts.add(trimmed);
            }
        }
        return parts;
    }

    private SplitPlan splitPlan(int level) {
        return switch (level) {
            case 0 -> new SplitPlan(PARAGRAPH_SPLITTER, "\n\n");
            case 1 -> new SplitPlan(LINE_SPLITTER, "\n");
            case 2 -> new SplitPlan(SENTENCE_SPLITTER, " ");
            case 3 -> new SplitPlan(CLAUSE_SPLITTER, " ");
            case 4 -> new SplitPlan(WORD_SPLITTER, " ");
            default -> null;
        };
    }

    private void hardSplit(String text, String sectionPath, List<SemanticBlock> output) {
        String remaining = text;
        while (!remaining.isBlank()) {
            if (remaining.length() <= MAX_CHUNK_SIZE) {
                output.add(new SemanticBlock(remaining, sectionPath));
                return;
            }
            int splitPosition = findSplitPosition(remaining);
            String piece = remaining.substring(0, splitPosition).trim();
            if (piece.isBlank()) {
                piece = remaining.substring(0, Math.min(MAX_CHUNK_SIZE, remaining.length())).trim();
                splitPosition = Math.min(MAX_CHUNK_SIZE, remaining.length());
            }
            output.add(new SemanticBlock(piece, sectionPath));
            remaining = remaining.substring(splitPosition).trim();
        }
    }

    private List<ChunkInput> buildChunks(List<SemanticBlock> blocks, Integer pageNo) {
        List<ChunkInput> chunks = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        String currentSection = null;
        int chunkIndex = 0;
        for (SemanticBlock block : blocks) {
            String text = block.text() == null ? "" : block.text().trim();
            if (text.isBlank()) {
                continue;
            }
            if (buffer.length() == 0) {
                buffer.append(text);
                currentSection = block.sectionPath();
                continue;
            }
            String candidate = buffer + "\n\n" + text;
            if (candidate.length() <= MAX_CHUNK_SIZE
                    && (buffer.length() < TARGET_CHUNK_SIZE || text.length() < TARGET_CHUNK_SIZE / 2)) {
                buffer.append("\n\n").append(text);
                currentSection = block.sectionPath() != null ? block.sectionPath() : currentSection;
                continue;
            }
            chunkIndex = appendChunk(chunks, chunkIndex, buffer.toString(), pageNo, currentSection);
            String overlap = overlapTail(buffer.toString());
            buffer.setLength(0);
            if (!overlap.isBlank()) {
                buffer.append(overlap);
            }
            if (buffer.length() > 0) {
                buffer.append("\n");
            }
            buffer.append(text);
            currentSection = block.sectionPath() != null ? block.sectionPath() : currentSection;
            while (buffer.length() > MAX_CHUNK_SIZE) {
                int splitPosition = findSplitPosition(buffer.toString());
                String piece = buffer.substring(0, splitPosition).trim();
                chunkIndex = appendChunk(chunks, chunkIndex, piece, pageNo, currentSection);
                String overlapSeed = overlapTail(piece);
                String remainder = buffer.substring(splitPosition).trim();
                buffer.setLength(0);
                if (!overlapSeed.isBlank()) {
                    buffer.append(overlapSeed);
                }
                if (!remainder.isBlank()) {
                    if (buffer.length() > 0) {
                        buffer.append("\n");
                    }
                    buffer.append(remainder);
                }
            }
        }
        appendChunk(chunks, chunkIndex, buffer.toString(), pageNo, currentSection);
        return chunks;
    }

    private int appendChunk(List<ChunkInput> chunks,
                            int chunkIndex,
                            String rawText,
                            Integer pageNo,
                            String sectionPath) {
        String finalText = rawText == null ? "" : rawText.trim();
        if (finalText.isBlank()) {
            return chunkIndex;
        }
        if (!chunks.isEmpty() && chunks.get(chunks.size() - 1).chunkText().equals(finalText)) {
            return chunkIndex;
        }
        chunks.add(new ChunkInput(chunkIndex, finalText, pageNo, sectionPath));
        return chunkIndex + 1;
    }

    private int findSplitPosition(String value) {
        int upperBound = Math.min(MAX_CHUNK_SIZE, value.length());
        int lowerBound = Math.min(TARGET_CHUNK_SIZE, upperBound);
        for (int index = upperBound; index >= lowerBound; index--) {
            char current = value.charAt(index - 1);
            if (isBoundaryCharacter(current)) {
                return index;
            }
        }
        return upperBound;
    }

    private String overlapTail(String text) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.isBlank()) {
            return "";
        }
        if (normalized.length() <= OVERLAP_SIZE) {
            return normalized;
        }
        int start = normalized.length() - OVERLAP_SIZE;
        while (start > 0 && !isBoundaryCharacter(normalized.charAt(start - 1))) {
            start--;
        }
        return normalized.substring(start).trim();
    }

    private boolean isBoundaryCharacter(char value) {
        return Character.isWhitespace(value)
                || "。！？!?；;：:,，、)]}".indexOf(value) >= 0;
    }

    private boolean isLikelyHeading(String paragraph) {
        String normalized = paragraph == null ? "" : paragraph.trim();
        if (normalized.isBlank() || normalized.length() > 80) {
            return false;
        }
        if (normalized.matches("^(#{1,6}\\s+.+|第[0-9一二三四五六七八九十百]+[章节篇部卷].*|[0-9一二三四五六七八九十]+[.、)）]\\s*.+)$")) {
            return true;
        }
        return !normalized.matches(".*[。！？!?；;].*") && normalized.split("\\s+").length <= 8;
    }

    private String contentHash(List<ChunkInput> chunks) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            String joined = chunks == null ? "" : chunks.stream()
                    .map(ChunkInput::chunkText)
                    .reduce("", (left, right) -> left + "\n" + right);
            byte[] encoded = digest.digest(joined.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : encoded) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception exception) {
            return null;
        }
    }

    private String pointId(String scopeType, String sourceType, Long sourceId, Long sessionId, Integer chunkIndex) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            String raw = "%s:%s:%s:%s:%s".formatted(scopeType, sourceType, sourceId, sessionId == null ? 0 : sessionId, chunkIndex);
            byte[] encoded = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : encoded) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception exception) {
            return Long.toHexString(System.nanoTime());
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private String trimError(String message) {
        if (message == null || message.isBlank()) {
            return "Unknown error";
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    private String requestId(String scopeType, String sourceType, Long sourceId) {
        return "rag-" + scopeType.toLowerCase() + "-" + sourceType.toLowerCase() + "-" + sourceId + "-" + System.currentTimeMillis();
    }

    private String defaultCollection(String scopeType) {
        return SCOPE_TEMP.equalsIgnoreCase(scopeType) ? "temp_chunks" : "kb_chunks";
    }

    record ChunkInput(int chunkIndex, String chunkText, Integer pageNo, String sectionPath) {
    }

    private record SemanticBlock(String text, String sectionPath) {
    }

    private record SplitPlan(Pattern pattern, String joiner) {
    }
}

interface RagSourceMapper extends BaseMapper<RagSourceEntity> {
}

interface RagChunkMapper extends BaseMapper<RagChunkEntity> {
}

interface RagTaskMapper extends BaseMapper<RagTaskEntity> {
}

@Data
@TableName("ea_rag_source")
class RagSourceEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerUserId;
    private Long projectId;
    private Long sessionId;
    private String scopeType;
    private String sourceType;
    private Long sourceId;
    private String sourceTitle;
    private String status;
    private String contentHash;
    private Integer chunkCount;
    private LocalDateTime lastIndexedAt;
    private String lastError;
    private String embeddingModelCode;
    private String embeddingVersion;
    private Integer embeddingDimension;
}

@Data
@TableName("ea_rag_chunk")
class RagChunkEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ragSourceId;
    private Long ownerUserId;
    private Long projectId;
    private Long sessionId;
    private String sourceType;
    private Long sourceId;
    private Integer chunkNum;
    private String chunkText;
    private Integer pageNo;
    private String sectionPath;
    private String vectorCollection;
    private String vectorPointId;
    private String embeddingModelCode;
    private String embeddingVersion;
    private Integer embeddingDimension;
}

@Data
@TableName("ea_rag_task")
class RagTaskEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerUserId;
    private Long projectId;
    private Long sessionId;
    private Long ragSourceId;
    private String taskType;
    private String taskStatus;
    private String scopeType;
    private String sourceType;
    private Long sourceId;
    private Integer retryCount;
    private String payloadJson;
    private String resultJson;
    private String lastError;
}
