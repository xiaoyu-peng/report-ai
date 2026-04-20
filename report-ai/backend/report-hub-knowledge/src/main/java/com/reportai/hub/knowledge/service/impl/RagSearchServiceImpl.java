package com.reportai.hub.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.reportai.hub.knowledge.dto.EsChunkDocument;
import com.reportai.hub.knowledge.dto.RagChunkHit;
import com.reportai.hub.knowledge.dto.RagSearchQuery;
import com.reportai.hub.knowledge.dto.RagSearchResponse;
import com.reportai.hub.knowledge.entity.ReportExcludedChunk;
import com.reportai.hub.knowledge.mapper.KnowledgeChunkMapper;
import com.reportai.hub.knowledge.mapper.ReportExcludedChunkMapper;
import com.reportai.hub.knowledge.service.EsChunkService;
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
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagSearchServiceImpl implements RagSearchService {

    private final KnowledgeChunkMapper chunkMapper;
    private final ReportExcludedChunkMapper excludedMapper;
    private final StringRedisTemplate redisTemplate;
    private final EsChunkService esChunkService;

    private static final long RAG_CACHE_TTL_SECONDS = 300;

    @Override
    public RagSearchResponse search(Long kbId, String query, int topK) {
        return search(kbId, query, topK, null, null);
    }

    @Override
    public RagSearchResponse search(Long kbId, String query, int topK, String includeKeywords, String excludeKeywords) {
        int k = Math.max(1, Math.min(topK, 50));
        List<String> includeList = parseKeywords(includeKeywords);
        List<String> excludeList = parseKeywords(excludeKeywords);
        List<RagChunkHit> hits = searchFromEs(kbId, query, includeList, excludeList, k);
        RagSearchResponse resp = new RagSearchResponse();
        resp.setKbId(kbId);
        resp.setQuery(query);
        resp.setTopK(k);
        resp.setHits(hits);
        return resp;
    }

    private List<String> parseKeywords(String keywords) {
        if (keywords == null || keywords.isBlank()) return null;
        return Arrays.stream(keywords.split("[,，\\s]+"))
            .filter(s -> !s.isBlank())
            .collect(Collectors.toList());
    }

    @Override
    public List<RagChunkHit> searchRaw(Long kbId, String query, int topK,
                                       String includeKeywords, String excludeKeywords) {
        List<String> includeList = parseKeywords(includeKeywords);
        List<String> excludeList = parseKeywords(excludeKeywords);
        return searchRaw(kbId, query, includeList, excludeList, topK);
    }

    @Override
    public List<RagChunkHit> searchRaw(Long kbId, String query, int topK) {
        return searchRaw(kbId, query, null, null, topK);
    }

    private List<RagChunkHit> searchRaw(Long kbId, String query, List<String> includeKeywords, List<String> excludeKeywords, int topK) {
        if (query == null || query.isBlank()) return List.of();

        String cacheKey = buildCacheKey(kbId, query, includeKeywords, excludeKeywords, topK);
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.debug("RAG cache hit: {}", cacheKey);
                com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                return om.readValue(cached, om.getTypeFactory().constructCollectionType(List.class, RagChunkHit.class));
            }
        } catch (Exception e) {
            log.warn("RAG cache read failed, fallback to ES: {}", e.getMessage());
        }

        List<RagChunkHit> hits = searchFromEs(kbId, query, includeKeywords, excludeKeywords, topK);

        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            redisTemplate.opsForValue().set(cacheKey, om.writeValueAsString(hits), RAG_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("RAG cache write failed: {}", e.getMessage());
        }

        return hits;
    }

    private String buildCacheKey(Long kbId, String query, List<String> includeKeywords, List<String> excludeKeywords, int topK) {
        String include = includeKeywords == null ? "" : String.join(",", includeKeywords);
        String exclude = excludeKeywords == null ? "" : String.join(",", excludeKeywords);
        String raw = kbId + ":" + query + ":" + include + ":" + exclude + ":" + topK;
        return "rag:es:" + Integer.toHexString(raw.hashCode());
    }

    private List<RagChunkHit> searchFromEs(Long kbId, String query, List<String> includeKeywords, List<String> excludeKeywords, int topK) {
        log.debug("ES search kb={} topK={} query={}", kbId, topK, query);
        List<EsChunkDocument> esDocs = esChunkService.searchWithIncludeExclude(kbId, query, includeKeywords, excludeKeywords, topK);
        double maxScore = maxEsScore(esDocs);
        List<RagChunkHit> hits = new ArrayList<>();
        for (EsChunkDocument doc : esDocs) {
            hits.add(toHit(doc, maxScore));
        }
        return hits;
    }

    /**
     * ES `_score` 是绝对值（通常 5–30），直接给前端相关度条会全部撑满。
     * 这里按本批结果内的 max 做线性归一化，top hit = 1.0，其他按比例缩；
     * 如果没有 score（空结果 / ES 没返回 score），兜底 1.0 保持旧行为。
     */
    private RagChunkHit toHit(EsChunkDocument doc, double maxScore) {
        RagChunkHit hit = new RagChunkHit();
        hit.setChunkId(doc.getId());
        hit.setKbId(doc.getKbId());
        hit.setDocId(doc.getDocId());
        hit.setFilename(doc.getDocName());
        hit.setContent(doc.getContent());
        hit.setChunkIndex(doc.getChunkIndex());
        hit.setPageStart(doc.getPageStart());
        hit.setPageEnd(doc.getPageEnd());
        Double raw = doc.getScore();
        double normalized = (raw == null || maxScore <= 0) ? 1.0 : Math.max(0.0, Math.min(raw / maxScore, 1.0));
        hit.setScore(normalized);
        return hit;
    }

    private double maxEsScore(List<EsChunkDocument> docs) {
        double max = 0.0;
        for (EsChunkDocument d : docs) {
            if (d.getScore() != null && d.getScore() > max) max = d.getScore();
        }
        return max;
    }

    @Override
    public RagSearchResponse search(RagSearchQuery q) {
        int topK = q.getTopK() == null ? 8 : Math.max(1, Math.min(q.getTopK(), 50));

        Collection<Long> excluded = q.getReportId() == null ? Collections.emptyList()
                : excludedMapper.selectList(new QueryWrapper<ReportExcludedChunk>()
                        .eq("report_id", q.getReportId()))
                .stream().map(ReportExcludedChunk::getChunkId).collect(Collectors.toSet());

        log.debug("RAG enhanced kbs={} excluded={}", q.getKbIds(), excluded.size());

        // 多 KB 合并前先各自归一化，避免不同 KB 的 _score 绝对量级差异影响排序条。
        List<RagChunkHit> allHits = new ArrayList<>();
        for (Long kbId : q.getKbIds()) {
            List<EsChunkDocument> esDocs = esChunkService.searchWithIncludeExclude(
                kbId, q.getQuery(), q.getIncludeKeywords(), q.getExcludeKeywords(), topK);
            double maxScore = maxEsScore(esDocs);
            for (EsChunkDocument doc : esDocs) {
                if (excluded.contains(doc.getId())) continue;
                allHits.add(toHit(doc, maxScore));
            }
        }

        List<String> highlightTerms = collectHighlightTerms(q);
        for (RagChunkHit h : allHits) {
            h.setHighlightSpans(computeSpans(h.getContent(), highlightTerms));
        }

        RagSearchResponse resp = new RagSearchResponse();
        resp.setQuery(q.getQuery());
        resp.setTopK(topK);
        resp.setHits(allHits.stream().limit(topK).collect(Collectors.toList()));
        return resp;
    }

    @Override
    public void excludeChunk(Long reportId, Long chunkId) {
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
