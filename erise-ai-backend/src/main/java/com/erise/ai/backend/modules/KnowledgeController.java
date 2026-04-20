package com.erise.ai.backend.modules;

import com.erise.ai.backend.common.api.ApiResponse;
import com.erise.ai.backend.common.api.PageResponse;
import com.erise.ai.backend.common.util.SecurityUtils;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeQueryService knowledgeQueryService;

    @GetMapping("/assets")
    public ApiResponse<PageResponse<KnowledgeAssetView>> assets(@RequestParam(required = false) String type,
                                                                @RequestParam(required = false) Long projectId,
                                                                @RequestParam(required = false) String q,
                                                                @RequestParam(required = false, defaultValue = "false") boolean knowledgeOnly,
                                                                @RequestParam(defaultValue = "1") long pageNum,
                                                                @RequestParam(defaultValue = "10") long pageSize) {
        return ApiResponse.success(knowledgeQueryService.assets(type, projectId, q, knowledgeOnly, pageNum, pageSize));
    }
}

@Service
@RequiredArgsConstructor
class KnowledgeQueryService {

    private final JdbcTemplate jdbcTemplate;
    private final FileParseStatusSupport fileParseStatusSupport;
    private final ProjectService projectService;

    PageResponse<KnowledgeAssetView> assets(String type, Long projectId, String keyword, boolean knowledgeOnly, long pageNum, long pageSize) {
        var currentUser = SecurityUtils.currentUser();
        if (projectId != null) {
            projectService.requireAccessibleProject(projectId);
        }

        List<Object> params = new ArrayList<>();
        String unionSql = buildUnionSql(type, projectId, keyword, knowledgeOnly, currentUser.userId(), currentUser.isAdmin(), params);
        String countSql = "select count(*) from (" + unionSql + ") assets";
        long total = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(Math.max((pageNum - 1) * pageSize, 0));
        pageParams.add(Math.max(pageSize, 1));
        List<KnowledgeAssetView> records = jdbcTemplate.query(
                "select * from (" + unionSql + ") assets order by updated_at desc limit ?, ?",
                (rs, rowNum) -> mapAsset(rs),
                pageParams.toArray()
        );
        return PageResponse.of(records, pageNum, pageSize, total);
    }

    private String buildUnionSql(String type,
                                 Long projectId,
                                 String keyword,
                                 boolean knowledgeOnly,
                                 Long userId,
                                 boolean admin,
                                 List<Object> params) {
        boolean includeFiles = type == null || type.isBlank() || "FILE".equalsIgnoreCase(type);
        boolean includeDocuments = type == null || type.isBlank() || "DOCUMENT".equalsIgnoreCase(type);
        boolean includeContents = type == null || type.isBlank() || "CONTENT".equalsIgnoreCase(type);
        List<String> parts = new ArrayList<>();
        if (includeFiles) {
            parts.add(fileSql(projectId, keyword, knowledgeOnly, userId, admin, params));
        }
        if (includeDocuments) {
            parts.add(documentSql(projectId, keyword, userId, admin, params));
        }
        if (includeContents) {
            parts.add(contentSql(projectId, keyword, userId, admin, params));
        }
        if (parts.isEmpty()) {
            return """
                    select
                      'FILE' as asset_type,
                      0 as asset_id,
                      0 as project_id,
                      0 as owner_user_id,
                      '' as owner_name,
                      '' as title,
                      null as summary,
                      null as file_ext,
                      null as mime_type,
                      null as file_size,
                      null as parse_status,
                      null as review_status,
                      null as index_status,
                      null as parse_error_message,
                      null as review_comment,
                      null as doc_status,
                      null as item_type,
                      now() as created_at,
                      now() as updated_at
                    where 1 = 0
                    """;
        }
        return String.join(" union all ", parts);
    }

    private String fileSql(Long projectId,
                           String keyword,
                           boolean knowledgeOnly,
                           Long userId,
                           boolean admin,
                           List<Object> params) {
        StringBuilder sql = new StringBuilder("""
                select
                  'FILE' as asset_type,
                  f.id as asset_id,
                  f.project_id,
                  f.owner_user_id as owner_user_id,
                  coalesce(up.display_name, u.username) as owner_name,
                  f.file_name as title,
                  null as summary,
                  f.file_ext,
                  f.mime_type,
                  f.file_size,
                  f.parse_status,
                  f.review_status,
                  f.index_status,
                  (
                    select t.last_error
                    from ea_file_parse_task t
                    where t.file_id = f.id
                    order by t.updated_at desc, t.id desc
                    limit 1
                  ) as parse_error_message,
                  f.review_comment,
                  null as doc_status,
                  null as item_type,
                  f.created_at,
                  f.updated_at
                from ea_file f
                left join ea_user u on u.id = f.owner_user_id and u.deleted = 0
                left join ea_user_profile up on up.user_id = f.owner_user_id and up.deleted = 0
                where f.deleted = 0
                """);
        if (!admin) {
            sql.append(" and f.owner_user_id = ? ");
            params.add(userId);
        }
        if (projectId != null) {
            sql.append(" and f.project_id = ? ");
            params.add(projectId);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" and f.file_name like ? ");
            params.add("%" + keyword.trim() + "%");
        }
        if (knowledgeOnly) {
            sql.append(" and lower(coalesce(f.file_ext, '')) in ('doc', 'docx', 'pdf', 'md', 'txt') ");
        }
        return sql.toString();
    }

    private String documentSql(Long projectId,
                               String keyword,
                               Long userId,
                               boolean admin,
                               List<Object> params) {
        StringBuilder sql = new StringBuilder("""
                select
                  'DOCUMENT' as asset_type,
                  d.id as asset_id,
                  d.project_id,
                  d.owner_user_id as owner_user_id,
                  coalesce(up.display_name, u.username) as owner_name,
                  d.title as title,
                  d.summary as summary,
                  null as file_ext,
                  'DOCUMENT' as mime_type,
                  null as file_size,
                  'SKIPPED' as parse_status,
                  null as review_status,
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
                  ) as parse_error_message,
                  null as review_comment,
                  d.doc_status,
                  null as item_type,
                  d.created_at,
                  d.updated_at
                from ea_document d
                left join ea_user u on u.id = d.owner_user_id and u.deleted = 0
                left join ea_user_profile up on up.user_id = d.owner_user_id and up.deleted = 0
                where d.deleted = 0
                """);
        if (!admin) {
            sql.append(" and d.owner_user_id = ? ");
            params.add(userId);
        }
        if (projectId != null) {
            sql.append(" and d.project_id = ? ");
            params.add(projectId);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" and (d.title like ? or d.summary like ?) ");
            params.add("%" + keyword.trim() + "%");
            params.add("%" + keyword.trim() + "%");
        }
        return sql.toString();
    }

    private String contentSql(Long projectId,
                              String keyword,
                              Long userId,
                              boolean admin,
                              List<Object> params) {
        StringBuilder sql = new StringBuilder("""
                select
                  'CONTENT' as asset_type,
                  ci.id as asset_id,
                  ci.project_id,
                  ci.owner_user_id as owner_user_id,
                  coalesce(up.display_name, u.username) as owner_name,
                  ci.title as title,
                  ci.summary as summary,
                  null as file_ext,
                  'CONTENT' as mime_type,
                  null as file_size,
                  'SKIPPED' as parse_status,
                  null as review_status,
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
                      and s.source_type = ci.item_type
                      and s.source_id = ci.id
                      and s.owner_user_id = ci.owner_user_id
                      and coalesce(s.session_id, 0) = 0
                    order by s.updated_at desc, s.id desc
                    limit 1
                  ), 'PENDING') as index_status,
                  (
                    select s.last_error
                    from ea_rag_source s
                    where s.deleted = 0
                      and s.scope_type = 'KB'
                      and s.source_type = ci.item_type
                      and s.source_id = ci.id
                      and s.owner_user_id = ci.owner_user_id
                      and coalesce(s.session_id, 0) = 0
                    order by s.updated_at desc, s.id desc
                    limit 1
                  ) as parse_error_message,
                  null as review_comment,
                  null as doc_status,
                  ci.item_type as item_type,
                  ci.created_at,
                  ci.updated_at
                from ea_content_item ci
                left join ea_user u on u.id = ci.owner_user_id and u.deleted = 0
                left join ea_user_profile up on up.user_id = ci.owner_user_id and up.deleted = 0
                where ci.deleted = 0
                """);
        if (!admin) {
            sql.append(" and ci.owner_user_id = ? ");
            params.add(userId);
        }
        if (projectId != null) {
            sql.append(" and ci.project_id = ? ");
            params.add(projectId);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" and (ci.title like ? or ci.summary like ? or ci.plain_text like ?) ");
            String likeKeyword = "%" + keyword.trim() + "%";
            params.add(likeKeyword);
            params.add(likeKeyword);
            params.add(likeKeyword);
        }
        return sql.toString();
    }

    private KnowledgeAssetView mapAsset(ResultSet rs) throws SQLException {
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
        return new KnowledgeAssetView(
                assetType,
                rs.getLong("asset_id"),
                rs.getLong("project_id"),
                rs.getObject("owner_user_id") == null ? null : rs.getLong("owner_user_id"),
                rs.getString("owner_name"),
                rs.getString("title"),
                rs.getString("summary"),
                rs.getString("file_ext"),
                rs.getString("mime_type"),
                rs.getObject("file_size") == null ? null : rs.getLong("file_size"),
                parseStatus,
                rs.getString("review_status"),
                indexStatus,
                parseErrorMessage,
                rs.getString("review_comment"),
                rs.getString("doc_status"),
                rs.getString("item_type"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }
}

record KnowledgeAssetView(
        String assetType,
        Long assetId,
        Long projectId,
        Long ownerUserId,
        String ownerName,
        String title,
        String summary,
        String fileExt,
        String mimeType,
        Long fileSize,
        String parseStatus,
        String reviewStatus,
        String indexStatus,
        String parseErrorMessage,
        String reviewComment,
        String docStatus,
        String itemType,
        java.time.LocalDateTime createdAt,
        java.time.LocalDateTime updatedAt
) {
}
