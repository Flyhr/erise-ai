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
import com.erise.ai.backend.common.exception.BizException;
import com.erise.ai.backend.common.exception.ErrorCodes;
import com.erise.ai.backend.common.util.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
                                                          @RequestParam(defaultValue = "10") long pageSize,
                                                          @RequestParam(required = false) String q,
                                                          @RequestParam(required = false) String roleCode) {
        return ApiResponse.success(adminService.users(pageNum, pageSize, q, roleCode));
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

    @PostMapping("/tasks/{taskOrigin}/{id}/retry")
    public ApiResponse<Void> retryTask(@PathVariable String taskOrigin, @PathVariable Long id) {
        adminService.retryTask(taskOrigin, id);
        return ApiResponse.success("success", null);
    }

    @GetMapping("/audit-logs")
    public ApiResponse<PageResponse<AdminAuditLogView>> auditLogs(@RequestParam(defaultValue = "1") long pageNum,
                                                                  @RequestParam(defaultValue = "10") long pageSize,
                                                                  @RequestParam(required = false) String q,
                                                                  @RequestParam(required = false) String operatorUsername,
                                                                  @RequestParam(required = false) String actionCode,
                                                                  @RequestParam(required = false)
                                                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                                  LocalDate createdDate) {
        return ApiResponse.success(adminService.auditLogs(pageNum, pageSize, q, operatorUsername, actionCode, createdDate));
    }

    @GetMapping("/ai/models")
    public ApiResponse<List<ModelConfigView>> aiModels() {
        return ApiResponse.success(adminService.aiModels());
    }

    @PutMapping("/ai/models/{id}")
    public ApiResponse<Void> updateAiModel(@PathVariable Long id, @RequestBody ModelConfigUpdateRequest request) {
        adminService.updateAiModel(id, request);
        return ApiResponse.success("success", null);
    }
}

@Service
@RequiredArgsConstructor
class AdminService {

    private static final int DASHBOARD_TREND_DAYS = 7;
    private static final Set<String> RETRYABLE_FAILURE_STATUSES = Set.of("FAILED", "NEEDS_REPAIR");

    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final FileParseTaskMapper fileParseTaskMapper;
    private final RagTaskMapper ragTaskMapper;
    private final TaskMapper taskMapper;
    private final AuditLogMapper auditLogMapper;
    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;
    private final EriseProperties eriseProperties;
    private final FileService fileService;
    private final DocumentService documentService;
    private final AiTempFileService aiTempFileService;
    private final ObjectMapper objectMapper;

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
                visitSeries(DASHBOARD_TREND_DAYS),
                apiCallSeries(DASHBOARD_TREND_DAYS),
                tokenSeries(DASHBOARD_TREND_DAYS),
                tokenUsage(),
                securityLogs(),
                downloadLogs(),
                topActions()
        );
    }

    PageResponse<AdminUserView> users(long pageNum, long pageSize, String keyword, String roleCode) {
        long safePageNum = Math.max(pageNum, 1L);
        long safePageSize = Math.max(pageSize, 1L);
        long offset = (safePageNum - 1L) * safePageSize;

        List<Object> params = new ArrayList<>();
        String whereClause = userFilterClause(keyword, roleCode, params);
        long total = scalar("""
                select count(*)
                from ea_user u
                left join ea_user_profile up on up.user_id = u.id and up.deleted = 0
                where u.deleted = 0
                """ + whereClause, params.toArray());

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(safePageSize);
        pageParams.add(offset);
        List<AdminUserView> records = jdbcTemplate.query("""
                        select u.id,
                               u.username,
                               coalesce(up.display_name, u.username) as display_name,
                               u.email,
                               u.role_code,
                               u.status,
                               u.enabled,
                               u.created_at
                        from ea_user u
                        left join ea_user_profile up on up.user_id = u.id and up.deleted = 0
                        where u.deleted = 0
                        """ + whereClause + """
                        order by u.created_at desc, u.id desc
                        limit ? offset ?
                        """,
                (rs, rowNum) -> new AdminUserView(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("display_name"),
                        rs.getString("email"),
                        rs.getString("role_code"),
                        rs.getString("status"),
                        rs.getInt("enabled"),
                        toLocalDateTime(rs.getTimestamp("created_at"))
                ),
                pageParams.toArray());
        return PageResponse.of(records, safePageNum, safePageSize, total);
    }

    void changeUserStatus(Long userId, String status) {
        var currentUser = SecurityUtils.currentUser();
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }
        if ("ADMIN".equalsIgnoreCase(user.getRoleCode())) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "Administrator accounts cannot be disabled", HttpStatus.BAD_REQUEST);
        }
        user.setStatus(status);
        user.setEnabled("ACTIVE".equalsIgnoreCase(status) ? 1 : 0);
        user.setUpdatedBy(currentUser.userId());
        userMapper.updateById(user);
        auditLogService.log(currentUser, "ADMIN_USER_STATUS", "USER", userId, status);
    }

    PageResponse<AdminTaskView> tasks(long pageNum, long pageSize) {
        long total = totalTaskCount();
        long safePageNum = Math.max(pageNum, 1L);
        long safePageSize = Math.max(pageSize, 1L);
        long offset = (safePageNum - 1L) * safePageSize;
        List<AdminTaskView> records = jdbcTemplate.query(taskPageSql(), (rs, rowNum) -> {
                    String taskOrigin = rs.getString("task_origin");
                    String taskType = rs.getString("task_type");
                    String taskStatus = rs.getString("task_status");
                    String resourceType = rs.getString("resource_type");
                    Long resourceId = rs.getObject("resource_id") == null ? null : rs.getLong("resource_id");
                    return new AdminTaskView(
                            rs.getLong("id"),
                            taskOrigin,
                            taskType,
                            taskStatus,
                            resourceType,
                            resourceId,
                            rs.getString("resource_title"),
                            rs.getObject("retry_count") == null ? 0 : rs.getInt("retry_count"),
                            rs.getString("last_error"),
                            isRetryableTask(taskOrigin, taskType, taskStatus, resourceType),
                            toLocalDateTime(rs.getTimestamp("created_at"))
                    );
                },
                safePageSize,
                offset
        );
        return PageResponse.of(records, safePageNum, safePageSize, total);
    }

    void retryTask(String taskOrigin, Long taskId) {
        var currentUser = SecurityUtils.currentUser();
        String normalizedOrigin = normalize(taskOrigin);
        switch (normalizedOrigin) {
            case "FILE_PARSE" -> retryFileParseTask(taskId);
            case "RAG" -> retryRagTask(taskId);
            case "TEMP_FILE_PARSE" -> retryTempFileParseTask(taskId);
            default -> throw new BizException(ErrorCodes.BAD_REQUEST, "Unsupported task origin", HttpStatus.BAD_REQUEST);
        }
        auditLogService.log(currentUser, "ADMIN_TASK_RETRY", "ADMIN_TASK", taskId, Map.of("taskOrigin", normalizedOrigin));
    }

    PageResponse<AdminAuditLogView> auditLogs(long pageNum,
                                              long pageSize,
                                              String keyword,
                                              String operatorUsername,
                                              String actionCode,
                                              LocalDate createdDate) {
        long safePageNum = Math.max(pageNum, 1L);
        long safePageSize = Math.max(pageSize, 1L);
        long offset = (safePageNum - 1L) * safePageSize;

        List<Object> params = new ArrayList<>();
        String whereClause = auditLogFilterClause(keyword, operatorUsername, actionCode, createdDate, params);
        long total = scalar("""
                select count(*)
                from ea_audit_log
                where deleted = 0
                """ + whereClause, params.toArray());

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(safePageSize);
        pageParams.add(offset);
        List<AdminAuditLogView> records = jdbcTemplate.query("""
                        select id, operator_username, action_code, resource_type, resource_id, detail_json, created_at
                        from ea_audit_log
                        where deleted = 0
                        """ + whereClause + """
                        order by created_at desc, id desc
                        limit ? offset ?
                        """,
                (rs, rowNum) -> new AdminAuditLogView(
                        rs.getLong("id"),
                        rs.getString("operator_username"),
                        rs.getString("action_code"),
                        rs.getString("resource_type"),
                        rs.getObject("resource_id", Long.class),
                        rs.getString("detail_json"),
                        toLocalDateTime(rs.getTimestamp("created_at"))
                ),
                pageParams.toArray());
        return PageResponse.of(records, safePageNum, safePageSize, total);
    }

    List<ModelConfigView> aiModels() {
        return jdbcTemplate.query("""
                        select id, model_code, model_name, provider_code, enabled, support_stream,
                               max_context_tokens, priority_no, base_url, api_key_ref
                        from ai_model_config
                        order by priority_no asc, id asc
                        """,
                (rs, rowNum) -> mapModelConfigView(rs.getLong("id"),
                        rs.getString("model_code"),
                        rs.getString("model_name"),
                        rs.getString("provider_code"),
                        rs.getBoolean("enabled"),
                        rs.getBoolean("support_stream"),
                        rs.getObject("max_context_tokens", Integer.class),
                        rs.getObject("priority_no", Integer.class),
                        rs.getString("base_url"),
                        rs.getString("api_key_ref")));
    }

    void updateAiModel(Long id, ModelConfigUpdateRequest request) {
        var currentUser = SecurityUtils.currentUser();
        ModelConfigView current = aiModel(id);
        if (current == null) {
            throw new BizException(ErrorCodes.NOT_FOUND, "Model not found", HttpStatus.NOT_FOUND);
        }

        String modelName = trimToNull(request.modelName());
        if (modelName == null) {
            modelName = current.modelName();
        }
        if (modelName == null || modelName.isBlank()) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "Model name is required", HttpStatus.BAD_REQUEST);
        }

        String providerCode = trimToNull(request.providerCode());
        if (providerCode == null) {
            providerCode = current.providerCode();
        }
        providerCode = normalize(providerCode);
        if (providerCode.isBlank()) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "Provider code is required", HttpStatus.BAD_REQUEST);
        }

        Boolean enabled = request.enabled() == null ? current.enabled() : request.enabled();
        Boolean supportStream = request.supportStream() == null ? current.supportStream() : request.supportStream();
        Integer maxContextTokens = request.maxContextTokens() == null ? current.maxContextTokens() : request.maxContextTokens();
        Integer priorityNo = request.priorityNo() == null ? current.priorityNo() : request.priorityNo();
        if (priorityNo == null) {
            priorityNo = 1;
        }
        if (priorityNo < 0) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "Priority must be zero or greater", HttpStatus.BAD_REQUEST);
        }
        if (maxContextTokens != null && maxContextTokens <= 0) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "Context tokens must be greater than zero", HttpStatus.BAD_REQUEST);
        }

        String baseUrl = request.baseUrl() == null ? current.baseUrl() : trimToNull(request.baseUrl());
        String apiKeyRef = request.apiKeyRef() == null ? current.apiKeyRef() : trimToNull(request.apiKeyRef());

        int updated = jdbcTemplate.update("""
                        update ai_model_config
                        set model_name = ?,
                            provider_code = ?,
                            enabled = ?,
                            support_stream = ?,
                            max_context_tokens = ?,
                            priority_no = ?,
                            base_url = ?,
                            api_key_ref = ?,
                            updated_at = current_timestamp(6)
                        where id = ?
                        """,
                modelName,
                providerCode,
                Boolean.TRUE.equals(enabled) ? 1 : 0,
                Boolean.TRUE.equals(supportStream) ? 1 : 0,
                maxContextTokens,
                priorityNo,
                baseUrl,
                apiKeyRef,
                id);
        if (updated <= 0) {
            throw new BizException(ErrorCodes.NOT_FOUND, "Model not found", HttpStatus.NOT_FOUND);
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("modelCode", current.modelCode());
        detail.put("modelName", modelName);
        detail.put("providerCode", providerCode);
        detail.put("enabled", Boolean.TRUE.equals(enabled));
        detail.put("supportStream", Boolean.TRUE.equals(supportStream));
        detail.put("maxContextTokens", maxContextTokens);
        detail.put("priorityNo", priorityNo);
        auditLogService.log(currentUser, "ADMIN_MODEL_UPDATE", "AI_MODEL", id, detail);
    }

    private void retryFileParseTask(Long taskId) {
        FileParseTaskEntity task = fileParseTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ErrorCodes.NOT_FOUND, "Task not found", HttpStatus.NOT_FOUND);
        }
        if (!isRetryableTask("FILE_PARSE", "FILE_PARSE", task.getTaskStatus(), "FILE")) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "This task cannot be retried", HttpStatus.BAD_REQUEST);
        }
        fileService.retryParse(task.getFileId());
    }

    private void retryRagTask(Long taskId) {
        RagTaskEntity task = ragTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ErrorCodes.NOT_FOUND, "Task not found", HttpStatus.NOT_FOUND);
        }
        if (!isRetryableTask("RAG", task.getTaskType(), task.getTaskStatus(), task.getSourceType())) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "This task cannot be retried", HttpStatus.BAD_REQUEST);
        }
        String sourceType = normalize(task.getSourceType());
        switch (sourceType) {
            case "FILE" -> fileService.retryParse(task.getSourceId());
            case "DOCUMENT" -> documentService.retryIndex(task.getSourceId());
            case "TEMP_FILE" -> aiTempFileService.retryByAdmin(task.getSourceId());
            default -> throw new BizException(ErrorCodes.BAD_REQUEST, "Unsupported RAG resource type", HttpStatus.BAD_REQUEST);
        }
    }

    private void retryTempFileParseTask(Long taskId) {
        TaskEntity task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ErrorCodes.NOT_FOUND, "Task not found", HttpStatus.NOT_FOUND);
        }
        if (!isRetryableTask("TEMP_FILE_PARSE", task.getTaskType(), task.getTaskStatus(), "TEMP_FILE")) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "This task cannot be retried", HttpStatus.BAD_REQUEST);
        }
        TempFileParseTaskPayload payload;
        try {
            payload = objectMapper.readValue(task.getPayloadJson(), TempFileParseTaskPayload.class);
        } catch (Exception exception) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "Task payload is invalid", HttpStatus.BAD_REQUEST);
        }
        if (payload == null || payload.tempFileId() == null) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "Task payload is invalid", HttpStatus.BAD_REQUEST);
        }
        aiTempFileService.retryByAdmin(payload.tempFileId());
    }

    private boolean isRetryableTask(String taskOrigin, String taskType, String taskStatus, String resourceType) {
        String normalizedOrigin = normalize(taskOrigin);
        String normalizedTaskType = normalize(taskType);
        String normalizedStatus = normalize(taskStatus);
        String normalizedResourceType = normalize(resourceType);
        if (!RETRYABLE_FAILURE_STATUSES.contains(normalizedStatus)) {
            return false;
        }
        return switch (normalizedOrigin) {
            case "FILE_PARSE", "TEMP_FILE_PARSE" -> true;
            case "RAG" -> "INDEX".equals(normalizedTaskType)
                    && Set.of("FILE", "DOCUMENT", "TEMP_FILE").contains(normalizedResourceType);
            default -> false;
        };
    }

    private long totalTaskCount() {
        Long value = jdbcTemplate.queryForObject("""
                        select count(*) from (
                            select fp.id
                            from ea_file_parse_task fp
                            where fp.deleted = 0
                            union all
                            select rt.id
                            from ea_rag_task rt
                            where rt.deleted = 0
                            union all
                            select t.id
                            from ea_task t
                            where t.deleted = 0
                              and t.task_type = 'TEMP_FILE_PARSE'
                        ) task_union
                        """,
                Long.class
        );
        return value == null ? 0L : value;
    }

    private String taskPageSql() {
        return """
                select *
                from (
                    select fp.id,
                           'FILE_PARSE' as task_origin,
                           'FILE_PARSE' as task_type,
                           fp.task_status,
                           'FILE' as resource_type,
                           f.id as resource_id,
                           f.file_name as resource_title,
                           fp.retry_count,
                           fp.last_error,
                           fp.created_at
                    from ea_file_parse_task fp
                    left join ea_file f on f.id = fp.file_id and f.deleted = 0
                    where fp.deleted = 0

                    union all

                    select rt.id,
                           'RAG' as task_origin,
                           rt.task_type,
                           rt.task_status,
                           rt.source_type as resource_type,
                           rt.source_id as resource_id,
                           coalesce(f.file_name, d.title, ci.title, tf.file_name, rs.source_title) as resource_title,
                           rt.retry_count,
                           rt.last_error,
                           rt.created_at
                    from ea_rag_task rt
                    left join ea_rag_source rs on rs.id = rt.rag_source_id and rs.deleted = 0
                    left join ea_file f on rt.source_type = 'FILE' and f.id = rt.source_id and f.deleted = 0
                    left join ea_document d on rt.source_type = 'DOCUMENT' and d.id = rt.source_id and d.deleted = 0
                    left join ea_content_item ci on rt.source_type in ('SHEET', 'BOARD', 'DATA_TABLE') and ci.id = rt.source_id and ci.deleted = 0
                    left join ea_ai_temp_file tf on rt.source_type = 'TEMP_FILE' and tf.id = rt.source_id and tf.deleted = 0
                    where rt.deleted = 0

                    union all

                    select t.id,
                           'TEMP_FILE_PARSE' as task_origin,
                           t.task_type,
                           t.task_status,
                           'TEMP_FILE' as resource_type,
                           cast(json_unquote(json_extract(t.payload_json, '$.tempFileId')) as unsigned) as resource_id,
                           coalesce(tf.file_name, json_unquote(json_extract(t.payload_json, '$.fileName'))) as resource_title,
                           t.retry_count,
                           t.last_error,
                           t.created_at
                    from ea_task t
                    left join ea_ai_temp_file tf
                      on tf.id = cast(json_unquote(json_extract(t.payload_json, '$.tempFileId')) as unsigned)
                     and tf.deleted = 0
                    where t.deleted = 0
                      and t.task_type = 'TEMP_FILE_PARSE'
                ) task_union
                order by created_at desc, id desc
                limit ? offset ?
                """;
    }

    private List<AdminSeriesView> visitSeries(int days) {
        LocalDateTime start = dashboardTrendStart(days);
        return List.of(
                new AdminSeriesView("pv", "浏览量（PV）", fillDailyTrend(days, jdbcTemplate.queryForList("""
                        select date(created_at) as point_date, count(*) as total
                        from ea_user_login_log
                        where deleted = 0 and success = 1 and created_at >= ?
                        group by date(created_at)
                        order by point_date asc
                        """, start))),
                new AdminSeriesView("uv", "访客数（UV）", fillDailyTrend(days, jdbcTemplate.queryForList("""
                        select date(created_at) as point_date, count(distinct user_id) as total
                        from ea_user_login_log
                        where deleted = 0 and success = 1 and user_id is not null and created_at >= ?
                        group by date(created_at)
                        order by point_date asc
                        """, start)))
        );
    }

    private List<AdminSeriesView> apiCallSeries(int days) {
        return fillSeriesTrend(days, jdbcTemplate.queryForList("""
                select date(r.created_at) as point_date,
                       coalesce(nullif(r.model_code, ''), 'unknown-model') as series_key,
                       coalesce(nullif(mc.model_name, ''), nullif(r.model_code, ''), '未识别模型') as series_label,
                       count(*) as total
                from ai_request_log r
                left join ai_model_config mc on mc.model_code = r.model_code
                where r.created_at >= ?
                group by date(r.created_at), coalesce(nullif(r.model_code, ''), 'unknown-model'),
                         coalesce(nullif(mc.model_name, ''), nullif(r.model_code, ''), '未识别模型')
                order by point_date asc, total desc, series_label asc
                """, dashboardTrendStart(days)));
    }

    private List<AdminSeriesView> tokenSeries(int days) {
        return fillSeriesTrend(days, jdbcTemplate.queryForList("""
                select date(r.created_at) as point_date,
                       coalesce(nullif(r.model_code, ''), 'unknown-model') as series_key,
                       coalesce(nullif(mc.model_name, ''), nullif(r.model_code, ''), '未识别模型') as series_label,
                       coalesce(sum(coalesce(r.input_token_count, 0) + coalesce(r.output_token_count, 0)), 0) as total
                from ai_request_log r
                left join ai_model_config mc on mc.model_code = r.model_code
                where r.created_at >= ?
                group by date(r.created_at), coalesce(nullif(r.model_code, ''), 'unknown-model'),
                         coalesce(nullif(mc.model_name, ''), nullif(r.model_code, ''), '未识别模型')
                order by point_date asc, total desc, series_label asc
                """, dashboardTrendStart(days)));
    }

    private List<AdminTrendPointView> fillDailyTrend(int days, List<Map<String, Object>> rows) {
        Map<LocalDate, Long> values = new LinkedHashMap<>();
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

    private List<AdminSeriesView> fillSeriesTrend(int days, List<Map<String, Object>> rows) {
        Map<String, String> labels = new LinkedHashMap<>();
        Map<String, Map<LocalDate, Long>> seriesValues = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object pointDate = row.get("point_date");
            LocalDate date = pointDate instanceof java.sql.Date sqlDate
                    ? sqlDate.toLocalDate()
                    : LocalDate.parse(String.valueOf(pointDate));
            String seriesKey = String.valueOf(row.get("series_key"));
            String seriesLabel = String.valueOf(row.get("series_label"));
            labels.putIfAbsent(seriesKey, seriesLabel);
            seriesValues.computeIfAbsent(seriesKey, ignored -> new LinkedHashMap<>())
                    .put(date, ((Number) row.get("total")).longValue());
        }
        List<AdminSeriesView> series = new ArrayList<>();
        LocalDate start = LocalDate.now().minusDays(days - 1L);
        for (Map.Entry<String, String> entry : labels.entrySet()) {
            List<AdminTrendPointView> points = new ArrayList<>();
            Map<LocalDate, Long> values = seriesValues.getOrDefault(entry.getKey(), Map.of());
            for (int i = 0; i < days; i++) {
                LocalDate date = start.plusDays(i);
                points.add(new AdminTrendPointView(date.toString(), values.getOrDefault(date, 0L)));
            }
            series.add(new AdminSeriesView(entry.getKey(), entry.getValue(), points));
        }
        return series;
    }

    private AdminTokenUsageView tokenUsage() {
        Long promptTokens7d = scalar("""
                select coalesce(sum(input_token_count), 0)
                from ai_request_log
                where created_at >= ?
                """, trailingDaysStart(7));
        Long completionTokens7d = scalar("""
                select coalesce(sum(output_token_count), 0)
                from ai_request_log
                where created_at >= ?
                """, trailingDaysStart(7));
        Long totalTokens24h = scalar("""
                select coalesce(sum(coalesce(input_token_count, 0) + coalesce(output_token_count, 0)), 0)
                from ai_request_log
                where created_at >= ?
                """, LocalDateTime.now().minusHours(24));
        Long apiCalls24h = scalar("""
                select count(*)
                from ai_request_log
                where created_at >= ?
                """, LocalDateTime.now().minusHours(24));
        return new AdminTokenUsageView(
                promptTokens7d == null ? 0L : promptTokens7d,
                completionTokens7d == null ? 0L : completionTokens7d,
                (promptTokens7d == null ? 0L : promptTokens7d) + (completionTokens7d == null ? 0L : completionTokens7d),
                totalTokens24h == null ? 0L : totalTokens24h,
                apiCalls24h == null ? 0L : apiCalls24h
        );
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
                        where deleted = 0 and created_at >= ?
                        group by action_code
                        order by total desc
                        limit 20
                        """,
                (rs, rowNum) -> new AdminActionMetricView(rs.getString("action_code"), rs.getLong("total")),
                trailingDaysStart(7));
    }

    private String auditLogFilterClause(String keyword,
                                        String operatorUsername,
                                        String actionCode,
                                        LocalDate createdDate,
                                        List<Object> params) {
        StringBuilder where = new StringBuilder();
        String normalizedKeyword = trimToNull(keyword);
        String normalizedOperator = trimToNull(operatorUsername);
        String normalizedAction = trimToNull(actionCode);
        if (normalizedKeyword != null) {
            where.append("""
                     and (
                       coalesce(operator_username, '') like ?
                       or action_code like ?
                       or coalesce(resource_type, '') like ?
                       or coalesce(cast(resource_id as char), '') like ?
                       or coalesce(detail_json, '') like ?
                     )
                    """);
            String fuzzy = "%" + normalizedKeyword + "%";
            params.add(fuzzy);
            params.add(fuzzy);
            params.add(fuzzy);
            params.add(fuzzy);
            params.add(fuzzy);
        }
        if (normalizedOperator != null) {
            where.append(" and coalesce(operator_username, '') like ? ");
            params.add("%" + normalizedOperator + "%");
        }
        if (normalizedAction != null) {
            where.append(" and action_code like ? ");
            params.add("%" + normalizedAction + "%");
        }
        if (createdDate != null) {
            where.append(" and created_at >= ? and created_at < ? ");
            params.add(createdDate.atStartOfDay());
            params.add(createdDate.plusDays(1L).atStartOfDay());
        }
        return where.toString();
    }

    private String userFilterClause(String keyword, String roleCode, List<Object> params) {
        StringBuilder where = new StringBuilder();
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        String normalizedRole = roleCode == null ? "" : roleCode.trim().toUpperCase(Locale.ROOT);
        if (!normalizedKeyword.isBlank()) {
            where.append("""
                     and (
                       u.username like ?
                       or coalesce(u.email, '') like ?
                       or coalesce(up.display_name, '') like ?
                     )
                    """);
            String fuzzy = "%" + normalizedKeyword + "%";
            params.add(fuzzy);
            params.add(fuzzy);
            params.add(fuzzy);
        }
        if ("USER".equals(normalizedRole) || "ADMIN".equals(normalizedRole)) {
            where.append(" and u.role_code = ? ");
            params.add(normalizedRole);
        }
        return where.toString();
    }

    private long count(String table) {
        Long value = jdbcTemplate.queryForObject("select count(*) from " + table + " where deleted = 0", Long.class);
        return value == null ? 0 : value;
    }

    private long scalar(String sql) {
        return scalar(sql, new Object[0]);
    }

    private long scalar(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private ModelConfigView aiModel(Long id) {
        List<ModelConfigView> records = jdbcTemplate.query("""
                        select id, model_code, model_name, provider_code, enabled, support_stream,
                               max_context_tokens, priority_no, base_url, api_key_ref
                        from ai_model_config
                        where id = ?
                        limit 1
                        """,
                (rs, rowNum) -> mapModelConfigView(rs.getLong("id"),
                        rs.getString("model_code"),
                        rs.getString("model_name"),
                        rs.getString("provider_code"),
                        rs.getBoolean("enabled"),
                        rs.getBoolean("support_stream"),
                        rs.getObject("max_context_tokens", Integer.class),
                        rs.getObject("priority_no", Integer.class),
                        rs.getString("base_url"),
                        rs.getString("api_key_ref")),
                id);
        return records.isEmpty() ? null : records.get(0);
    }

    private ModelConfigView mapModelConfigView(Long id,
                                               String modelCode,
                                               String modelName,
                                               String providerCode,
                                               boolean enabled,
                                               boolean supportStream,
                                               Integer maxContextTokens,
                                               Integer priorityNo,
                                               String baseUrl,
                                               String apiKeyRef) {
        return new ModelConfigView(
                id,
                modelCode,
                modelName,
                providerCode,
                enabled,
                eriseProperties.getCloud().getDefaultModelCode().equals(modelCode),
                supportStream,
                maxContextTokens,
                priorityNo,
                baseUrl,
                apiKeyRef
        );
    }

    private LocalDateTime dashboardTrendStart(int days) {
        return LocalDate.now().minusDays(Math.max(days - 1L, 0L)).atStartOfDay();
    }

    private LocalDateTime trailingDaysStart(int days) {
        return LocalDateTime.of(LocalDate.now().minusDays(Math.max(days - 1L, 0L)), LocalTime.MIN);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
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
        List<AdminSeriesView> visitSeries,
        List<AdminSeriesView> apiCallSeries,
        List<AdminSeriesView> tokenSeries,
        AdminTokenUsageView tokenUsage,
        List<AdminSecurityLogView> securityLogs,
        List<AdminDownloadLogView> downloadLogs,
        List<AdminActionMetricView> topActions
) {
}

record AdminTrendPointView(String label, long value) {
}

record AdminSeriesView(String key, String label, List<AdminTrendPointView> points) {
}

record AdminTokenUsageView(
        long promptTokens7d,
        long completionTokens7d,
        long totalTokens7d,
        long totalTokens24h,
        long apiCalls24h
) {
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

record AdminTaskView(
        Long id,
        String taskOrigin,
        String taskType,
        String taskStatus,
        String resourceType,
        Long resourceId,
        String resourceTitle,
        Integer retryCount,
        String lastError,
        Boolean retryable,
        LocalDateTime createdAt
) {
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

record ModelConfigUpdateRequest(
        String modelName,
        String providerCode,
        Boolean enabled,
        Boolean supportStream,
        Integer maxContextTokens,
        Integer priorityNo,
        String baseUrl,
        String apiKeyRef
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
