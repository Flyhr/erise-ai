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
import java.util.HashSet;
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

    List<SearchResultView> retrieveKnowledge(Long userId,
                                             List<Long> projectScopeIds,
                                             List<InternalKnowledgeAttachment> attachments,
                                             String keyword,
                                             int limit) {
        String normalizedKeyword = requireKeyword(keyword);
        int safeLimit = Math.max(limit, 1);
        List<String> searchTerms = sparseKnowledgeSupport.queryTerms(normalizedKeyword);
        Set<Long> projectSet = new HashSet<>(projectScopeIds == null ? List.of() : projectScopeIds);
        List<InternalKnowledgeAttachment> safeAttachments = attachments == null ? List.of() : attachments;

        List<SearchResultView> rawResults = new ArrayList<>();
        rawResults.addAll(searchSparseKnowledgeForRetrieve(userId, projectSet, safeAttachments, normalizedKeyword, safeLimit));
        List<SearchResultView> sparseResults = uniqueResults(rawResults, normalizedKeyword, searchTerms)
                .stream()
                .limit(safeLimit)
                .toList();
        if (!sparseResults.isEmpty()) {
            return sparseResults;
        }

        List<SearchResultView> fallbackResults = new ArrayList<>();
        for (Long projectId : projectSet) {
            fallbackResults.addAll(searchDocumentKnowledgeFallback(userId, projectId, normalizedKeyword, safeLimit));
            fallbackResults.addAll(searchContentKnowledgeFallback(userId, projectId, normalizedKeyword, safeLimit));
        }
        fallbackResults.addAll(searchTempAttachmentFallback(userId, safeAttachments, normalizedKeyword, safeLimit));
        return uniqueResults(fallbackResults, normalizedKeyword, searchTerms)
                .stream()
                .limit(safeLimit)
                .toList();
    }

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

    private List<SearchResultView> searchSparseKnowledgeForRetrieve(Long userId,
                                                                    Set<Long> projectScopeIds,
                                                                    List<InternalKnowledgeAttachment> attachments,
                                                                    String keyword,
                                                                    int limit) {
        List<SearchResultView> results = new ArrayList<>();
        results.addAll(sparseKnowledgeSupport.searchRows(
                        userId,
                        true,
                        SparseKnowledgeSupport.SCOPE_KB,
                        null,
                        keyword,
                        Math.max(limit * 8, 80)
                ).stream()
                .filter(row -> matchKnowledgeScope(row, projectScopeIds, attachments))
                .map(this::toSearchResult)
                .toList());
        results.addAll(sparseKnowledgeSupport.searchRows(
                        userId,
                        true,
                        SparseKnowledgeSupport.SCOPE_TEMP,
                        Set.of("TEMP_FILE"),
                        keyword,
                        Math.max(limit * 4, 40)
                ).stream()
                .filter(row -> matchKnowledgeScope(row, projectScopeIds, attachments))
                .map(this::toSearchResult)
                .toList());
        return results;
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

    private List<SearchResultView> searchDocumentKnowledgeFallback(Long userId, Long projectId, String keyword, int limit) {
        return jdbcTemplate.query("""
                        select d.id,
                               d.project_id,
                               d.title,
                               left(c.plain_text, 300) as snippet,
                               d.updated_at
                        from ea_document d
                        left join ea_document_content c on c.document_id = d.id and c.deleted = 0
                        left join ea_rag_source rs
                          on rs.scope_type = 'KB'
                         and rs.source_type = 'DOCUMENT'
                         and rs.source_id = d.id
                         and rs.deleted = 0
                        where d.deleted = 0
                          and d.owner_user_id = ?
                          and d.project_id = ?
                          and d.title like ?
                          and (rs.id is null or rs.status <> 'READY')
                        order by d.updated_at desc
                        limit ?
                        """,
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
                        null
                ),
                userId,
                projectId,
                "%" + keyword + "%",
                Math.max(limit, 1)
        );
    }

    private List<SearchResultView> searchContentKnowledgeFallback(Long userId, Long projectId, String keyword, int limit) {
        return jdbcTemplate.query("""
                        select id, project_id, item_type, title, left(coalesce(summary, ''), 300) as snippet, updated_at
                        from ea_content_item
                        where deleted = 0
                          and owner_user_id = ?
                          and project_id = ?
                          and title like ?
                        order by updated_at desc
                        limit ?
                        """,
                (rs, rowNum) -> new SearchResultView(
                        rs.getString("item_type"),
                        rs.getLong("id"),
                        rs.getLong("project_id"),
                        rs.getString("title"),
                        rs.getString("item_type"),
                        rs.getString("snippet"),
                        rs.getTimestamp("updated_at").toLocalDateTime(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                userId,
                projectId,
                "%" + keyword + "%",
                Math.max(limit, 1)
        );
    }

    private List<SearchResultView> searchTempAttachmentFallback(Long userId,
                                                                List<InternalKnowledgeAttachment> attachments,
                                                                String keyword,
                                                                int limit) {
        List<InternalKnowledgeAttachment> tempAttachments = attachments.stream()
                .filter(item -> item != null && "TEMP_FILE".equalsIgnoreCase(item.attachmentType()))
                .toList();
        if (tempAttachments.isEmpty()) {
            return List.of();
        }
        List<SearchResultView> results = new ArrayList<>();
        for (InternalKnowledgeAttachment attachment : tempAttachments) {
            results.addAll(jdbcTemplate.query("""
                            select id, project_id, session_id, file_name, mime_type, file_size, updated_at
                            from ea_ai_temp_file
                            where deleted = 0
                              and owner_user_id = ?
                              and id = ?
                              and file_name like ?
                              and (? is null or session_id = ?)
                            limit ?
                            """,
                    (rs, rowNum) -> new SearchResultView(
                            "TEMP_FILE",
                            rs.getLong("id"),
                            rs.getObject("project_id") == null ? null : rs.getLong("project_id"),
                            rs.getString("file_name"),
                            rs.getString("mime_type"),
                            null,
                            rs.getTimestamp("updated_at").toLocalDateTime(),
                            null,
                            null,
                            null,
                            rs.getObject("file_size") == null ? null : rs.getLong("file_size"),
                            null,
                            null,
                            null,
                            null
                    ),
                    userId,
                    attachment.sourceId(),
                    "%" + keyword + "%",
                    attachment.sessionId(),
                    attachment.sessionId(),
                    Math.max(limit, 1)
            ));
        }
        return results;
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

    private boolean matchKnowledgeScope(SparseSearchRow row,
                                        Set<Long> projectScopeIds,
                                        List<InternalKnowledgeAttachment> attachments) {
        if (!attachments.isEmpty() && matchesAttachment(row.sourceType(), row.sourceId(), row.sessionId(), attachments)) {
            return true;
        }
        if (SparseKnowledgeSupport.SCOPE_KB.equalsIgnoreCase(row.scopeType())) {
            return projectScopeIds.isEmpty() || (row.projectId() != null && projectScopeIds.contains(row.projectId()));
        }
        return false;
    }

    private boolean matchesAttachment(String sourceType,
                                      Long sourceId,
                                      Long sessionId,
                                      List<InternalKnowledgeAttachment> attachments) {
        return attachments.stream().anyMatch(attachment -> {
            if (attachment == null || attachment.attachmentType() == null || attachment.sourceId() == null) {
                return false;
            }
            if (!attachment.attachmentType().equalsIgnoreCase(sourceType)) {
                return false;
            }
            if (!attachment.sourceId().equals(sourceId)) {
                return false;
            }
            return attachment.sessionId() == null || attachment.sessionId().equals(sessionId);
        });
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
