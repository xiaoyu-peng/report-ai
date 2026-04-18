package com.reportai.hub.knowledge.service.impl;

import com.reportai.hub.knowledge.dto.RagChunkHit;
import com.reportai.hub.knowledge.dto.RagSearchResponse;
import com.reportai.hub.knowledge.mapper.KnowledgeChunkMapper;
import com.reportai.hub.knowledge.service.RagSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagSearchServiceImpl implements RagSearchService {

    private final KnowledgeChunkMapper chunkMapper;
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
     * 做法：
     *  1. 去掉保留字符；
     *  2. 按空白分词；
     *  3. 每个词加 "+" 前缀表示 "必须包含"，并在两端加 "*" 允许前缀匹配；
     *  4. 中文逐字空格分隔（MySQL FULLTEXT 对 CJK 的默认分词极差，
     *     已通过 innodb-ft-min-token-size=1 启用单字索引）。
     */
    private String toBooleanModeExpression(String raw) {
        String cleaned = BOOLEAN_RESERVED.matcher(raw).replaceAll(" ");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cleaned.length(); i++) {
            char ch = cleaned.charAt(i);
            if (ch == '-' && i + 1 < cleaned.length() && !Character.isWhitespace(cleaned.charAt(i + 1))) {
                sb.append(' ').append('-');
            } else if (isCjk(ch)) {
                sb.append(' ').append(ch).append(' ');
            } else {
                sb.append(ch);
            }
        }
        String split = sb.toString();
        return Arrays.stream(split.split("\\s+"))
                .filter(s -> !s.isBlank())
                .map(s -> {
                    if (s.startsWith("-")) return s;
                    return "+" + s;
                })
                .collect(Collectors.joining(" "));
    }

    private boolean isCjk(char ch) {
        Character.UnicodeBlock b = Character.UnicodeBlock.of(ch);
        return b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || b == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS;
    }
}
