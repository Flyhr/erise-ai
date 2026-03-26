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
import com.erise.ai.backend.common.util.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/overview")
    public ApiResponse<AdminOverviewView> overview() {
        return ApiResponse.success(adminService.overview());
    }

    @GetMapping("/users")
    public ApiResponse<PageResponse<AdminUserView>> users(@RequestParam(defaultValue = "1") long pageNum,
                                                          @RequestParam(defaultValue = "10") long pageSize) {
        return ApiResponse.success(adminService.users(pageNum, pageSize));
    }

    @PostMapping("/users/{id}/status")
    public ApiResponse<Void> changeStatus(@PathVariable Long id, @Valid @RequestBody AdminUserStatusRequest request) {
        adminService.changeUserStatus(id, request.status());
        return ApiResponse.success("success", null);
    }

    @GetMapping("/tasks")
    public ApiResponse<PageResponse<AdminTaskView>> tasks(@RequestParam(defaultValue = "1") long pageNum,
                                                          @RequestParam(defaultValue = "10") long pageSize) {
        return ApiResponse.success(adminService.tasks(pageNum, pageSize));
    }

    @GetMapping("/audit-logs")
    public ApiResponse<PageResponse<AdminAuditLogView>> auditLogs(@RequestParam(defaultValue = "1") long pageNum,
                                                                  @RequestParam(defaultValue = "10") long pageSize) {
        return ApiResponse.success(adminService.auditLogs(pageNum, pageSize));
    }

    @GetMapping("/ai/models")
    public ApiResponse<List<ModelConfigView>> aiModels() {
        return ApiResponse.success(adminService.aiModels());
    }
}

@Service
@RequiredArgsConstructor
class AdminService {

    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final FileParseTaskMapper fileParseTaskMapper;
    private final AuditLogMapper auditLogMapper;
    private final ModelConfigMapper modelConfigMapper;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;

    AdminOverviewView overview() {
        long userCount = count("ea_user");
        long projectCount = count("ea_project");
        long fileCount = count("ea_file");
        long documentCount = count("ea_document");
        return new AdminOverviewView(userCount, projectCount, fileCount, documentCount);
    }

    PageResponse<AdminUserView> users(long pageNum, long pageSize) {
        Page<UserEntity> page = userMapper.selectPage(Page.of(pageNum, pageSize),
                new LambdaQueryWrapper<UserEntity>().orderByDesc(UserEntity::getCreatedAt));
        List<AdminUserView> records = page.getRecords().stream().map(user -> {
            UserProfileEntity profile = userProfileMapper.selectOne(new LambdaQueryWrapper<UserProfileEntity>()
                    .eq(UserProfileEntity::getUserId, user.getId())
                    .last("limit 1"));
            return new AdminUserView(user.getId(), user.getUsername(),
                    profile == null ? user.getUsername() : profile.getDisplayName(),
                    user.getEmail(), user.getRoleCode(), user.getStatus(), user.getEnabled(), user.getCreatedAt());
        }).toList();
        return PageResponse.of(records, pageNum, pageSize, page.getTotal());
    }

    void changeUserStatus(Long userId, String status) {
        var currentUser = SecurityUtils.currentUser();
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }
        user.setStatus(status);
        user.setEnabled("ACTIVE".equalsIgnoreCase(status) ? 1 : 0);
        user.setUpdatedBy(currentUser.userId());
        userMapper.updateById(user);
        auditLogService.log(currentUser, "ADMIN_USER_STATUS", "USER", userId, status);
    }

    PageResponse<AdminTaskView> tasks(long pageNum, long pageSize) {
        Page<FileParseTaskEntity> page = fileParseTaskMapper.selectPage(Page.of(pageNum, pageSize),
                new LambdaQueryWrapper<FileParseTaskEntity>().orderByDesc(FileParseTaskEntity::getCreatedAt));
        List<AdminTaskView> records = page.getRecords().stream()
                .map(task -> new AdminTaskView(task.getId(), "FILE_PARSE", task.getTaskStatus(),
                        task.getRetryCount(), task.getLastError(), task.getCreatedAt()))
                .toList();
        return PageResponse.of(records, pageNum, pageSize, page.getTotal());
    }

    PageResponse<AdminAuditLogView> auditLogs(long pageNum, long pageSize) {
        Page<AuditLogEntity> page = auditLogMapper.selectPage(Page.of(pageNum, pageSize),
                new LambdaQueryWrapper<AuditLogEntity>().orderByDesc(AuditLogEntity::getCreatedAt));
        List<AdminAuditLogView> records = page.getRecords().stream()
                .map(log -> new AdminAuditLogView(log.getId(), log.getOperatorUsername(), log.getActionCode(),
                        log.getResourceType(), log.getResourceId(), log.getDetailJson(), log.getCreatedAt()))
                .toList();
        return PageResponse.of(records, pageNum, pageSize, page.getTotal());
    }

    List<ModelConfigView> aiModels() {
        return modelConfigMapper.selectList(new LambdaQueryWrapper<ModelConfigEntity>().orderByDesc(ModelConfigEntity::getIsDefault))
                .stream()
                .map(model -> new ModelConfigView(model.getId(), model.getModelName(), model.getProviderCode(),
                        model.getEnabled(), model.getIsDefault(), model.getConfigJson()))
                .toList();
    }

    private long count(String table) {
        Long value = jdbcTemplate.queryForObject("select count(*) from " + table + " where deleted = 0", Long.class);
        return value == null ? 0 : value;
    }
}

interface ModelConfigMapper extends BaseMapper<ModelConfigEntity> {
}

@Data
@TableName("ea_ai_model_config")
class ModelConfigEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String modelName;
    private String providerCode;
    private Integer enabled;
    private Integer isDefault;
    private String configJson;
}

record AdminOverviewView(long userCount, long projectCount, long fileCount, long documentCount) {
}

record AdminUserView(
        Long id,
        String username,
        String displayName,
        String email,
        String roleCode,
        String status,
        Integer enabled,
        java.time.LocalDateTime createdAt
) {
}

record AdminUserStatusRequest(@NotBlank String status) {
}

record AdminTaskView(Long id, String taskType, String taskStatus, Integer retryCount, String lastError,
                     java.time.LocalDateTime createdAt) {
}

record AdminAuditLogView(
        Long id,
        String operatorUsername,
        String actionCode,
        String resourceType,
        Long resourceId,
        String detailJson,
        java.time.LocalDateTime createdAt
) {
}

record ModelConfigView(Long id, String modelName, String providerCode, Integer enabled, Integer isDefault,
                       String configJson) {
}
