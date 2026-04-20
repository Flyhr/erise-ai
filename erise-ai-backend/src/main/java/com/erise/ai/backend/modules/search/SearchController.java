package com.erise.ai.backend.modules;

import com.erise.ai.backend.common.api.ApiResponse;
import com.erise.ai.backend.common.api.PageResponse;
import com.erise.ai.backend.common.entity.AuditableEntity;
import com.erise.ai.backend.common.exception.BizException;
import com.erise.ai.backend.common.exception.ErrorCodes;
import com.erise.ai.backend.common.util.SecurityUtils;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private static final Set<String> PAGE_SEARCH_SOURCE_TYPES = Set.of("FILE", "DOCUMENT");

    private final JdbcTemplate jdbcTemplate;
    private final ProjectService projectService;
    private final SearchHistoryMapper searchHistoryMapper;
    private final SparseKnowledgeSupport sparseKnowledgeSupport;

    PageResponse<SearchResultView> search(String keyword, Long projectId, long pageNum, long pageSize) {
        var currentUser = SecurityUtils.currentUser();
        String normalizedKeyword = requireKeyword(keyword);
        if (projectId != null) {
            projectService.requireAccessibleProject(projectId);
        }

        List<String> searchTerms = sparseKnowledgeSupport.queryTerms(normalizedKeyword);
        String projectSql = projectScopeSql(currentUser.userId(), currentUser.isAdmin(), projectId);

        List<SearchResultView> rawResults = new ArrayList<>();
        rawResults.addAll(searchSparseKnowledge(currentUser.userId(), currentUser.isAdmin(), projectId, normalizedKeyword));
        rawResults.addAll(searchFileTitleFallback(projectSql, normalizedKeyword, 20));
        rawResults.addAll(searchDocumentTitleFallback(projectSql, normalizedKeyword, 20));

        List<SearchResultView> results = uniqueResults(rawResults, normalizedKeyword, searchTerms);
        recordHistory(currentUser.userId(), normalizedKeyword, projectId);

        int from = (int) Math.max(0, (pageNum - 1) * pageSize);
        int to = Math.min(results.size(), from + (int) pageSize);
        List<SearchResultView> pageRecords = from >= results.size() ? List.of() : results.subList(from, to);
        return PageResponse.of(pageRecords, pageNum, pageSize, results.size());
    }

    List<String> suggest(String keyword, Long projectId) {
        var currentUser = SecurityUtils.currentUser();
        String baseSql = projectScopeSql(currentUser.userId(), currentUser.isAdmin(), projectId);
        String like = "%" + safeKeyword(keyword) + "%";
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
        values.addAll(jdbcTemplate.queryForList(
                "select distinct title from ea_content_item where deleted = 0 and title like ? and project_id in (" + baseSql + ") limit 5",
                String.class,
                like
        ));
        values.addAll(history().stream().map(SearchHistoryView::keyword).limit(5).toList());
        return values.stream().filter(item -> item != null && !item.isBlank()).distinct().limit(12).toList();
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

    /*
     * 废弃说明（2026-04）：
     * AI 助手检索已经迁移到 Python 侧直接查询 Qdrant dense+sparse 一体化索引，
     * 不再使用本类中那套给聊天检索准备的 Java BM25 入口和兜底链路。
     *
     * 为了避免后续误接回旧逻辑，相关方法已经从运行时代码中移除，仅保留下面这段说明：
     * - retrieveKnowledge(...)
     * - searchSparseKnowledgeForRetrieve(...)
     * - searchDocumentKnowledgeFallback(...)
     * - searchContentKnowledgeFallback(...)
     * - searchTempAttachmentFallback(...)
     * - matchKnowledgeScope(...)
     * - matchesAttachment(...)
     *
     * 注意：当前文件里保留下来的 searchSparseKnowledge(...) 仍然服务站内页面搜索，
     * 它不是 AI 助手检索主链的一部分，所以这里不做删除。
     */

    private List<SearchResultView> searchSparseKnowledge(Long userId, boolean admin, Long projectId, String keyword) {
        return sparseKnowledgeSupport.searchRows(
                        userId,
                        !admin,
                        SparseKnowledgeSupport.SCOPE_KB,
                        PAGE_SEARCH_SOURCE_TYPES,
                        keyword,
                        120
                ).stream()
                .filter(row -> projectId == null || (row.projectId() != null && projectId.equals(row.projectId())))
                .map(this::toSearchResult)
                .toList();
    }

    private List<SearchResultView> searchFileTitleFallback(String projectSql, String keyword, int limit) {
        return jdbcTemplate.query("""
                        select id, project_id, file_name, file_ext, mime_type, file_size,
                               upload_status, parse_status, index_status, updated_at
                        from ea_file
                        where deleted = 0
                          and file_name like ?
                          and project_id in (%s)
                          and coalesce(index_status, 'PENDING') <> 'SUCCESS'
                        order by updated_at desc
                        limit ?
                        """.formatted(projectSql),
                (rs, rowNum) -> new SearchResultView(
                        "FILE",
                        rs.getLong("id"),
                        rs.getLong("project_id"),
                        rs.getString("file_name"),
                        rs.getString("mime_type"),
                        null,
                        rs.getTimestamp("updated_at").toLocalDateTime(),
                        null,
                        null,
                        rs.getString("file_ext"),
                        rs.getObject("file_size") == null ? null : rs.getLong("file_size"),
                        rs.getString("upload_status"),
                        rs.getString("parse_status"),
                        rs.getString("index_status"),
                        null
                ),
                "%" + keyword + "%",
                limit
        );
    }

    private List<SearchResultView> searchDocumentTitleFallback(String projectSql, String keyword, int limit) {
        return jdbcTemplate.query("""
                        select d.id,
                               d.project_id,
                               d.title,
                               d.doc_status,
                               left(c.plain_text, 220) as snippet,
                               d.updated_at
                        from ea_document d
                        left join ea_document_content c on c.document_id = d.id and c.deleted = 0
                        left join ea_rag_source rs
                          on rs.scope_type = 'KB'
                         and rs.source_type = 'DOCUMENT'
                         and rs.source_id = d.id
                         and rs.deleted = 0
                        where d.deleted = 0
                          and d.project_id in (%s)
                          and d.title like ?
                          and (rs.id is null or rs.status <> 'READY')
                        order by d.updated_at desc
                        limit ?
                        """.formatted(projectSql),
                (rs, rowNum) -> new SearchResultView(
                        "DOCUMENT",
                        rs.getLong("id"),
                        rs.getLong("project_id"),
                        rs.getString("title"),
                        "DOCUMENT",
                        rs.getString("snippet"),
                        rs.getTimestamp("updated_at").toLocalDateTime(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        rs.getString("doc_status")
                ),
                "%" + keyword + "%",
                limit
        );
    }

    private SearchResultView toSearchResult(SparseSearchRow row) {
        return new SearchResultView(
                row.sourceType(),
                row.sourceId(),
                row.projectId(),
                row.title(),
                "FILE".equalsIgnoreCase(row.sourceType()) ? row.mimeType() : row.sourceType(),
                row.snippet(),
                row.updatedAt(),
                row.pageNo(),
                row.sectionPath(),
                row.fileExt(),
                row.fileSize(),
                row.uploadStatus(),
                row.parseStatus(),
                row.indexStatus(),
                row.docStatus()
        );
    }

    private Comparator<SearchResultView> searchResultComparator(String keyword, List<String> searchTerms) {
        Comparator<LocalDateTime> updatedAtComparator = Comparator.nullsLast(Comparator.reverseOrder());
        return Comparator.comparingDouble((SearchResultView row) ->
                        knowledgeMatchScore(row.title(), row.snippet(), row.sectionPath(), keyword, searchTerms))
                .reversed()
                .thenComparing(SearchResultView::updatedAt, updatedAtComparator);
    }

    private double knowledgeMatchScore(String title,
                                       String snippet,
                                       String sectionPath,
                                       String keyword,
                                       List<String> searchTerms) {
        String normalizedKeyword = safeLower(keyword).trim();
        String safeTitle = safeLower(title);
        String safeSnippet = safeLower(snippet);
        String safeSectionPath = safeLower(sectionPath);
        double score = 0D;
        if (!normalizedKeyword.isBlank()) {
            if (safeTitle.contains(normalizedKeyword)) {
                score += 12D;
            }
            if (safeSectionPath.contains(normalizedKeyword)) {
                score += 7D;
            }
            if (safeSnippet.contains(normalizedKeyword)) {
                score += 9D;
            }
        }
        for (String term : searchTerms) {
            String normalizedTerm = safeLower(term).trim();
            if (normalizedTerm.isBlank()) {
                continue;
            }
            if (safeTitle.contains(normalizedTerm)) {
                score += 3D + Math.min(normalizedTerm.length(), 6) * 0.2D;
            }
            if (safeSectionPath.contains(normalizedTerm)) {
                score += 2.5D;
            }
            if (safeSnippet.contains(normalizedTerm)) {
                score += 2D + Math.min(normalizedTerm.length(), 8) * 0.1D;
            }
        }
        return score;
    }

    private List<SearchResultView> uniqueResults(List<SearchResultView> rawResults,
                                                 String keyword,
                                                 List<String> searchTerms) {
        Comparator<SearchResultView> comparator = searchResultComparator(keyword, searchTerms);
        Map<String, SearchResultView> deduplicated = new LinkedHashMap<>();
        rawResults.stream().sorted(comparator).forEach(result -> {
            String key = result.sourceType() + ":" + result.sourceId();
            deduplicated.putIfAbsent(key, result);
        });
        return new ArrayList<>(deduplicated.values());
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

    private String requireKeyword(String keyword) {
        String normalized = safeKeyword(keyword);
        if (normalized.isBlank()) {
            throw new BizException(ErrorCodes.SEARCH_ERROR, "Keyword is required", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String safeKeyword(String keyword) {
        return keyword == null ? "" : keyword.trim();
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase();
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
        LocalDateTime updatedAt,
        Integer pageNo,
        String sectionPath,
        String fileExt,
        Long fileSize,
        String uploadStatus,
        String parseStatus,
        String indexStatus,
        String docStatus
) {
}

record SearchHistoryView(String keyword, Long projectId, LocalDateTime createdAt) {
}
