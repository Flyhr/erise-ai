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
        List<String> parts = new ArrayList<>();
        if (includeFiles) {
            parts.add(fileSql(projectId, keyword, knowledgeOnly, userId, admin, params));
        }
        if (includeDocuments) {
            parts.add(documentSql(projectId, keyword, userId, admin, params));
        }
        if (parts.isEmpty()) {
            return """
                    select
                      'FILE' as asset_type,
                      0 as asset_id,
                      0 as project_id,
                      '' as title,
                      null as summary,
                      null as file_ext,
                      null as mime_type,
                      null as file_size,
                      null as parse_status,
                      null as index_status,
                      null as parse_error_message,
                      null as doc_status,
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
                  f.file_name as title,
                  null as summary,
                  f.file_ext,
                  f.mime_type,
                  f.file_size,
                  f.parse_status,
                  f.index_status,
                  (
                    select t.last_error
                    from ea_file_parse_task t
                    where t.file_id = f.id
                    order by t.updated_at desc, t.id desc
                    limit 1
                  ) as parse_error_message,
                  null as doc_status,
                  f.created_at,
                  f.updated_at
                from ea_file f
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
                  d.title as title,
                  d.summary as summary,
                  null as file_ext,
                  'DOCUMENT' as mime_type,
                  null as file_size,
                  null as parse_status,
                  null as index_status,
                  null as parse_error_message,
                  d.doc_status,
                  d.created_at,
                  d.updated_at
                from ea_document d
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

    private KnowledgeAssetView mapAsset(ResultSet rs) throws SQLException {
        return new KnowledgeAssetView(
                rs.getString("asset_type"),
                rs.getLong("asset_id"),
                rs.getLong("project_id"),
                rs.getString("title"),
                rs.getString("summary"),
                rs.getString("file_ext"),
                rs.getString("mime_type"),
                rs.getObject("file_size") == null ? null : rs.getLong("file_size"),
                rs.getString("parse_status"),
                rs.getString("index_status"),
                rs.getString("parse_error_message"),
                rs.getString("doc_status"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }
}

record KnowledgeAssetView(
        String assetType,
        Long assetId,
        Long projectId,
        String title,
        String summary,
        String fileExt,
        String mimeType,
        Long fileSize,
        String parseStatus,
        String indexStatus,
        String parseErrorMessage,
        String docStatus,
        java.time.LocalDateTime createdAt,
        java.time.LocalDateTime updatedAt
) {
}
