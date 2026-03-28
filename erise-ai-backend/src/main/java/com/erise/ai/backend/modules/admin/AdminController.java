package com.erise.ai.backend.modules;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erise.ai.backend.common.api.ApiResponse;
import com.erise.ai.backend.common.api.PageResponse;
import com.erise.ai.backend.common.config.EriseProperties;
import com.erise.ai.backend.common.entity.AuditableEntity;
import com.erise.ai.backend.common.util.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
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

    @GetMapping("/dashboard")
    public ApiResponse<AdminDashboardView> dashboard() {
        return ApiResponse.success(adminService.dashboard());
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
    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;
    private final EriseProperties eriseProperties;

    AdminOverviewView overview() {
        long userCount = count("ea_user");
        long projectCount = count("ea_project");
        long fileCount = count("ea_file");
        long documentCount = count("ea_document");
        return new AdminOverviewView(userCount, projectCount, fileCount, documentCount);
    }

    AdminDashboardView dashboard() {
        AdminOverviewView overview = overview();
        AdminOperationalMetricsView metrics = new AdminOperationalMetricsView(
                scalar("select count(*) from ai_chat_session where status <> 'deleted'"),
                count("ea_search_history"),
                scalar("select count(distinct user_id) from ea_user_login_log where deleted = 0 and success = 1 and date(created_at) = curdate()"),
                scalar("select count(*) from ea_user_login_log where deleted = 0 and success = 0 and created_at >= date_sub(now(), interval 24 hour)"),
                scalar("select count(*) from ea_audit_log where deleted = 0 and action_code = 'FILE_DOWNLOAD' and created_at >= date_sub(now(), interval 24 hour)"),
                scalar("select count(*) from ea_audit_log where deleted = 0 and action_code = 'AI_CHAT' and created_at >= date_sub(now(), interval 24 hour)")
        );
        return new AdminDashboardView(
                overview,
                metrics,
                loginTrend(7),
                downloadTrend(7),
                securityLogs(),
                downloadLogs(),
                topActions()
        );
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
        String defaultModelCode = eriseProperties.getCloud().getDefaultModelCode();
        return jdbcTemplate.query("""
                        select id, model_code, model_name, provider_code, enabled, support_stream,
                               max_context_tokens, priority_no, base_url, api_key_ref
                        from ai_model_config
                        order by priority_no asc, id asc
                        """,
                (rs, rowNum) -> new ModelConfigView(
                        rs.getLong("id"),
                        rs.getString("model_code"),
                        rs.getString("model_name"),
                        rs.getString("provider_code"),
                        rs.getBoolean("enabled"),
                        defaultModelCode.equals(rs.getString("model_code")),
                        rs.getBoolean("support_stream"),
                        rs.getObject("max_context_tokens", Integer.class),
                        rs.getObject("priority_no", Integer.class),
                        rs.getString("base_url"),
                        rs.getString("api_key_ref")
                ));
    }

    private List<AdminTrendPointView> loginTrend(int days) {
        return fillDailyTrend(days, jdbcTemplate.queryForList("""
                select date(created_at) as point_date, count(*) as total
                from ea_user_login_log
                where deleted = 0 and success = 1 and created_at >= date_sub(curdate(), interval ? day)
                group by date(created_at)
                order by point_date asc
                """, days, days));
    }

    private List<AdminTrendPointView> downloadTrend(int days) {
        return fillDailyTrend(days, jdbcTemplate.queryForList("""
                select date(created_at) as point_date, count(*) as total
                from ea_audit_log
                where deleted = 0 and action_code = 'FILE_DOWNLOAD' and created_at >= date_sub(curdate(), interval ? day)
                group by date(created_at)
                order by point_date asc
                """, days, days));
    }

    private List<AdminTrendPointView> fillDailyTrend(int days, List<Map<String, Object>> rows) {
        Map<LocalDate, Long> values = new java.util.HashMap<>();
        for (Map<String, Object> row : rows) {
            Object pointDate = row.get("point_date");
            LocalDate date = pointDate instanceof java.sql.Date sqlDate
                    ? sqlDate.toLocalDate()
                    : LocalDate.parse(String.valueOf(pointDate));
            values.put(date, ((Number) row.get("total")).longValue());
        }
        List<AdminTrendPointView> trend = new ArrayList<>();
        LocalDate start = LocalDate.now().minusDays(days - 1L);
        for (int i = 0; i < days; i++) {
            LocalDate date = start.plusDays(i);
            trend.add(new AdminTrendPointView(date.toString(), values.getOrDefault(date, 0L)));
        }
        return trend;
    }

    private List<AdminSecurityLogView> securityLogs() {
        return jdbcTemplate.query("""
                        select username, login_ip, user_agent, created_at
                        from ea_user_login_log
                        where deleted = 0 and success = 0
                        order by created_at desc
                        limit 20
                        """,
                (rs, rowNum) -> new AdminSecurityLogView(
                        rs.getString("username"),
                        rs.getString("login_ip"),
                        rs.getString("user_agent"),
                        toLocalDateTime(rs.getTimestamp("created_at"))
                ));
    }

    private List<AdminDownloadLogView> downloadLogs() {
        return jdbcTemplate.query("""
                        select operator_username, resource_id, detail_json, created_at
                        from ea_audit_log
                        where deleted = 0 and action_code = 'FILE_DOWNLOAD'
                        order by created_at desc
                        limit 20
                        """,
                (rs, rowNum) -> new AdminDownloadLogView(
                        rs.getString("operator_username"),
                        rs.getLong("resource_id"),
                        rs.getString("detail_json"),
                        toLocalDateTime(rs.getTimestamp("created_at"))
                ));
    }

    private List<AdminActionMetricView> topActions() {
        return jdbcTemplate.query("""
                        select action_code, count(*) as total
                        from ea_audit_log
                        where deleted = 0 and created_at >= date_sub(now(), interval 7 day)
                        group by action_code
                        order by total desc
                        limit 8
                        """,
                (rs, rowNum) -> new AdminActionMetricView(rs.getString("action_code"), rs.getLong("total")));
    }

    private long count(String table) {
        Long value = jdbcTemplate.queryForObject("select count(*) from " + table + " where deleted = 0", Long.class);
        return value == null ? 0 : value;
    }

    private long scalar(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}

/*
 * Legacy Java AI model mapping kept for reference after moving runtime/model management
 * to the Python AI chat service.
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
 */

record AdminOverviewView(long userCount, long projectCount, long fileCount, long documentCount) {
}

record AdminOperationalMetricsView(
        long aiSessionCount,
        long searchCount,
        long activeUsersToday,
        long failedLogins24h,
        long downloads24h,
        long aiChats24h
) {
}

record AdminDashboardView(
        AdminOverviewView overview,
        AdminOperationalMetricsView metrics,
        List<AdminTrendPointView> visitTrend,
        List<AdminTrendPointView> downloadTrend,
        List<AdminSecurityLogView> securityLogs,
        List<AdminDownloadLogView> downloadLogs,
        List<AdminActionMetricView> topActions
) {
}

record AdminTrendPointView(String label, long value) {
}

record AdminSecurityLogView(String username, String loginIp, String userAgent, LocalDateTime createdAt) {
}

record AdminDownloadLogView(String operatorUsername, Long resourceId, String detailJson, LocalDateTime createdAt) {
}

record AdminActionMetricView(String actionCode, long total) {
}

record AdminUserView(
        Long id,
        String username,
        String displayName,
        String email,
        String roleCode,
        String status,
        Integer enabled,
        LocalDateTime createdAt
) {
}

record AdminUserStatusRequest(@NotBlank String status) {
}

record AdminTaskView(Long id, String taskType, String taskStatus, Integer retryCount, String lastError,
                     LocalDateTime createdAt) {
}

record AdminAuditLogView(
        Long id,
        String operatorUsername,
        String actionCode,
        String resourceType,
        Long resourceId,
        String detailJson,
        LocalDateTime createdAt
) {
}

record ModelConfigView(
        Long id,
        String modelCode,
        String modelName,
        String providerCode,
        Boolean enabled,
        Boolean isDefault,
        Boolean supportStream,
        Integer maxContextTokens,
        Integer priorityNo,
        String baseUrl,
        String apiKeyRef
) {
}