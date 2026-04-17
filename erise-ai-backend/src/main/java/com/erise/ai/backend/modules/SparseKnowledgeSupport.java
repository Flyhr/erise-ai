package com.erise.ai.backend.modules;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erise.ai.backend.common.entity.AuditableEntity;
import com.huaban.analysis.jieba.JiebaSegmenter;
import com.huaban.analysis.jieba.SegToken;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class SparseKnowledgeSupport {

    static final String FIELD_TITLE = "TITLE";
    static final String FIELD_SECTION = "SECTION";
    static final String FIELD_BODY = "BODY";
    static final String SCOPE_KB = "KB";
    static final String SCOPE_TEMP = "TEMP";

    private static final int TITLE_WEIGHT = 6;
    private static final int SECTION_WEIGHT = 3;
    private static final int BODY_WEIGHT = 1;
    private static final int BODY_SPARSE_MAX_CHUNKS = 800;
    private static final int BODY_SPARSE_MAX_TERMS = 64;
    private static final int SPARSE_INSERT_BATCH_SIZE = 1000;

    private final RagSparseTermMapper ragSparseTermMapper;
    private final JdbcTemplate jdbcTemplate;
    private final KnowledgeTokenizerSupport knowledgeTokenizerSupport;

    void rebuildSourceIndex(RagSourceEntity source, List<RagChunkEntity> chunks, Long operatorUserId) {
        if (source == null) {
            return;
        }
        deleteSourceIndex(source.getId());
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        Long resolvedOperatorUserId = operatorUserId == null ? source.getOwnerUserId() : operatorUserId;
        List<SparseTermRow> pendingRows = new ArrayList<>(SPARSE_INSERT_BATCH_SIZE);
        int bodyChunksIndexed = 0;
        for (RagChunkEntity chunk : chunks) {
            if (chunk == null || chunk.getId() == null) {
                continue;
            }
            appendTermRows(pendingRows, source, chunk, FIELD_TITLE, source.getSourceTitle(), resolvedOperatorUserId, 24);
            appendTermRows(pendingRows, source, chunk, FIELD_SECTION, chunk.getSectionPath(), resolvedOperatorUserId, 24);
            if (bodyChunksIndexed < BODY_SPARSE_MAX_CHUNKS) {
                appendTermRows(pendingRows, source, chunk, FIELD_BODY, chunk.getChunkText(), resolvedOperatorUserId, BODY_SPARSE_MAX_TERMS);
                bodyChunksIndexed += 1;
            }
            flushTermRowsIfNeeded(pendingRows);
        }
        flushTermRows(pendingRows);
    }

    void deleteSourceIndex(Long ragSourceId) {
        if (ragSourceId == null) {
            return;
        }
        jdbcTemplate.update("delete from ea_rag_sparse_term where rag_source_id = ?", ragSourceId);
    }

    List<String> queryTerms(String keyword) {
        return knowledgeTokenizerSupport.tokenizeQuery(keyword);
    }

    List<SparseSearchRow> searchRows(Long ownerUserId,
                                     boolean restrictOwner,
                                     String scopeType,
                                     Set<String> sourceTypes,
                                     String keyword,
                                     int limit) {
        List<String> terms = knowledgeTokenizerSupport.tokenizeQuery(keyword);
        if (terms.isEmpty()) {
            return List.of();
        }

        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                select
                  t.scope_type,
                  t.source_type,
                  t.source_id,
                  c.project_id,
                  c.session_id,
                  coalesce(f.file_name, d.title, tf.file_name, s.source_title) as display_title,
                  left(c.chunk_text, 320) as snippet,
                  c.page_no,
                  c.section_path,
                  coalesce(f.updated_at, d.updated_at, tf.updated_at, c.updated_at) as updated_at,
                  f.file_ext,
                  f.mime_type,
                  f.file_size,
                  f.upload_status,
                  f.parse_status,
                  f.index_status,
                  d.doc_status,
                  sum(case t.field_code
                        when 'TITLE' then t.term_freq * %d
                        when 'SECTION' then t.term_freq * %d
                        else t.term_freq * %d
                      end) as sparse_score,
                  count(distinct t.term) as matched_terms
                from ea_rag_sparse_term t
                join ea_rag_chunk c on c.id = t.rag_chunk_id and c.deleted = 0
                join ea_rag_source s on s.id = t.rag_source_id and s.deleted = 0
                left join ea_file f on t.source_type = 'FILE' and f.id = t.source_id and f.deleted = 0
                left join ea_document d on t.source_type = 'DOCUMENT' and d.id = t.source_id and d.deleted = 0
                left join ea_ai_temp_file tf on t.source_type = 'TEMP_FILE' and tf.id = t.source_id and tf.deleted = 0
                where t.deleted = 0
                  and s.status = 'READY'
                """.formatted(TITLE_WEIGHT, SECTION_WEIGHT, BODY_WEIGHT));
        if (scopeType != null && !scopeType.isBlank()) {
            sql.append(" and t.scope_type = ? ");
            params.add(scopeType.trim().toUpperCase(Locale.ROOT));
        }
        if (restrictOwner && ownerUserId != null) {
            sql.append(" and t.owner_user_id = ? ");
            params.add(ownerUserId);
        }
        if (sourceTypes != null && !sourceTypes.isEmpty()) {
            sql.append(" and t.source_type in (");
            appendPlaceholders(sql, sourceTypes.size());
            sql.append(") ");
            sourceTypes.stream()
                    .map(value -> value == null ? null : value.trim().toUpperCase(Locale.ROOT))
                    .forEach(params::add);
        }
        sql.append(" and t.term in (");
        appendPlaceholders(sql, terms.size());
        sql.append(") ");
        params.addAll(terms);
        sql.append("""
                group by
                  t.scope_type,
                  t.source_type,
                  t.source_id,
                  c.id,
                  c.project_id,
                  c.session_id,
                  s.source_title,
                  c.chunk_text,
                  c.page_no,
                  c.section_path,
                  c.updated_at,
                  f.file_name,
                  f.updated_at,
                  f.file_ext,
                  f.mime_type,
                  f.file_size,
                  f.upload_status,
                  f.parse_status,
                  f.index_status,
                  d.title,
                  d.updated_at,
                  d.doc_status,
                  tf.file_name,
                  tf.updated_at
                order by sparse_score desc, matched_terms desc, updated_at desc
                limit ?
                """);
        params.add(Math.max(limit, 1));

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new SparseSearchRow(
                rs.getString("scope_type"),
                rs.getString("source_type"),
                rs.getLong("source_id"),
                rs.getObject("project_id") == null ? null : rs.getLong("project_id"),
                rs.getObject("session_id") == null ? null : rs.getLong("session_id"),
                rs.getString("display_title"),
                rs.getString("snippet"),
                rs.getObject("page_no") == null ? null : rs.getInt("page_no"),
                rs.getString("section_path"),
                rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toLocalDateTime(),
                rs.getString("file_ext"),
                rs.getString("mime_type"),
                rs.getObject("file_size") == null ? null : rs.getLong("file_size"),
                rs.getString("upload_status"),
                rs.getString("parse_status"),
                rs.getString("index_status"),
                rs.getString("doc_status"),
                rs.getDouble("sparse_score"),
                rs.getLong("matched_terms")
        ), params.toArray());
    }

    private void appendTermRows(List<SparseTermRow> pendingRows,
                                RagSourceEntity source,
                                RagChunkEntity chunk,
                                String fieldCode,
                                String text,
                                Long operatorUserId,
                                int maxUniqueTerms) {
        Map<String, Integer> termFrequencies = knowledgeTokenizerSupport.countTerms(text, maxUniqueTerms);
        if (termFrequencies.isEmpty()) {
            return;
        }
        int docLen = termFrequencies.values().stream().mapToInt(Integer::intValue).sum();
        for (Map.Entry<String, Integer> entry : termFrequencies.entrySet()) {
            pendingRows.add(new SparseTermRow(
                    source.getId(),
                    chunk.getId(),
                    source.getOwnerUserId(),
                    source.getProjectId(),
                    source.getSessionId() == null ? 0L : source.getSessionId(),
                    source.getScopeType(),
                    source.getSourceType(),
                    source.getSourceId(),
                    entry.getKey(),
                    fieldCode,
                    entry.getValue(),
                    docLen,
                    operatorUserId,
                    operatorUserId
            ));
        }
    }

    private void flushTermRowsIfNeeded(List<SparseTermRow> rows) {
        if (rows.size() >= SPARSE_INSERT_BATCH_SIZE) {
            flushTermRows(rows);
        }
    }

    private void flushTermRows(List<SparseTermRow> rows) {
        if (rows.isEmpty()) {
            return;
        }
        List<SparseTermRow> batch = new ArrayList<>(rows);
        rows.clear();
        jdbcTemplate.batchUpdate(
                """
                insert into ea_rag_sparse_term (
                  rag_source_id, rag_chunk_id, owner_user_id, project_id, session_id,
                  scope_type, source_type, source_id, term, field_code, term_freq,
                  doc_len, created_by, updated_by
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        SparseTermRow row = batch.get(i);
                        ps.setLong(1, row.ragSourceId());
                        ps.setLong(2, row.ragChunkId());
                        ps.setLong(3, row.ownerUserId());
                        if (row.projectId() == null) {
                            ps.setObject(4, null);
                        } else {
                            ps.setLong(4, row.projectId());
                        }
                        ps.setLong(5, row.sessionId());
                        ps.setString(6, row.scopeType());
                        ps.setString(7, row.sourceType());
                        ps.setLong(8, row.sourceId());
                        ps.setString(9, row.term());
                        ps.setString(10, row.fieldCode());
                        ps.setInt(11, row.termFreq());
                        ps.setInt(12, row.docLen());
                        if (row.createdBy() == null) {
                            ps.setObject(13, null);
                        } else {
                            ps.setLong(13, row.createdBy());
                        }
                        if (row.updatedBy() == null) {
                            ps.setObject(14, null);
                        } else {
                            ps.setLong(14, row.updatedBy());
                        }
                    }

                    @Override
                    public int getBatchSize() {
                        return batch.size();
                    }
                }
        );
    }

    private void appendPlaceholders(StringBuilder builder, int size) {
        for (int index = 0; index < size; index++) {
            if (index > 0) {
                builder.append(", ");
            }
            builder.append('?');
        }
    }
}

@Service
class KnowledgeTokenizerSupport {

    private static final Pattern ASCII_TERM_PATTERN = Pattern.compile("[a-z0-9][a-z0-9_\\-.]{1,31}");
    private static final int MAX_TERM_LENGTH = 96;
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "by", "for", "from", "in", "into", "is",
            "of", "on", "or", "that", "the", "this", "to", "with",
            "\u7684", "\u4e86", "\u548c", "\u662f", "\u5728", "\u4e2d", "\u53ca", "\u6216",
            "\u5c31", "\u90fd", "\u800c", "\u4e0e", "\u4ee5\u53ca", "\u4e00\u4e2a"
    );

    private final JiebaSegmenter jiebaSegmenter = new JiebaSegmenter();

    List<String> tokenizeQuery(String text) {
        return List.copyOf(tokenizeInternal(text, 8));
    }

    Map<String, Integer> countTerms(String text, int maxUniqueTerms) {
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return Map.of();
        }
        LinkedHashMap<String, Integer> frequencies = new LinkedHashMap<>();
        synchronized (jiebaSegmenter) {
            for (SegToken token : jiebaSegmenter.process(normalized, JiebaSegmenter.SegMode.SEARCH)) {
                addFrequency(frequencies, token == null ? null : token.word, maxUniqueTerms);
            }
        }
        if (frequencies.isEmpty()) {
            Matcher matcher = ASCII_TERM_PATTERN.matcher(normalized);
            while (matcher.find()) {
                addFrequency(frequencies, matcher.group(), maxUniqueTerms);
            }
        }
        if (frequencies.isEmpty()) {
            String fallback = sanitizeToken(normalized.replace(" ", ""));
            if (fallback != null) {
                frequencies.put(fallback, 1);
            }
        }
        return frequencies;
    }

    private List<String> tokenizeInternal(String text, int maxUniqueTerms) {
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        synchronized (jiebaSegmenter) {
            for (SegToken token : jiebaSegmenter.process(normalized, JiebaSegmenter.SegMode.SEARCH)) {
                addToken(tokens, token == null ? null : token.word, maxUniqueTerms);
                if (tokens.size() >= maxUniqueTerms) {
                    return List.copyOf(tokens);
                }
            }
        }
        Matcher matcher = ASCII_TERM_PATTERN.matcher(normalized);
        while (matcher.find() && tokens.size() < maxUniqueTerms) {
            addToken(tokens, matcher.group(), maxUniqueTerms);
        }
        if (tokens.isEmpty()) {
            addToken(tokens, normalized.replace(" ", ""), maxUniqueTerms);
        }
        return List.copyOf(tokens);
    }

    private void addToken(Set<String> tokens, String raw, int maxUniqueTerms) {
        String normalized = sanitizeToken(raw);
        if (normalized == null || tokens.size() >= maxUniqueTerms) {
            return;
        }
        tokens.add(normalized);
    }

    private void addFrequency(Map<String, Integer> frequencies, String raw, int maxUniqueTerms) {
        String normalized = sanitizeToken(raw);
        if (normalized == null) {
            return;
        }
        if (!frequencies.containsKey(normalized) && frequencies.size() >= maxUniqueTerms) {
            return;
        }
        frequencies.merge(normalized, 1, Integer::sum);
    }

    private String sanitizeToken(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = normalize(raw).replace(" ", "");
        if (normalized.isBlank() || STOP_WORDS.contains(normalized)) {
            return null;
        }
        if (normalized.length() > MAX_TERM_LENGTH) {
            return null;
        }
        if (containsHan(normalized)) {
            if (normalized.length() < 2) {
                return null;
            }
        } else if (normalized.length() < 2) {
            return null;
        }
        return normalized;
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace('\u3000', ' ')
                .replace('\u00A0', ' ')
                .replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]+", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private boolean containsHan(String text) {
        for (char current : text.toCharArray()) {
            if (Character.UnicodeScript.of(current) == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }
}

interface RagSparseTermMapper extends BaseMapper<RagSparseTermEntity> {
}

record SparseTermRow(
        Long ragSourceId,
        Long ragChunkId,
        Long ownerUserId,
        Long projectId,
        Long sessionId,
        String scopeType,
        String sourceType,
        Long sourceId,
        String term,
        String fieldCode,
        Integer termFreq,
        Integer docLen,
        Long createdBy,
        Long updatedBy
) {
}

@Data
@TableName("ea_rag_sparse_term")
class RagSparseTermEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ragSourceId;
    private Long ragChunkId;
    private Long ownerUserId;
    private Long projectId;
    private Long sessionId;
    private String scopeType;
    private String sourceType;
    private Long sourceId;
    private String term;
    private String fieldCode;
    private Integer termFreq;
    private Integer docLen;
}

record SparseSearchRow(
        String scopeType,
        String sourceType,
        Long sourceId,
        Long projectId,
        Long sessionId,
        String title,
        String snippet,
        Integer pageNo,
        String sectionPath,
        LocalDateTime updatedAt,
        String fileExt,
        String mimeType,
        Long fileSize,
        String uploadStatus,
        String parseStatus,
        String indexStatus,
        String docStatus,
        Double sparseScore,
        Long matchedTerms
) {
}
