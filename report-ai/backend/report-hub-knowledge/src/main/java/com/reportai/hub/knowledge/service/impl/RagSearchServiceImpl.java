package com.reportai.hub.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.reportai.hub.knowledge.dto.RagChunkHit;
import com.reportai.hub.knowledge.dto.RagSearchQuery;
import com.reportai.hub.knowledge.dto.RagSearchResponse;
import com.reportai.hub.knowledge.entity.ReportExcludedChunk;
import com.reportai.hub.knowledge.mapper.KnowledgeChunkMapper;
import com.reportai.hub.knowledge.mapper.ReportExcludedChunkMapper;
import com.reportai.hub.knowledge.service.RagSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagSearchServiceImpl implements RagSearchService {

    private final KnowledgeChunkMapper chunkMapper;
    private final ReportExcludedChunkMapper excludedMapper;
    private final StringRedisTemplate redisTemplate;

    private static final long RAG_CACHE_TTL_SECONDS = 300;

    /** BOOLEAN MODE 保留字符，需要从用户 query 中剔除。 */
    private static final Pattern BOOLEAN_RESERVED =
            Pattern.compile("[+><()~*\"@]");

    @Override
    public RagSearchResponse search(Long kbId, String query, int topK) {
        return search(kbId, query, topK, null, null);
    }

    @Override
    public RagSearchResponse search(Long kbId, String query, int topK, String includeKeywords, String excludeKeywords) {
        int k = Math.max(1, Math.min(topK, 50));
        String combinedQuery = buildCombinedQuery(query, includeKeywords, excludeKeywords);
        List<RagChunkHit> hits = searchRaw(kbId, combinedQuery, k);
        RagSearchResponse resp = new RagSearchResponse();
        resp.setKbId(kbId);
        resp.setQuery(query);
        resp.setTopK(k);
        resp.setHits(hits);
        return resp;
    }

    private String buildCombinedQuery(String query, String includeKeywords, String excludeKeywords) {
        StringBuilder combined = new StringBuilder(query);
        if (includeKeywords != null && !includeKeywords.isBlank()) {
            combined.append(" ").append(includeKeywords);
        }
        if (excludeKeywords != null && !excludeKeywords.isBlank()) {
            for (String word : excludeKeywords.split("[,，\\s]+")) {
                if (!word.isBlank()) {
                    combined.append(" -").append(word);
                }
            }
        }
        return combined.toString();
    }

    @Override
    public List<RagChunkHit> searchRaw(Long kbId, String query, int topK,
                                       String includeKeywords, String excludeKeywords) {
        return searchRaw(kbId, buildCombinedQuery(query, includeKeywords, excludeKeywords), topK);
    }

    @Override
    public List<RagChunkHit> searchRaw(Long kbId, String query, int topK) {
        if (query == null || query.isBlank()) return List.of();

        String cacheKey = "rag:" + kbId + ":" + Integer.toHexString(query.hashCode()) + ":" + topK;
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.debug("RAG cache hit: {}", cacheKey);
                com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                return om.readValue(cached, om.getTypeFactory().constructCollectionType(List.class, RagChunkHit.class));
            }
        } catch (Exception e) {
            log.warn("RAG cache read failed, fallback to DB: {}", e.getMessage());
        }

        String expr = toBooleanModeExpression(query);
        log.debug("RAG search kb={} topK={} expr={}", kbId, topK, expr);
        List<RagChunkHit> hits = chunkMapper.searchFulltext(kbId, expr, topK);

        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            redisTemplate.opsForValue().set(cacheKey, om.writeValueAsString(hits), RAG_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("RAG cache write failed: {}", e.getMessage());
        }

        return hits;
    }

    /**
     * 把自然语言 query 转成 MySQL FULLTEXT BOOLEAN MODE 可识别的表达式。
     *
     * 历史做法是把中文逐字拆成 `+五 +一 +出 +行 +政 +策`，依赖单字索引 AND 到底。
     * 结果：6 个字全都要出现才命中 → 长 topic 召回率接近 0。
     *
     * 现在索引已用 `WITH PARSER ngram`（ngram_token_size=2），FULLTEXT 内部
     * 会把 "五一出行政策" 切成 五一/一出/出行/行政/政策 5 个 bigram。我们只需：
     *  1. 干掉 BOOLEAN 保留字符；
     *  2. 按空白再分一层词；
     *  3. 不加 "+" 前缀 —— BOOLEAN MODE 下未加前缀的词贡献 relevance 但不强制，
     *     等价于对 ngram 集合做"模糊匹配+打分"，长短 topic 都能打到；
     *  4. `-` 前缀（来自 exclude 关键词）保留原样，负权过滤。
     */
    private String toBooleanModeExpression(String raw) {
        String cleaned = BOOLEAN_RESERVED.matcher(raw).replaceAll(" ");
        return Arrays.stream(cleaned.split("\\s+"))
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining(" "));
    }

    // ==================== T5 增强检索 ====================

    @Override
    public RagSearchResponse search(RagSearchQuery q) {
        int topK = q.getTopK() == null ? 8 : Math.max(1, Math.min(q.getTopK(), 50));
        String combined = buildCombinedQuery(
                q.getQuery(),
                q.getIncludeKeywords() == null ? null : String.join(" ", q.getIncludeKeywords()),
                q.getExcludeKeywords() == null ? null : String.join(",", q.getExcludeKeywords()));

        Collection<Long> excluded = q.getReportId() == null ? Collections.emptyList()
                : excludedMapper.selectList(new QueryWrapper<ReportExcludedChunk>()
                        .eq("report_id", q.getReportId()))
                .stream().map(ReportExcludedChunk::getChunkId).collect(Collectors.toSet());

        String expr = toBooleanModeExpression(combined);
        log.debug("RAG enhanced kbs={} expr={} excluded={}", q.getKbIds(), expr, excluded.size());

        List<RagChunkHit> hits = chunkMapper.searchFulltextEnhanced(
                q.getKbIds(), expr, topK, excluded.isEmpty() ? null : excluded);

        // 计算高亮区间：用 query + includeKeywords 的字符级匹配
        List<String> highlightTerms = collectHighlightTerms(q);
        for (RagChunkHit h : hits) {
            h.setHighlightSpans(computeSpans(h.getContent(), highlightTerms));
        }

        RagSearchResponse resp = new RagSearchResponse();
        resp.setQuery(q.getQuery());
        resp.setTopK(topK);
        resp.setHits(hits);
        return resp;
    }

    @Override
    public void excludeChunk(Long reportId, Long chunkId) {
        // 唯一键 (report_id, chunk_id) 已在 DDL 设为复合主键，重复 insert 会报错，先 select
        Long exists = excludedMapper.selectCount(new QueryWrapper<ReportExcludedChunk>()
                .eq("report_id", reportId).eq("chunk_id", chunkId));
        if (exists != null && exists > 0) return;
        ReportExcludedChunk e = new ReportExcludedChunk();
        e.setReportId(reportId);
        e.setChunkId(chunkId);
        excludedMapper.insert(e);
        log.info("excluded chunk={} from report={}", chunkId, reportId);
    }

    @Override
    public void includeChunk(Long reportId, Long chunkId) {
        excludedMapper.delete(new QueryWrapper<ReportExcludedChunk>()
                .eq("report_id", reportId).eq("chunk_id", chunkId));
    }

    private List<String> collectHighlightTerms(RagSearchQuery q) {
        List<String> out = new ArrayList<>();
        if (q.getQuery() != null) {
            for (String t : q.getQuery().split("\\s+")) if (!t.isBlank() && t.length() > 1) out.add(t);
        }
        if (q.getIncludeKeywords() != null) out.addAll(q.getIncludeKeywords());
        return out;
    }

    private List<int[]> computeSpans(String text, List<String> terms) {
        if (text == null || terms == null || terms.isEmpty()) return Collections.emptyList();
        List<int[]> spans = new ArrayList<>();
        for (String k : terms) {
            if (k == null || k.isBlank()) continue;
            int idx = 0;
            while ((idx = text.indexOf(k, idx)) >= 0) {
                spans.add(new int[]{idx, idx + k.length()});
                idx += k.length();
                if (spans.size() > 30) return spans;
            }
        }
        return spans;
    }
}
