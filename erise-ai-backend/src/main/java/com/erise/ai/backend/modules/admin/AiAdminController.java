package com.erise.ai.backend.modules;

import com.erise.ai.backend.common.api.ApiResponse;
import com.erise.ai.backend.common.api.PageResponse;
import com.erise.ai.backend.common.exception.BizException;
import com.erise.ai.backend.common.exception.ErrorCodes;
import com.erise.ai.backend.common.util.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/ai")
@RequiredArgsConstructor
@Validated
public class AiAdminController {

    private final AiAdminService aiAdminService;

    @GetMapping("/prompts")
    public ApiResponse<PageResponse<AiPromptTemplateSummaryView>> prompts(
            @RequestParam(defaultValue = "1") @Min(1) long pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String scene,
            @RequestParam(required = false) Boolean enabledOnly) {
        return ApiResponse.success(aiAdminService.promptTemplates(pageNum, pageSize, q, scene, enabledOnly));
    }

    @GetMapping("/prompts/{templateCode}/versions")
    public ApiResponse<List<AiPromptTemplateVersionView>> promptVersions(@PathVariable String templateCode) {
        return ApiResponse.success(aiAdminService.promptTemplateVersions(templateCode));
    }

    @PostMapping("/prompts")
    public ApiResponse<AiPromptTemplateVersionView> createPrompt(@Valid @RequestBody AiPromptTemplateCreateRequest request) {
        return ApiResponse.success(aiAdminService.createPromptTemplate(request));
    }

    @PostMapping("/prompts/{templateCode}/versions")
    public ApiResponse<AiPromptTemplateVersionView> createPromptVersion(@PathVariable String templateCode,
                                                                        @Valid @RequestBody AiPromptTemplateVersionCreateRequest request) {
        return ApiResponse.success(aiAdminService.createPromptTemplateVersion(templateCode, request));
    }

    @PutMapping("/prompts/{id}")
    public ApiResponse<Void> updatePrompt(@PathVariable Long id, @Valid @RequestBody AiPromptTemplateUpdateRequest request) {
        aiAdminService.updatePromptTemplate(id, request);
        return ApiResponse.success("success", null);
    }

    @PostMapping("/prompts/{id}/status")
    public ApiResponse<Void> updatePromptStatus(@PathVariable Long id, @Valid @RequestBody AiPromptTemplateStatusRequest request) {
        aiAdminService.updatePromptTemplateStatus(id, request.enabled());
        return ApiResponse.success("success", null);
    }

    @GetMapping("/request-logs")
    public ApiResponse<PageResponse<AiRequestLogAdminView>> requestLogs(
            @RequestParam(defaultValue = "1") @Min(1) long pageNum,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String modelCode,
            @RequestParam(required = false) String scene,
            @RequestParam(required = false) Boolean successFlag,
            @RequestParam(required = false) Boolean errorOnly,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdDate) {
        return ApiResponse.success(aiAdminService.requestLogs(pageNum, pageSize, q, modelCode, scene, successFlag, errorOnly, createdDate));
    }

    @GetMapping("/cost-stats")
    public ApiResponse<AiCostStatsView> costStats(@RequestParam(defaultValue = "7") @Min(1) @Max(365) int days) {
        return ApiResponse.success(aiAdminService.costStats(days));
    }

    @GetMapping("/feedback")
    public ApiResponse<PageResponse<AiFeedbackAdminView>> feedback(
            @RequestParam(defaultValue = "1") @Min(1) long pageNum,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String feedbackType,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdDate) {
        return ApiResponse.success(aiAdminService.feedback(pageNum, pageSize, q, feedbackType, createdDate));
    }

    @GetMapping("/index-tasks")
    public ApiResponse<PageResponse<AiIndexTaskAdminView>> indexTasks(
            @RequestParam(defaultValue = "1") @Min(1) long pageNum,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String taskOrigin,
            @RequestParam(required = false) String taskStatus,
            @RequestParam(required = false) Boolean errorOnly) {
        return ApiResponse.success(aiAdminService.indexTasks(pageNum, pageSize, q, taskOrigin, taskStatus, errorOnly));
    }
}

@Service
@RequiredArgsConstructor
class AiAdminService {

    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;

    PageResponse<AiPromptTemplateSummaryView> promptTemplates(long pageNum,
                                                              long pageSize,
                                                              String keyword,
                                                              String scene,
                                                              Boolean enabledOnly) {
        long safePageNum = Math.max(pageNum, 1L);
        long safePageSize = Math.max(pageSize, 1L);
        long offset = (safePageNum - 1L) * safePageSize;

        List<Object> params = new ArrayList<>();
        String whereClause = promptSummaryFilterClause(keyword, scene, enabledOnly, params);
        String summarySql = promptSummaryBaseSql() + whereClause;
        long total = scalar("select count(*) from (" + summarySql + ") prompt_summary", params.toArray());

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(safePageSize);
        pageParams.add(offset);
        List<AiPromptTemplateSummaryView> records = jdbcTemplate.query(summarySql + """
                        order by latest_updated_at desc, template_code asc
                        limit ? offset ?
                        """,
                (rs, rowNum) -> new AiPromptTemplateSummaryView(
                        rs.getString("template_code"),
                        rs.getString("template_name"),
                        rs.getString("scene"),
                        rs.getLong("latest_version_id"),
                        rs.getInt("latest_version_no"),
                        rs.getObject("enabled_version_id", Long.class),
                        rs.getObject("enabled_version_no", Integer.class),
                        rs.getBoolean("has_enabled_version"),
                        toLocalDateTime(rs.getTimestamp("latest_updated_at"))
                ),
                pageParams.toArray());
        return PageResponse.of(records, safePageNum, safePageSize, total);
    }

    List<AiPromptTemplateVersionView> promptTemplateVersions(String templateCode) {
        String normalizedCode = requireValue(templateCode, "Template code is required");
        return jdbcTemplate.query("""
                        select id, template_code, template_name, scene, system_prompt, user_prompt_wrapper,
                               enabled, version_no, created_by, created_at, updated_at
                        from ai_prompt_template
                        where template_code = ?
                        order by version_no desc, id desc
                        """,
                (rs, rowNum) -> mapPromptTemplateVersion(rs),
                normalizedCode);
    }

    AiPromptTemplateVersionView createPromptTemplate(AiPromptTemplateCreateRequest request) {
        var currentUser = SecurityUtils.currentUser();
        String templateCode = requireValue(request.templateCode(), "Template code is required").toLowerCase(Locale.ROOT);
        Long exists = jdbcTemplate.queryForObject(
                "select count(*) from ai_prompt_template where template_code = ?",
                Long.class,
                templateCode
        );
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCodes.CONFLICT, "Template code already exists", HttpStatus.CONFLICT);
        }

        boolean enabled = request.enabled() == null || request.enabled();
        jdbcTemplate.update("""
                        insert into ai_prompt_template (template_code, template_name, scene, system_prompt,
                                                       user_prompt_wrapper, enabled, version_no, created_by,
                                                       created_at, updated_at)
                        values (?, ?, ?, ?, ?, ?, 1, ?, current_timestamp(6), current_timestamp(6))
                        """,
                templateCode,
                requireValue(request.templateName(), "Template name is required"),
                normalizeScene(request.scene()),
                request.systemPrompt().trim(),
                trimToNull(request.userPromptWrapper()),
                enabled ? 1 : 0,
                currentUser.username());

        Long id = jdbcTemplate.queryForObject("""
                select id
                from ai_prompt_template
                where template_code = ? and version_no = 1
                limit 1
                """, Long.class, templateCode);
        auditLogService.log(currentUser, "ADMIN_AI_PROMPT_CREATE", "AI_PROMPT_TEMPLATE", id, Map.of("templateCode", templateCode));
        return promptVersionById(id);
    }

    AiPromptTemplateVersionView createPromptTemplateVersion(String templateCode, AiPromptTemplateVersionCreateRequest request) {
        var currentUser = SecurityUtils.currentUser();
        String normalizedCode = requireValue(templateCode, "Template code is required");
        AiPromptTemplateVersionView latest = latestPromptVersion(normalizedCode);
        int nextVersion = latest.versionNo() + 1;
        boolean enabled = request.enabled() != null && request.enabled();
        if (enabled) {
            jdbcTemplate.update("update ai_prompt_template set enabled = 0 where template_code = ?", normalizedCode);
        }
        jdbcTemplate.update("""
                        insert into ai_prompt_template (template_code, template_name, scene, system_prompt,
                                                       user_prompt_wrapper, enabled, version_no, created_by,
                                                       created_at, updated_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, current_timestamp(6), current_timestamp(6))
                        """,
                normalizedCode,
                trimToNull(request.templateName()) == null ? latest.templateName() : request.templateName().trim(),
                trimToNull(request.scene()) == null ? latest.scene() : normalizeScene(request.scene()),
                request.systemPrompt().trim(),
                trimToNull(request.userPromptWrapper()),
                enabled ? 1 : 0,
                nextVersion,
                currentUser.username());

        Long id = jdbcTemplate.queryForObject("""
                select id
                from ai_prompt_template
                where template_code = ? and version_no = ?
                limit 1
                """, Long.class, normalizedCode, nextVersion);
        auditLogService.log(currentUser, "ADMIN_AI_PROMPT_VERSION_CREATE", "AI_PROMPT_TEMPLATE", id,
                Map.of("templateCode", normalizedCode, "versionNo", nextVersion));
        return promptVersionById(id);
    }

    void updatePromptTemplate(Long id, AiPromptTemplateUpdateRequest request) {
        var currentUser = SecurityUtils.currentUser();
        AiPromptTemplateVersionView current = promptVersionById(id);
        int updated = jdbcTemplate.update("""
                        update ai_prompt_template
                        set template_name = ?,
                            scene = ?,
                            system_prompt = ?,
                            user_prompt_wrapper = ?,
                            updated_at = current_timestamp(6)
                        where id = ?
                        """,
                requireValue(request.templateName(), "Template name is required"),
                normalizeScene(request.scene()),
                request.systemPrompt().trim(),
                trimToNull(request.userPromptWrapper()),
                id);
        if (updated <= 0) {
            throw new BizException(ErrorCodes.NOT_FOUND, "Prompt template version not found", HttpStatus.NOT_FOUND);
        }
        auditLogService.log(currentUser, "ADMIN_AI_PROMPT_UPDATE", "AI_PROMPT_TEMPLATE", id,
                Map.of("templateCode", current.templateCode(), "versionNo", current.versionNo()));
    }

    void updatePromptTemplateStatus(Long id, boolean enabled) {
        var currentUser = SecurityUtils.currentUser();
        AiPromptTemplateVersionView current = promptVersionById(id);
        if (enabled) {
            jdbcTemplate.update("update ai_prompt_template set enabled = 0 where template_code = ?", current.templateCode());
        }
        jdbcTemplate.update("""
                        update ai_prompt_template
                        set enabled = ?,
                            updated_at = current_timestamp(6)
                        where id = ?
                        """,
                enabled ? 1 : 0,
                id);
        auditLogService.log(currentUser, enabled ? "ADMIN_AI_PROMPT_ENABLE" : "ADMIN_AI_PROMPT_DISABLE",
                "AI_PROMPT_TEMPLATE", id, Map.of("templateCode", current.templateCode(), "versionNo", current.versionNo()));
    }

    PageResponse<AiRequestLogAdminView> requestLogs(long pageNum,
                                                    long pageSize,
                                                    String keyword,
                                                    String modelCode,
                                                    String scene,
                                                    Boolean successFlag,
                                                    Boolean errorOnly,
                                                    LocalDate createdDate) {
        long safePageNum = Math.max(pageNum, 1L);
        long safePageSize = Math.max(pageSize, 1L);
        long offset = (safePageNum - 1L) * safePageSize;

        List<Object> params = new ArrayList<>();
        String whereClause = requestLogFilterClause(keyword, modelCode, scene, successFlag, errorOnly, createdDate, params);
        long total = scalar("""
                select count(*)
                from ai_request_log r
                left join ea_user u on u.id = r.user_id and u.deleted = 0
                left join ea_project p on p.id = r.project_id and p.deleted = 0
                where 1 = 1
                """ + whereClause, params.toArray());

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(safePageSize);
        pageParams.add(offset);
        List<AiRequestLogAdminView> records = jdbcTemplate.query("""
                        select r.id, r.request_id, r.session_id, r.user_id, u.username,
                               r.project_id, p.name as project_name,
                               r.provider_code, r.model_code, r.scene, r.stream,
                               r.input_token_count, r.output_token_count, r.total_token_count,
                               r.latency_ms, r.success_flag, r.answer_source, r.message_status,
                               r.error_code, r.error_message, r.created_at
                        from ai_request_log r
                        left join ea_user u on u.id = r.user_id and u.deleted = 0
                        left join ea_project p on p.id = r.project_id and p.deleted = 0
                        where 1 = 1
                        """ + whereClause + """
                        order by r.created_at desc, r.id desc
                        limit ? offset ?
                        """,
                (rs, rowNum) -> new AiRequestLogAdminView(
                        rs.getLong("id"),
                        rs.getString("request_id"),
                        rs.getLong("session_id"),
                        rs.getLong("user_id"),
                        rs.getString("username"),
                        rs.getObject("project_id", Long.class),
                        rs.getString("project_name"),
                        rs.getString("provider_code"),
                        rs.getString("model_code"),
                        rs.getString("scene"),
                        rs.getBoolean("stream"),
                        rs.getObject("input_token_count", Integer.class),
                        rs.getObject("output_token_count", Integer.class),
                        rs.getObject("total_token_count", Integer.class),
                        rs.getObject("latency_ms", Integer.class),
                        rs.getBoolean("success_flag"),
                        rs.getString("answer_source"),
                        rs.getString("message_status"),
                        rs.getString("error_code"),
                        rs.getString("error_message"),
                        toLocalDateTime(rs.getTimestamp("created_at"))
                ),
                pageParams.toArray());
        return PageResponse.of(records, safePageNum, safePageSize, total);
    }

    AiCostStatsView costStats(int days) {
        int safeDays = Math.max(days, 1);
        LocalDateTime start = LocalDate.now().minusDays(safeDays - 1L).atTime(LocalTime.MIN);

        Map<String, Object> aggregate = jdbcTemplate.queryForMap("""
                select count(*) as total_requests,
                       coalesce(sum(case when r.success_flag = 1 then 1 else 0 end), 0) as success_requests,
                       coalesce(sum(case when r.success_flag = 0 then 1 else 0 end), 0) as failed_requests,
                       coalesce(sum(coalesce(r.input_token_count, 0)), 0) as prompt_tokens,
                       coalesce(sum(coalesce(r.output_token_count, 0)), 0) as completion_tokens,
                       coalesce(sum(coalesce(r.total_token_count, 0)), 0) as total_tokens,
                       coalesce(round(avg(coalesce(r.latency_ms, 0))), 0) as average_latency_ms,
                       coalesce(sum(
                           (coalesce(r.input_token_count, 0) / 1000000.0) * coalesce(mc.input_price_per_million, 0)
                           + (coalesce(r.output_token_count, 0) / 1000000.0) * coalesce(mc.output_price_per_million, 0)
                       ), 0) as estimated_cost
                from ai_request_log r
                left join ai_model_config mc on mc.model_code = r.model_code
                where r.created_at >= ?
                """, start);

        List<AiCostModelBreakdownView> modelBreakdown = jdbcTemplate.query("""
                        select r.model_code,
                               coalesce(mc.model_name, r.model_code) as model_name,
                               coalesce(nullif(mc.currency_code, ''), 'USD') as currency_code,
                               count(*) as request_count,
                               coalesce(sum(coalesce(r.total_token_count, 0)), 0) as total_tokens,
                               coalesce(sum(
                                   (coalesce(r.input_token_count, 0) / 1000000.0) * coalesce(mc.input_price_per_million, 0)
                                   + (coalesce(r.output_token_count, 0) / 1000000.0) * coalesce(mc.output_price_per_million, 0)
                               ), 0) as estimated_cost
                        from ai_request_log r
                        left join ai_model_config mc on mc.model_code = r.model_code
                        where r.created_at >= ?
                        group by r.model_code, coalesce(mc.model_name, r.model_code), coalesce(nullif(mc.currency_code, ''), 'USD')
                        order by estimated_cost desc, request_count desc, model_name asc
                        """,
                (rs, rowNum) -> new AiCostModelBreakdownView(
                        rs.getString("model_code"),
                        rs.getString("model_name"),
                        rs.getString("currency_code"),
                        rs.getLong("request_count"),
                        rs.getLong("total_tokens"),
                        rs.getDouble("estimated_cost")
                ),
                start);

        return new AiCostStatsView(
                safeDays,
                numberValue(aggregate.get("total_requests")),
                numberValue(aggregate.get("success_requests")),
                numberValue(aggregate.get("failed_requests")),
                numberValue(aggregate.get("prompt_tokens")),
                numberValue(aggregate.get("completion_tokens")),
                numberValue(aggregate.get("total_tokens")),
                numberValue(aggregate.get("average_latency_ms")),
                resolveCurrencyCode(modelBreakdown),
                decimalValue(aggregate.get("estimated_cost")),
                modelBreakdown
        );
    }

    PageResponse<AiFeedbackAdminView> feedback(long pageNum,
                                               long pageSize,
                                               String keyword,
                                               String feedbackType,
                                               LocalDate createdDate) {
        long safePageNum = Math.max(pageNum, 1L);
        long safePageSize = Math.max(pageSize, 1L);
        long offset = (safePageNum - 1L) * safePageSize;

        List<Object> params = new ArrayList<>();
        String whereClause = feedbackFilterClause(keyword, feedbackType, createdDate, params);
        long total = scalar("""
                select count(*)
                from ai_message_feedback f
                join ai_chat_message m on m.id = f.message_id
                join ai_chat_session s on s.id = f.session_id
                left join ea_user u on u.id = f.user_id and u.deleted = 0
                left join ea_project p on p.id = s.project_id and p.deleted = 0
                where 1 = 1
                """ + whereClause, params.toArray());

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(safePageSize);
        pageParams.add(offset);
        List<AiFeedbackAdminView> records = jdbcTemplate.query("""
                        select f.id, f.message_id, f.session_id, f.user_id, u.username,
                               f.feedback_type, f.feedback_note,
                               left(coalesce(m.content, ''), 280) as answer_excerpt,
                               s.project_id, p.name as project_name,
                               coalesce(r.model_code, m.model_code) as model_code,
                               f.created_at
                        from ai_message_feedback f
                        join ai_chat_message m on m.id = f.message_id
                        join ai_chat_session s on s.id = f.session_id
                        left join ai_request_log r on r.assistant_message_id = m.id
                        left join ea_user u on u.id = f.user_id and u.deleted = 0
                        left join ea_project p on p.id = s.project_id and p.deleted = 0
                        where 1 = 1
                        """ + whereClause + """
                        order by f.created_at desc, f.id desc
                        limit ? offset ?
                        """,
                (rs, rowNum) -> new AiFeedbackAdminView(
                        rs.getLong("id"),
                        rs.getLong("message_id"),
                        rs.getLong("session_id"),
                        rs.getLong("user_id"),
                        rs.getString("username"),
                        rs.getString("feedback_type"),
                        rs.getString("feedback_note"),
                        rs.getString("answer_excerpt"),
                        rs.getObject("project_id", Long.class),
                        rs.getString("project_name"),
                        rs.getString("model_code"),
                        toLocalDateTime(rs.getTimestamp("created_at"))
                ),
                pageParams.toArray());
        return PageResponse.of(records, safePageNum, safePageSize, total);
    }

    PageResponse<AiIndexTaskAdminView> indexTasks(long pageNum,
                                                  long pageSize,
                                                  String keyword,
                                                  String taskOrigin,
                                                  String taskStatus,
                                                  Boolean errorOnly) {
        long safePageNum = Math.max(pageNum, 1L);
        long safePageSize = Math.max(pageSize, 1L);
        long offset = (safePageNum - 1L) * safePageSize;

        List<Object> params = new ArrayList<>();
        String whereClause = indexTaskFilterClause(keyword, taskOrigin, taskStatus, errorOnly, params);
        String unionSql = indexTaskBaseSql();
        long total = scalar("select count(*) from (" + unionSql + ") index_task where 1 = 1 " + whereClause, params.toArray());

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(safePageSize);
        pageParams.add(offset);
        List<AiIndexTaskAdminView> records = jdbcTemplate.query("""
                        select *
                        from (
                        """ + unionSql + """
                        ) index_task
                        where 1 = 1
                        """ + whereClause + """
                        order by created_at desc, id desc
                        limit ? offset ?
                        """,
                (rs, rowNum) -> new AiIndexTaskAdminView(
                        rs.getLong("id"),
                        rs.getString("task_origin"),
                        rs.getString("task_type"),
                        rs.getString("task_status"),
                        rs.getString("resource_type"),
                        rs.getObject("resource_id", Long.class),
                        rs.getString("resource_title"),
                        rs.getObject("retry_count", Integer.class),
                        rs.getString("last_error"),
                        isRetryableStatus(rs.getString("task_status")),
                        toLocalDateTime(rs.getTimestamp("created_at"))
                ),
                pageParams.toArray());
        return PageResponse.of(records, safePageNum, safePageSize, total);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private String promptSummaryBaseSql() {
        return """
                select latest.template_code,
                       latest.template_name,
                       latest.scene,
                       latest.id as latest_version_id,
                       latest.version_no as latest_version_no,
                       enabled_template.id as enabled_version_id,
                       enabled_template.version_no as enabled_version_no,
                       case when enabled_template.id is null then 0 else 1 end as has_enabled_version,
                       latest.updated_at as latest_updated_at
                from ai_prompt_template latest
                join (
                    select template_code, max(version_no) as latest_version_no
                    from ai_prompt_template
                    group by template_code
                ) latest_version
                  on latest_version.template_code = latest.template_code
                 and latest_version.latest_version_no = latest.version_no
                left join ai_prompt_template enabled_template
                  on enabled_template.template_code = latest.template_code
                 and enabled_template.enabled = 1
                where 1 = 1
                """;
    }

    private String promptSummaryFilterClause(String keyword, String scene, Boolean enabledOnly, List<Object> params) {
        StringBuilder where = new StringBuilder();
        String normalizedKeyword = trimToNull(keyword);
        String normalizedScene = trimToNull(scene);
        if (normalizedKeyword != null) {
            where.append("""
                     and (
                       latest.template_code like ?
                       or latest.template_name like ?
                       or latest.scene like ?
                     )
                    """);
            String fuzzy = "%" + normalizedKeyword + "%";
            params.add(fuzzy);
            params.add(fuzzy);
            params.add(fuzzy);
        }
        if (normalizedScene != null) {
            where.append(" and latest.scene = ? ");
            params.add(normalizedScene);
        }
        if (Boolean.TRUE.equals(enabledOnly)) {
            where.append(" and enabled_template.id is not null ");
        }
        return where.toString();
    }

    private AiPromptTemplateVersionView latestPromptVersion(String templateCode) {
        List<AiPromptTemplateVersionView> versions = jdbcTemplate.query("""
                        select id, template_code, template_name, scene, system_prompt, user_prompt_wrapper,
                               enabled, version_no, created_by, created_at, updated_at
                        from ai_prompt_template
                        where template_code = ?
                        order by version_no desc, id desc
                        limit 1
                        """,
                (rs, rowNum) -> mapPromptTemplateVersion(rs),
                templateCode);
        if (versions.isEmpty()) {
            throw new BizException(ErrorCodes.NOT_FOUND, "Prompt template not found", HttpStatus.NOT_FOUND);
        }
        return versions.get(0);
    }

    private AiPromptTemplateVersionView promptVersionById(Long id) {
        List<AiPromptTemplateVersionView> versions = jdbcTemplate.query("""
                        select id, template_code, template_name, scene, system_prompt, user_prompt_wrapper,
                               enabled, version_no, created_by, created_at, updated_at
                        from ai_prompt_template
                        where id = ?
                        limit 1
                        """,
                (rs, rowNum) -> mapPromptTemplateVersion(rs),
                id);
        if (versions.isEmpty()) {
            throw new BizException(ErrorCodes.NOT_FOUND, "Prompt template version not found", HttpStatus.NOT_FOUND);
        }
        return versions.get(0);
    }

    private AiPromptTemplateVersionView mapPromptTemplateVersion(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AiPromptTemplateVersionView(
                rs.getLong("id"),
                rs.getString("template_code"),
                rs.getString("template_name"),
                rs.getString("scene"),
                rs.getString("system_prompt"),
                rs.getString("user_prompt_wrapper"),
                rs.getBoolean("enabled"),
                rs.getInt("version_no"),
                rs.getString("created_by"),
                toLocalDateTime(rs.getTimestamp("created_at")),
                toLocalDateTime(rs.getTimestamp("updated_at"))
        );
    }

    private String requestLogFilterClause(String keyword,
                                          String modelCode,
                                          String scene,
                                          Boolean successFlag,
                                          Boolean errorOnly,
                                          LocalDate createdDate,
                                          List<Object> params) {
        StringBuilder where = new StringBuilder();
        String normalizedKeyword = trimToNull(keyword);
        String normalizedModelCode = trimToNull(modelCode);
        String normalizedScene = trimToNull(scene);
        if (normalizedKeyword != null) {
            where.append("""
                     and (
                       r.request_id like ?
                       or coalesce(u.username, '') like ?
                       or coalesce(r.model_code, '') like ?
                       or coalesce(r.error_message, '') like ?
                       or coalesce(p.name, '') like ?
                     )
                    """);
            String fuzzy = "%" + normalizedKeyword + "%";
            params.add(fuzzy);
            params.add(fuzzy);
            params.add(fuzzy);
            params.add(fuzzy);
            params.add(fuzzy);
        }
        if (normalizedModelCode != null) {
            where.append(" and r.model_code = ? ");
            params.add(normalizedModelCode);
        }
        if (normalizedScene != null) {
            where.append(" and r.scene = ? ");
            params.add(normalizedScene);
        }
        if (successFlag != null) {
            where.append(" and r.success_flag = ? ");
            params.add(Boolean.TRUE.equals(successFlag) ? 1 : 0);
        }
        if (Boolean.TRUE.equals(errorOnly)) {
            where.append(" and (r.success_flag = 0 or coalesce(r.error_code, '') <> '' or coalesce(r.error_message, '') <> '') ");
        }
        if (createdDate != null) {
            where.append(" and r.created_at >= ? and r.created_at < ? ");
            params.add(createdDate.atStartOfDay());
            params.add(createdDate.plusDays(1L).atStartOfDay());
        }
        return where.toString();
    }

    private String feedbackFilterClause(String keyword, String feedbackType, LocalDate createdDate, List<Object> params) {
        StringBuilder where = new StringBuilder();
        String normalizedKeyword = trimToNull(keyword);
        String normalizedFeedbackType = trimToNull(feedbackType);
        if (normalizedKeyword != null) {
            where.append("""
                     and (
                       coalesce(u.username, '') like ?
                       or coalesce(f.feedback_note, '') like ?
                       or coalesce(m.content, '') like ?
                       or coalesce(p.name, '') like ?
                     )
                    """);
            String fuzzy = "%" + normalizedKeyword + "%";
            params.add(fuzzy);
            params.add(fuzzy);
            params.add(fuzzy);
            params.add(fuzzy);
        }
        if (normalizedFeedbackType != null) {
            where.append(" and f.feedback_type = ? ");
            params.add(normalizedFeedbackType.toUpperCase(Locale.ROOT));
        }
        if (createdDate != null) {
            where.append(" and f.created_at >= ? and f.created_at < ? ");
            params.add(createdDate.atStartOfDay());
            params.add(createdDate.plusDays(1L).atStartOfDay());
        }
        return where.toString();
    }

    private String indexTaskBaseSql() {
        return """
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
                """;
    }

    private String indexTaskFilterClause(String keyword,
                                         String taskOrigin,
                                         String taskStatus,
                                         Boolean errorOnly,
                                         List<Object> params) {
        StringBuilder where = new StringBuilder();
        String normalizedKeyword = trimToNull(keyword);
        String normalizedTaskOrigin = trimToNull(taskOrigin);
        String normalizedTaskStatus = trimToNull(taskStatus);
        if (normalizedKeyword != null) {
            where.append("""
                     and (
                       coalesce(index_task.task_origin, '') like ?
                       or coalesce(index_task.task_type, '') like ?
                       or coalesce(index_task.resource_type, '') like ?
                       or coalesce(index_task.resource_title, '') like ?
                       or coalesce(index_task.last_error, '') like ?
                     )
                    """);
            String fuzzy = "%" + normalizedKeyword + "%";
            params.add(fuzzy);
            params.add(fuzzy);
            params.add(fuzzy);
            params.add(fuzzy);
            params.add(fuzzy);
        }
        if (normalizedTaskOrigin != null) {
            where.append(" and index_task.task_origin = ? ");
            params.add(normalizedTaskOrigin.toUpperCase(Locale.ROOT));
        }
        if (normalizedTaskStatus != null) {
            where.append(" and index_task.task_status = ? ");
            params.add(normalizedTaskStatus.toUpperCase(Locale.ROOT));
        }
        if (Boolean.TRUE.equals(errorOnly)) {
            where.append(" and coalesce(index_task.last_error, '') <> '' ");
        }
        return where.toString();
    }

    private boolean isRetryableStatus(String taskStatus) {
        String normalized = taskStatus == null ? "" : taskStatus.trim().toUpperCase(Locale.ROOT);
        return "FAILED".equals(normalized) || "NEEDS_REPAIR".equals(normalized);
    }

    private String resolveCurrencyCode(List<AiCostModelBreakdownView> breakdown) {
        if (breakdown.isEmpty()) {
            return "USD";
        }
        String first = breakdown.get(0).currencyCode();
        boolean mixed = breakdown.stream().anyMatch(item -> !first.equalsIgnoreCase(item.currencyCode()));
        return mixed ? "MIXED" : first;
    }

    private long scalar(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private long numberValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private double decimalValue(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0D;
    }

    private String normalizeScene(String value) {
        return requireValue(value, "Scene is required");
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String requireValue(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BizException(ErrorCodes.BAD_REQUEST, message, HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }
}

record AiPromptTemplateSummaryView(
        String templateCode,
        String templateName,
        String scene,
        Long latestVersionId,
        Integer latestVersionNo,
        Long enabledVersionId,
        Integer enabledVersionNo,
        Boolean enabled,
        LocalDateTime updatedAt
) {
}

record AiPromptTemplateVersionView(
        Long id,
        String templateCode,
        String templateName,
        String scene,
        String systemPrompt,
        String userPromptWrapper,
        Boolean enabled,
        Integer versionNo,
        String createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

record AiPromptTemplateCreateRequest(
        @NotBlank @Size(max = 128) String templateCode,
        @NotBlank @Size(max = 255) String templateName,
        @NotBlank @Size(max = 32) String scene,
        @NotBlank String systemPrompt,
        String userPromptWrapper,
        Boolean enabled
) {
}

record AiPromptTemplateVersionCreateRequest(
        @Size(max = 255) String templateName,
        @Size(max = 32) String scene,
        @NotBlank String systemPrompt,
        String userPromptWrapper,
        Boolean enabled
) {
}

record AiPromptTemplateUpdateRequest(
        @NotBlank @Size(max = 255) String templateName,
        @NotBlank @Size(max = 32) String scene,
        @NotBlank String systemPrompt,
        String userPromptWrapper
) {
}

record AiPromptTemplateStatusRequest(@NotNull Boolean enabled) {
}

record AiRequestLogAdminView(
        Long id,
        String requestId,
        Long sessionId,
        Long userId,
        String username,
        Long projectId,
        String projectName,
        String providerCode,
        String modelCode,
        String scene,
        Boolean stream,
        Integer inputTokenCount,
        Integer outputTokenCount,
        Integer totalTokenCount,
        Integer latencyMs,
        Boolean successFlag,
        String answerSource,
        String messageStatus,
        String errorCode,
        String errorMessage,
        LocalDateTime createdAt
) {
}

record AiCostStatsView(
        Integer windowDays,
        Long totalRequests,
        Long successRequests,
        Long failedRequests,
        Long promptTokens,
        Long completionTokens,
        Long totalTokens,
        Long averageLatencyMs,
        String currencyCode,
        Double estimatedCost,
        List<AiCostModelBreakdownView> modelBreakdown
) {
}

record AiCostModelBreakdownView(
        String modelCode,
        String modelName,
        String currencyCode,
        Long requestCount,
        Long totalTokens,
        Double estimatedCost
) {
}

record AiFeedbackAdminView(
        Long id,
        Long messageId,
        Long sessionId,
        Long userId,
        String username,
        String feedbackType,
        String feedbackNote,
        String answerExcerpt,
        Long projectId,
        String projectName,
        String modelCode,
        LocalDateTime createdAt
) {
}

record AiIndexTaskAdminView(
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
