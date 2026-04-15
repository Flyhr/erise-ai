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
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
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
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    public ApiResponse<PageResponse<ProjectDetailView>> page(@RequestParam(defaultValue = "1") long pageNum,
                                                             @RequestParam(defaultValue = "10") long pageSize,
                                                             @RequestParam(required = false) String q,
                                                             @RequestParam(required = false) String status) {
        return ApiResponse.success(projectService.page(pageNum, pageSize, q, status));
    }

    @PostMapping
    public ApiResponse<ProjectDetailView> create(@Valid @RequestBody ProjectCreateRequest request) {
        return ApiResponse.success(projectService.create(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProjectDetailView> detail(@PathVariable Long id) {
        return ApiResponse.success(projectService.detail(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProjectDetailView> update(@PathVariable Long id, @Valid @RequestBody ProjectUpdateRequest request) {
        return ApiResponse.success(projectService.update(id, request));
    }

    @PostMapping("/{id}/archive")
    public ApiResponse<Void> archive(@PathVariable Long id) {
        projectService.archive(id);
        return ApiResponse.success("success", null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return ApiResponse.success("success", null);
    }
}

@Service
@RequiredArgsConstructor
class ProjectService {

    private final ProjectMapper projectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;
    private final RagKnowledgeService ragKnowledgeService;

    PageResponse<ProjectDetailView> page(long pageNum, long pageSize, String keyword, String status) {
        var currentUser = SecurityUtils.currentUser();
        String trimmedKeyword = keyword == null ? null : keyword.trim();
        String trimmedStatus = status == null ? null : status.trim();
        Page<ProjectEntity> page = projectMapper.selectPage(Page.of(pageNum, pageSize),
                new LambdaQueryWrapper<ProjectEntity>()
                        .eq(!currentUser.isAdmin(), ProjectEntity::getOwnerUserId, currentUser.userId())
                        .and(trimmedKeyword != null && !trimmedKeyword.isEmpty(), wrapper -> wrapper
                                .like(ProjectEntity::getName, trimmedKeyword)
                                .or()
                                .like(ProjectEntity::getDescription, trimmedKeyword))
                        .eq(trimmedStatus != null && !trimmedStatus.isEmpty(), ProjectEntity::getProjectStatus, trimmedStatus)
                        .orderByDesc(ProjectEntity::getUpdatedAt));
        var records = page.getRecords().stream().map(this::toView).toList();
        return PageResponse.of(records, pageNum, pageSize, page.getTotal());
    }

    ProjectDetailView create(ProjectCreateRequest request) {
        var currentUser = SecurityUtils.currentUser();
        ProjectEntity entity = new ProjectEntity();
        entity.setOwnerUserId(currentUser.userId());
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setProjectStatus("ACTIVE");
        entity.setArchived(0);
        entity.setCreatedBy(currentUser.userId());
        entity.setUpdatedBy(currentUser.userId());
        projectMapper.insert(entity);
        auditLogService.log(currentUser, "PROJECT_CREATE", "PROJECT", entity.getId(), request);
        return toView(entity);
    }

    ProjectDetailView detail(Long id) {
        return toView(requireAccessibleProject(id));
    }

    ProjectDetailView internalDetail(Long id) {
        ProjectEntity entity = projectMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCodes.NOT_FOUND, "Project not found", HttpStatus.NOT_FOUND);
        }
        return toView(entity);
    }

    ProjectDetailView update(Long id, ProjectUpdateRequest request) {
        var currentUser = SecurityUtils.currentUser();
        ProjectEntity entity = requireAccessibleProject(id);
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setUpdatedBy(currentUser.userId());
        projectMapper.updateById(entity);
        auditLogService.log(currentUser, "PROJECT_UPDATE", "PROJECT", id, request);
        return toView(entity);
    }

    void archive(Long id) {
        var currentUser = SecurityUtils.currentUser();
        ProjectEntity entity = requireAccessibleProject(id);
        entity.setArchived(1);
        entity.setProjectStatus("ARCHIVED");
        entity.setUpdatedBy(currentUser.userId());
        projectMapper.updateById(entity);
        auditLogService.log(currentUser, "PROJECT_ARCHIVE", "PROJECT", id, null);
    }

    void delete(Long id) {
        var currentUser = SecurityUtils.currentUser();
        ProjectEntity entity = requireAccessibleProject(id);
        try {
            ragKnowledgeService.deleteProjectSources(currentUser.userId(), entity.getId());
        } catch (RuntimeException ignored) {
        }
        entity.setUpdatedBy(currentUser.userId());
        projectMapper.deleteById(entity.getId());
        auditLogService.log(currentUser, "PROJECT_DELETE", "PROJECT", id, null);
    }

    ProjectEntity requireAccessibleProject(Long projectId) {
        var currentUser = SecurityUtils.currentUser();
        ProjectEntity entity = projectMapper.selectById(projectId);
        if (entity == null || (!currentUser.isAdmin() && !currentUser.userId().equals(entity.getOwnerUserId()))) {
            throw new BizException(ErrorCodes.NOT_FOUND, "Project not found", HttpStatus.NOT_FOUND);
        }
        return entity;
    }

    ProjectEntity requireAccessibleProject(Long projectId, Long actorUserId) {
        ProjectEntity entity = projectMapper.selectById(projectId);
        if (entity == null || actorUserId == null || !actorUserId.equals(entity.getOwnerUserId())) {
            throw new BizException(ErrorCodes.FORBIDDEN, "No permission", HttpStatus.FORBIDDEN);
        }
        return entity;
    }

    ProjectDetailView toView(ProjectEntity entity) {
        Long fileCount = jdbcTemplate.queryForObject(
                "select count(*) from ea_file where project_id = ? and deleted = 0",
                Long.class,
                entity.getId()
        );
        Long documentCount = jdbcTemplate.queryForObject(
                "select count(*) from ea_document where project_id = ? and deleted = 0",
                Long.class,
                entity.getId()
        );
        return new ProjectDetailView(
                entity.getId(),
                entity.getOwnerUserId(),
                entity.getName(),
                entity.getDescription(),
                entity.getProjectStatus(),
                entity.getArchived(),
                fileCount == null ? 0 : fileCount,
                documentCount == null ? 0 : documentCount,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}

interface ProjectMapper extends BaseMapper<ProjectEntity> {
}

@Data
@TableName("ea_project")
class ProjectEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerUserId;
    private String name;
    private String description;
    private String projectStatus;
    private Integer archived;
}

record ProjectCreateRequest(@NotBlank String name, String description) {
}

record ProjectUpdateRequest(@NotBlank String name, String description) {
}

record ProjectDetailView(
        Long id,
        Long ownerUserId,
        String name,
        String description,
        String projectStatus,
        Integer archived,
        long fileCount,
        long documentCount,
        java.time.LocalDateTime createdAt,
        java.time.LocalDateTime updatedAt
) {
}
