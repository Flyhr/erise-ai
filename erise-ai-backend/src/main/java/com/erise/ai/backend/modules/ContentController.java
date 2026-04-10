package com.erise.ai.backend.modules;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erise.ai.backend.common.api.ApiResponse;
import com.erise.ai.backend.common.api.PageResponse;
import com.erise.ai.backend.common.entity.AuditableEntity;
import com.erise.ai.backend.common.exception.BizException;
import com.erise.ai.backend.common.exception.ErrorCodes;
import com.erise.ai.backend.common.util.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.Locale;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/contents")
@RequiredArgsConstructor
public class ContentController {

    private final ContentItemService contentItemService;

    @GetMapping
    public ApiResponse<PageResponse<ContentItemSummaryView>> page(@RequestParam Long projectId,
                                                                  @RequestParam(required = false) String itemType,
                                                                  @RequestParam(required = false) String q,
                                                                  @RequestParam(defaultValue = "1") long pageNum,
                                                                  @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.success(contentItemService.page(projectId, itemType, q, pageNum, pageSize));
    }

    @PostMapping
    public ApiResponse<ContentItemDetailView> create(@Valid @RequestBody ContentItemCreateRequest request) {
        return ApiResponse.success(contentItemService.create(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<ContentItemDetailView> detail(@PathVariable Long id) {
        return ApiResponse.success(contentItemService.detail(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<ContentItemDetailView> update(@PathVariable Long id, @Valid @RequestBody ContentItemUpdateRequest request) {
        return ApiResponse.success(contentItemService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        contentItemService.delete(id);
        return ApiResponse.success("success", null);
    }
}

@Service
@RequiredArgsConstructor
class ContentItemService {

    private final ContentItemMapper contentItemMapper;
    private final ProjectService projectService;
    private final RagKnowledgeService ragKnowledgeService;
    private final AuditLogService auditLogService;

    PageResponse<ContentItemSummaryView> page(Long projectId, String itemType, String keyword, long pageNum, long pageSize) {
        projectService.requireAccessibleProject(projectId);
        String normalizedType = itemType == null || itemType.isBlank() ? null : normalizeType(itemType);
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        LambdaQueryWrapper<ContentItemEntity> wrapper = new LambdaQueryWrapper<ContentItemEntity>()
                .eq(ContentItemEntity::getProjectId, projectId)
                .eq(normalizedType != null, ContentItemEntity::getItemType, normalizedType)
                .orderByDesc(ContentItemEntity::getUpdatedAt);
        wrapper.and(normalizedKeyword != null, query -> query
                .like(ContentItemEntity::getTitle, normalizedKeyword)
                .or()
                .like(ContentItemEntity::getSummary, normalizedKeyword)
                .or()
                .like(ContentItemEntity::getPlainText, normalizedKeyword));
        Page<ContentItemEntity> page = contentItemMapper.selectPage(Page.of(pageNum, pageSize), wrapper);
        return PageResponse.of(page.getRecords().stream().map(this::toSummary).toList(), pageNum, pageSize, page.getTotal());
    }

    ContentItemDetailView create(ContentItemCreateRequest request) {
        var currentUser = SecurityUtils.currentUser();
        projectService.requireAccessibleProject(request.projectId());
        ContentItemEntity entity = new ContentItemEntity();
        entity.setOwnerUserId(currentUser.userId());
        entity.setProjectId(request.projectId());
        entity.setItemType(normalizeType(request.itemType()));
        entity.setTitle(request.title());
        entity.setSummary(request.summary());
        entity.setContentJson(defaultString(request.contentJson(), "{}"));
        entity.setPlainText(defaultString(request.plainText(), ""));
        entity.setCoverMetaJson(request.coverMetaJson());
        entity.setCreatedBy(currentUser.userId());
        entity.setUpdatedBy(currentUser.userId());
        contentItemMapper.insert(entity);
        syncKnowledge(entity);
        auditLogService.log(currentUser, "CONTENT_CREATE", entity.getItemType(), entity.getId(), request);
        return detail(entity.getId());
    }

    ContentItemDetailView detail(Long id) {
        return toDetail(requireAccessibleItem(id));
    }

    ContentItemDetailView update(Long id, ContentItemUpdateRequest request) {
        var currentUser = SecurityUtils.currentUser();
        ContentItemEntity entity = requireAccessibleItem(id);
        entity.setTitle(request.title());
        entity.setSummary(request.summary());
        entity.setContentJson(defaultString(request.contentJson(), "{}"));
        entity.setPlainText(defaultString(request.plainText(), ""));
        entity.setCoverMetaJson(request.coverMetaJson());
        entity.setUpdatedBy(currentUser.userId());
        contentItemMapper.updateById(entity);
        syncKnowledge(entity);
        auditLogService.log(currentUser, "CONTENT_SAVE", entity.getItemType(), entity.getId(), request);
        return toDetail(entity);
    }

    void delete(Long id) {
        var currentUser = SecurityUtils.currentUser();
        ContentItemEntity entity = requireAccessibleItem(id);
        contentItemMapper.deleteById(id);
        ragKnowledgeService.deleteKbSource(entity.getOwnerUserId(), entity.getProjectId(), entity.getItemType(), id);
        auditLogService.log(currentUser, "CONTENT_DELETE", entity.getItemType(), id, null);
    }

    private void syncKnowledge(ContentItemEntity entity) {
        ragKnowledgeService.replaceKbSource(
                entity.getOwnerUserId(),
                entity.getProjectId(),
                entity.getItemType(),
                entity.getId(),
                entity.getTitle(),
                ragKnowledgeService.splitText(joinText(entity.getSummary(), entity.getPlainText()), null)
        );
    }

    private ContentItemEntity requireAccessibleItem(Long id) {
        ContentItemEntity entity = contentItemMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCodes.NOT_FOUND, "Content item not found", HttpStatus.NOT_FOUND);
        }
        projectService.requireAccessibleProject(entity.getProjectId());
        return entity;
    }

    private ContentItemSummaryView toSummary(ContentItemEntity entity) {
        return new ContentItemSummaryView(
                entity.getId(),
                entity.getProjectId(),
                entity.getItemType(),
                entity.getTitle(),
                entity.getSummary(),
                entity.getUpdatedAt()
        );
    }

    private ContentItemDetailView toDetail(ContentItemEntity entity) {
        return new ContentItemDetailView(
                entity.getId(),
                entity.getProjectId(),
                entity.getItemType(),
                entity.getTitle(),
                entity.getSummary(),
                entity.getContentJson(),
                entity.getPlainText(),
                entity.getCoverMetaJson(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String normalizeType(String itemType) {
        String normalized = itemType == null ? "" : itemType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "SHEET", "BOARD", "DATA_TABLE" -> normalized;
            default -> throw new BizException(ErrorCodes.BAD_REQUEST, "Unsupported content type", HttpStatus.BAD_REQUEST);
        };
    }

    private String defaultString(String value, String defaultValue) {
        return value == null ? defaultValue : value;
    }

    private String joinText(String summary, String plainText) {
        StringBuilder builder = new StringBuilder();
        if (summary != null && !summary.isBlank()) {
            builder.append(summary.trim());
        }
        if (plainText != null && !plainText.isBlank()) {
            if (!builder.isEmpty()) {
                builder.append("\n\n");
            }
            builder.append(plainText.trim());
        }
        return builder.toString();
    }
}

interface ContentItemMapper extends BaseMapper<ContentItemEntity> {
}

@Data
@TableName("ea_content_item")
class ContentItemEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerUserId;
    private Long projectId;
    private String itemType;
    private String title;
    private String summary;
    private String contentJson;
    private String plainText;
    private String coverMetaJson;
}

record ContentItemCreateRequest(
        @NotNull Long projectId,
        @NotBlank String itemType,
        @NotBlank String title,
        String summary,
        String contentJson,
        String plainText,
        String coverMetaJson
) {
}

record ContentItemUpdateRequest(
        @NotBlank String title,
        String summary,
        @NotNull String contentJson,
        @NotNull String plainText,
        String coverMetaJson
) {
}

record ContentItemSummaryView(
        Long id,
        Long projectId,
        String itemType,
        String title,
        String summary,
        LocalDateTime updatedAt
) {
}

record ContentItemDetailView(
        Long id,
        Long projectId,
        String itemType,
        String title,
        String summary,
        String contentJson,
        String plainText,
        String coverMetaJson,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
