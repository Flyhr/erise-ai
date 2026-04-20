package com.erise.ai.backend.modules;

import com.erise.ai.backend.common.api.ApiResponse;
import com.erise.ai.backend.common.api.PageResponse;
import com.erise.ai.backend.common.exception.BizException;
import com.erise.ai.backend.common.exception.ErrorCodes;
import com.erise.ai.backend.common.util.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspace")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @GetMapping("/recent")
    public ApiResponse<PageResponse<WorkspaceRecentItemView>> recent(@RequestParam String mode,
            @RequestParam(required = false) String assetType,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        return ApiResponse.success(workspaceService.recent(mode, assetType, pageNum, pageSize));
    }

    @PostMapping("/activities/view")
    public ApiResponse<Void> track(@Valid @RequestBody WorkspaceActivityRequest request) {
        workspaceService.track(request);
        return ApiResponse.success("success", null);
    }
}

@Service
@RequiredArgsConstructor
class WorkspaceService {

    private static final List<String> VIEW_ACTIONS = List.of(
            "FILE_PREVIEW", "FILE_DETAIL_OPEN", "FILE_EDIT_OPEN", "DOCUMENT_VIEW", "DOCUMENT_EDIT_OPEN");
    private static final List<String> EDIT_ACTIONS = List.of(
            "FILE_EDIT_SAVE", "DOCUMENT_SAVE", "DOCUMENT_PUBLISH");

    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;
    private final FileService fileService;
    private final DocumentService documentService;
    private final FileParseStatusSupport fileParseStatusSupport;

    PageResponse<WorkspaceRecentItemView> recent(String mode, String assetType, long pageNum, long pageSize) {
        var currentUser = SecurityUtils.currentUser();
        List<String> actions = "edited".equalsIgnoreCase(mode) ? EDIT_ACTIONS : VIEW_ACTIONS;
        List<Object> params = new ArrayList<>();
        params.add(currentUser.userId());
        params.addAll(actions);
        StringBuilder sql = new StringBuilder("""
                select action_code, resource_type, resource_id, created_at
                from ea_audit_log
                where deleted = 0 and operator_user_id = ?
                  and action_code in (%s)
                """.formatted("?, ".repeat(Math.max(actions.size() - 1, 0)) + "?"));
        if (assetType != null && !assetType.isBlank()) {
            sql.append(" and resource_type = ? ");
            params.add(assetType.trim().toUpperCase());
        }
        sql.append(" order by created_at desc limit 400 ");

        List<AuditRecentRow> rows = jdbcTemplate.query(
                sql.toString(),
                (rs, rowNum) -> new AuditRecentRow(
                        rs.getString("action_code"),
                        rs.getString("resource_type"),
                        rs.getLong("resource_id"),
                        rs.getTimestamp("created_at").toLocalDateTime()),
                params.toArray());

        Map<String, WorkspaceRecentItemView> deduplicated = new LinkedHashMap<>();
        for (AuditRecentRow row : rows) {
            String key = row.resourceType() + ":" + row.resourceId();
            if (deduplicated.containsKey(key)) {
                continue;
            }
            WorkspaceRecentItemView item = switch (row.resourceType()) {
                case "FILE" -> loadFileRecent(currentUser.userId(), currentUser.isAdmin(), row);
                case "DOCUMENT" -> loadDocumentRecent(currentUser.userId(), currentUser.isAdmin(), row);
                default -> null;
            };
            if (item != null) {
                deduplicated.put(key, item);
            }
        }

        List<WorkspaceRecentItemView> results = new ArrayList<>(deduplicated.values());
        int from = (int) Math.max((pageNum - 1) * pageSize, 0);
        int to = Math.min(results.size(), from + (int) Math.max(pageSize, 1));
        List<WorkspaceRecentItemView> pageRecords = from >= results.size() ? List.of() : results.subList(from, to);
        return PageResponse.of(pageRecords, pageNum, pageSize, results.size());
    }

    void track(WorkspaceActivityRequest request) {
        var currentUser = SecurityUtils.currentUser();
        switch (request.assetType().trim().toUpperCase()) {
            case "FILE" -> fileService.detail(request.assetId());
            case "DOCUMENT" -> documentService.detail(request.assetId());
            default -> throw new BizException(ErrorCodes.BAD_REQUEST, "Unsupported asset type: " + request.assetType());
        }
        auditLogService.log(currentUser, request.actionCode().trim().toUpperCase(),
                request.assetType().trim().toUpperCase(), request.assetId(), null);
    }

    private WorkspaceRecentItemView loadFileRecent(Long userId, boolean admin, AuditRecentRow row) {
        List<WorkspaceRecentItemView> records = jdbcTemplate.query("""
                select
                  'FILE' as asset_type,
                  f.id as asset_id,
                  f.project_id,
                  f.file_name as title,
                  null as summary,
                  f.file_ext,
                  f.mime_type,
                  f.file_size,
                  null as doc_status,
                  f.parse_status,
                  f.index_status,
                  (
                    select t.last_error
                    from ea_file_parse_task t
                    where t.deleted = 0
                      and t.file_id = f.id
                    order by t.updated_at desc, t.id desc
                    limit 1
                  ) as parse_error_message
                from ea_file f
                where f.deleted = 0 and f.id = ?
                """ + (admin ? "" : " and f.owner_user_id = ? "),
                (rs, rowNum) -> mapRecent(rs, row),
                admin
                        ? new Object[] { row.resourceId() }
                        : new Object[] { row.resourceId(), userId });
        return records.isEmpty() ? null : records.get(0);
    }

    private WorkspaceRecentItemView loadDocumentRecent(Long userId, boolean admin, AuditRecentRow row) {
        List<WorkspaceRecentItemView> records = jdbcTemplate.query("""
                select
                  'DOCUMENT' as asset_type,
                  d.id as asset_id,
                  d.project_id,
                  d.title as title,
                  d.summary as summary,
                  null as file_ext,
                  'DOCUMENT' as mime_type,
                  null as file_size,
                  d.doc_status,
                  'SKIPPED' as parse_status,
                  coalesce((
                    select case
                      when upper(coalesce(s.status, '')) = 'READY' then 'READY'
                      when upper(coalesce(s.status, '')) = 'PROCESSING' then 'PROCESSING'
                      when upper(coalesce(s.status, '')) in ('FAILED', 'NEEDS_REPAIR') then 'FAILED'
                      when upper(coalesce(s.status, '')) = 'DELETED' then 'DELETED'
                      else 'PENDING'
                    end
                    from ea_rag_source s
                    where s.deleted = 0
                      and s.scope_type = 'KB'
                      and s.source_type = 'DOCUMENT'
                      and s.source_id = d.id
                      and s.owner_user_id = d.owner_user_id
                      and coalesce(s.session_id, 0) = 0
                    order by s.updated_at desc, s.id desc
                    limit 1
                  ), 'PENDING') as index_status,
                  (
                    select s.last_error
                    from ea_rag_source s
                    where s.deleted = 0
                      and s.scope_type = 'KB'
                      and s.source_type = 'DOCUMENT'
                      and s.source_id = d.id
                      and s.owner_user_id = d.owner_user_id
                      and coalesce(s.session_id, 0) = 0
                    order by s.updated_at desc, s.id desc
                    limit 1
                  ) as parse_error_message
                from ea_document d
                where d.deleted = 0 and d.id = ?
                """ + (admin ? "" : " and d.owner_user_id = ? "),
                (rs, rowNum) -> mapRecent(rs, row),
                admin
                        ? new Object[] { row.resourceId() }
                        : new Object[] { row.resourceId(), userId });
        return records.isEmpty() ? null : records.get(0);
    }

    private WorkspaceRecentItemView mapRecent(ResultSet rs, AuditRecentRow row) throws SQLException {
        String assetType = rs.getString("asset_type");
        String parseStatus = rs.getString("parse_status");
        String indexStatus = rs.getString("index_status");
        String parseErrorMessage = rs.getString("parse_error_message");
        if ("FILE".equalsIgnoreCase(assetType)) {
            FileParseStatusView statusView = fileParseStatusSupport.resolve(
                    rs.getLong("asset_id"),
                    parseStatus,
                    indexStatus
            );
            parseStatus = statusView.parseStatus();
            indexStatus = statusView.indexStatus();
            parseErrorMessage = statusView.parseErrorMessage();
        }
        return new WorkspaceRecentItemView(
                assetType,
                rs.getLong("asset_id"),
                rs.getLong("project_id"),
                rs.getString("title"),
                rs.getString("summary"),
                row.actionCode(),
                row.createdAt(),
                rs.getString("file_ext"),
                rs.getString("mime_type"),
                rs.getObject("file_size") == null ? null : rs.getLong("file_size"),
                rs.getString("doc_status"),
                parseStatus,
                indexStatus,
                parseErrorMessage);
    }
}

record WorkspaceActivityRequest(@NotBlank String assetType, @NotNull Long assetId, @NotBlank String actionCode) {
}

record WorkspaceRecentItemView(
        String assetType,
        Long assetId,
        Long projectId,
        String title,
        String summary,
        String actionCode,
        LocalDateTime lastActionAt,
        String fileExt,
        String mimeType,
        Long fileSize,
        String docStatus,
        String parseStatus,
        String indexStatus,
        String parseErrorMessage) {
}

record AuditRecentRow(String actionCode, String resourceType, Long resourceId, LocalDateTime createdAt) {
}
