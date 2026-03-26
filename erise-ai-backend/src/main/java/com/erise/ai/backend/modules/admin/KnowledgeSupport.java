package com.erise.ai.backend.modules;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erise.ai.backend.common.entity.AuditableEntity;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class KnowledgeService {

    private final KnowledgeChunkMapper knowledgeChunkMapper;

    void replaceForSource(Long userId, Long projectId, String sourceType, Long sourceId, String sourceTitle, List<ChunkInput> chunks) {
        deleteForSource(projectId, sourceType, sourceId);
        for (ChunkInput chunk : chunks) {
            KnowledgeChunkEntity entity = new KnowledgeChunkEntity();
            entity.setOwnerUserId(userId);
            entity.setProjectId(projectId);
            entity.setSourceType(sourceType);
            entity.setSourceId(sourceId);
            entity.setSourceTitle(sourceTitle);
            entity.setChunkIndex(chunk.chunkIndex());
            entity.setChunkText(chunk.chunkText());
            entity.setPageNo(chunk.pageNo());
            entity.setSectionPath(chunk.sectionPath());
            entity.setIndexStatus("READY");
            entity.setCreatedBy(userId);
            entity.setUpdatedBy(userId);
            knowledgeChunkMapper.insert(entity);
        }
    }

    void deleteForSource(Long projectId, String sourceType, Long sourceId) {
        knowledgeChunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunkEntity>()
                .eq(KnowledgeChunkEntity::getProjectId, projectId)
                .eq(KnowledgeChunkEntity::getSourceType, sourceType)
                .eq(KnowledgeChunkEntity::getSourceId, sourceId));
    }

    List<KnowledgeChunkEntity> queryProjectChunks(Long userId, Long projectId, String keyword, int limit) {
        LambdaQueryWrapper<KnowledgeChunkEntity> wrapper = new LambdaQueryWrapper<KnowledgeChunkEntity>()
                .eq(KnowledgeChunkEntity::getOwnerUserId, userId)
                .eq(KnowledgeChunkEntity::getProjectId, projectId)
                .like(KnowledgeChunkEntity::getChunkText, keyword)
                .last("limit " + Math.max(limit, 1));
        return knowledgeChunkMapper.selectList(wrapper);
    }

    List<ChunkInput> splitText(String text, Integer pageNo) {
        List<ChunkInput> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        int index = 0;
        String normalized = text.replace("\r", "");
        String[] parts = normalized.split("\n\n");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isBlank()) {
                chunks.add(new ChunkInput(index++, trimmed, pageNo, null));
            }
        }
        if (chunks.isEmpty()) {
            chunks.add(new ChunkInput(0, normalized, pageNo, null));
        }
        return chunks;
    }

    record ChunkInput(int chunkIndex, String chunkText, Integer pageNo, String sectionPath) {
    }
}

interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunkEntity> {
}

@Data
@TableName("ea_knowledge_chunk")
class KnowledgeChunkEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerUserId;
    private Long projectId;
    private String sourceType;
    private Long sourceId;
    private String sourceTitle;
    private Integer chunkIndex;
    private String chunkText;
    private Integer pageNo;
    private String sectionPath;
    private String embeddingRef;
    private String indexStatus;
}
