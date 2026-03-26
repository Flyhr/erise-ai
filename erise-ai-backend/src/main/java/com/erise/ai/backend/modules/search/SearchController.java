package com.erise.ai.backend.modules;

import com.erise.ai.backend.common.api.ApiResponse;
import com.erise.ai.backend.common.api.PageResponse;
import com.erise.ai.backend.common.entity.AuditableEntity;
import com.erise.ai.backend.common.exception.BizException;
import com.erise.ai.backend.common.exception.ErrorCodes;
import com.erise.ai.backend.common.util.SecurityUtils;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    public ApiResponse<PageResponse<SearchResultView>> search(@RequestParam String q,
                                                              @RequestParam(required = false) Long projectId,
                                                              @RequestParam(defaultValue = "1") long pageNum,
                                                              @RequestParam(defaultValue = "10") long pageSize) {
        return ApiResponse.success(searchService.search(q, projectId, pageNum, pageSize));
    }

    @GetMapping("/suggest")
    public ApiResponse<List<String>> suggest(@RequestParam String q, @RequestParam(required = false) Long projectId) {
        return ApiResponse.success(searchService.suggest(q, projectId));
    }

    @GetMapping("/history")
    public ApiResponse<List<SearchHistoryView>> history() {
        return ApiResponse.success(searchService.history());
    }
}

@Service
@RequiredArgsConstructor
class SearchService {

    private final JdbcTemplate jdbcTemplate;
    private final ProjectService projectService;
    private final SearchHistoryMapper searchHistoryMapper;

    PageResponse<SearchResultView> search(String keyword, Long projectId, long pageNum, long pageSize) {
        var currentUser = SecurityUtils.currentUser();
        if (keyword == null || keyword.isBlank()) {
            throw new BizException(ErrorCodes.SEARCH_ERROR, "Keyword is required", HttpStatus.BAD_REQUEST);
        }
        if (projectId != null) {
            projectService.requireAccessibleProject(projectId);
        }
        String projectSql = projectScopeSql(currentUser.userId(), currentUser.isAdmin(), projectId);
        List<SearchResultView> rawResults = new ArrayList<>();
        rawResults.addAll(searchFiles(keyword, projectSql));
        rawResults.addAll(searchDocuments(keyword, projectSql));
        rawResults.addAll(searchChunks(keyword, projectSql));
        List<SearchResultView> results = uniqueResults(rawResults);
        recordHistory(currentUser.userId(), keyword, projectId);
        int from = (int) Math.max(0, (pageNum - 1) * pageSize);
        int to = Math.min(results.size(), from + (int) pageSize);
        List<SearchResultView> pageRecords = from >= results.size() ? List.of() : results.subList(from, to);
        return PageResponse.of(pageRecords, pageNum, pageSize, results.size());
    }

    List<String> suggest(String keyword, Long projectId) {
        var currentUser = SecurityUtils.currentUser();
        String baseSql = projectScopeSql(currentUser.userId(), currentUser.isAdmin(), projectId);
        String like = "%" + keyword + "%";
        List<String> values = new ArrayList<>();
        values.addAll(jdbcTemplate.queryForList(
                "select distinct title from ea_document where deleted = 0 and title like ? and project_id in (" + baseSql + ") limit 5",
                String.class,
                like
        ));
        values.addAll(jdbcTemplate.queryForList(
                "select distinct file_name from ea_file where deleted = 0 and file_name like ? and project_id in (" + baseSql + ") limit 5",
                String.class,
                like
        ));
        values.addAll(history().stream().map(SearchHistoryView::keyword).limit(5).toList());
        return values.stream().distinct().limit(10).toList();
    }

    List<SearchHistoryView> history() {
        var currentUser = SecurityUtils.currentUser();
        return searchHistoryMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SearchHistoryEntity>()
                                .eq(SearchHistoryEntity::getUserId, currentUser.userId())
                                .orderByDesc(SearchHistoryEntity::getCreatedAt)
                                .last("limit 20")
                ).stream()
                .map(item -> new SearchHistoryView(item.getKeyword(), item.getProjectId(), item.getCreatedAt()))
                .toList();
    }

    List<SearchResultView> retrieveKnowledge(Long userId, Long projectId, String keyword, int limit) {
        List<SearchResultView> chunks = jdbcTemplate.query("""
                select source_type, source_id, source_title, chunk_text, page_no, project_id
                from ea_knowledge_chunk
                where deleted = 0 and owner_user_id = ? and project_id = ? and chunk_text like ?
                order by updated_at desc
                limit ?
                """, (rs, rowNum) -> mapChunk(rs), userId, projectId, "%" + keyword + "%", Math.max(limit, 1));
        if (!chunks.isEmpty()) {
            return chunks;
        }
        return searchDocumentKnowledge(userId, projectId, keyword, limit);
    }

    private List<SearchResultView> searchFiles(String keyword, String projectSql) {
        return jdbcTemplate.query("""
                        select id, project_id, file_name, mime_type, updated_at
                        from ea_file
                        where deleted = 0 and file_name like ? and project_id in (%s)
                        order by updated_at desc
                        limit 20
                        """.formatted(projectSql),
                (rs, rowNum) -> new SearchResultView("FILE", rs.getLong("id"), rs.getLong("project_id"),
                        rs.getString("file_name"), rs.getString("mime_type"), null, rs.getTimestamp("updated_at").toLocalDateTime()),
                "%" + keyword + "%");
    }

    private List<SearchResultView> searchDocuments(String keyword, String projectSql) {
        return jdbcTemplate.query("""
                        select d.id, d.project_id, d.title, left(c.plain_text, 200) as snippet, d.updated_at
                        from ea_document d
                        join ea_document_content c on c.document_id = d.id and c.deleted = 0
                        where d.deleted = 0 and d.project_id in (%s) and (d.title like ? or c.plain_text like ?)
                        order by d.updated_at desc
                        limit 20
                        """.formatted(projectSql),
                (rs, rowNum) -> new SearchResultView("DOCUMENT", rs.getLong("id"), rs.getLong("project_id"),
                        rs.getString("title"), "DOCUMENT", rs.getString("snippet"), rs.getTimestamp("updated_at").toLocalDateTime()),
                "%" + keyword + "%", "%" + keyword + "%");
    }

    private List<SearchResultView> searchChunks(String keyword, String projectSql) {
        return jdbcTemplate.query("""
                        select source_type, source_id, project_id, source_title, left(chunk_text, 200) as snippet, updated_at
                        from ea_knowledge_chunk
                        where deleted = 0 and project_id in (%s) and chunk_text like ?
                        order by updated_at desc
                        limit 20
                        """.formatted(projectSql),
                (rs, rowNum) -> new SearchResultView(rs.getString("source_type"), rs.getLong("source_id"), rs.getLong("project_id"),
                        rs.getString("source_title"), "KNOWLEDGE", rs.getString("snippet"), rs.getTimestamp("updated_at").toLocalDateTime()),
                "%" + keyword + "%");
    }

    private List<SearchResultView> searchDocumentKnowledge(Long userId, Long projectId, String keyword, int limit) {
        return jdbcTemplate.query("""
                        select d.id, d.project_id, d.title, left(c.plain_text, 300) as snippet, d.updated_at
                        from ea_document d
                        join ea_document_content c on c.document_id = d.id and c.deleted = 0
                        where d.deleted = 0 and d.owner_user_id = ? and d.project_id = ? and (d.title like ? or c.plain_text like ?)
                        order by d.updated_at desc
                        limit ?
                        """,
                (rs, rowNum) -> new SearchResultView("DOCUMENT", rs.getLong("id"), rs.getLong("project_id"),
                        rs.getString("title"), "DOCUMENT", rs.getString("snippet"), rs.getTimestamp("updated_at").toLocalDateTime()),
                userId, projectId, "%" + keyword + "%", "%" + keyword + "%", Math.max(limit, 1));
    }

    private SearchResultView mapChunk(ResultSet rs) throws SQLException {
        return new SearchResultView(
                rs.getString("source_type"),
                rs.getLong("source_id"),
                rs.getLong("project_id"),
                rs.getString("source_title"),
                "KNOWLEDGE",
                rs.getString("chunk_text"),
                null
        );
    }

    private List<SearchResultView> uniqueResults(List<SearchResultView> rawResults) {
        Comparator<SearchResultView> comparator = Comparator.comparing(SearchResultView::updatedAt,
                Comparator.nullsLast(Comparator.reverseOrder()));
        Map<String, SearchResultView> deduplicated = new LinkedHashMap<>();
        rawResults.stream().sorted(comparator).forEach(result -> {
            String key = result.sourceType() + ":" + result.sourceId();
            SearchResultView existing = deduplicated.get(key);
            if (existing == null || shouldReplace(existing, result)) {
                deduplicated.put(key, result);
            }
        });
        return new ArrayList<>(deduplicated.values());
    }

    private boolean shouldReplace(SearchResultView existing, SearchResultView candidate) {
        return (existing.snippet() == null || existing.snippet().isBlank())
                && candidate.snippet() != null
                && !candidate.snippet().isBlank();
    }

    private void recordHistory(Long userId, String keyword, Long projectId) {
        SearchHistoryEntity entity = new SearchHistoryEntity();
        entity.setUserId(userId);
        entity.setKeyword(keyword);
        entity.setProjectId(projectId);
        entity.setCreatedBy(userId);
        entity.setUpdatedBy(userId);
        searchHistoryMapper.insert(entity);
    }

    private String projectScopeSql(Long userId, boolean admin, Long projectId) {
        if (projectId != null) {
            return String.valueOf(projectId);
        }
        if (admin) {
            return "select id from ea_project where deleted = 0";
        }
        return "select id from ea_project where deleted = 0 and owner_user_id = " + userId;
    }
}

interface SearchHistoryMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<SearchHistoryEntity> {
}

@lombok.Data
@com.baomidou.mybatisplus.annotation.TableName("ea_search_history")
class SearchHistoryEntity extends AuditableEntity {

    @com.baomidou.mybatisplus.annotation.TableId(type = com.baomidou.mybatisplus.annotation.IdType.AUTO)
    private Long id;
    private Long userId;
    private String keyword;
    private Long projectId;
}

record SearchResultView(
        String sourceType,
        Long sourceId,
        Long projectId,
        String title,
        String mimeType,
        String snippet,
        java.time.LocalDateTime updatedAt
) {
}

record SearchHistoryView(String keyword, Long projectId, java.time.LocalDateTime createdAt) {
}
