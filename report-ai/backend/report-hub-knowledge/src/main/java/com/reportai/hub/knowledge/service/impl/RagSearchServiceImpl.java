package com.reportai.hub.knowledge.service.impl;

import com.reportai.hub.knowledge.dto.RagChunkHit;
import com.reportai.hub.knowledge.dto.RagSearchResponse;
import com.reportai.hub.knowledge.mapper.KnowledgeChunkMapper;
import com.reportai.hub.knowledge.service.RagSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagSearchServiceImpl implements RagSearchService {

    private final KnowledgeChunkMapper chunkMapper;

    /** BOOLEAN MODE 保留字符，需要从用户 query 中剔除。 */
    private static final Pattern BOOLEAN_RESERVED =
            Pattern.compile("[+\\-><()~*\"@]");

    @Override
    public RagSearchResponse search(Long kbId, String query, int topK) {
        int k = Math.max(1, Math.min(topK, 50));
        List<RagChunkHit> hits = searchRaw(kbId, query, k);
        RagSearchResponse resp = new RagSearchResponse();
        resp.setKbId(kbId);
        resp.setQuery(query);
        resp.setTopK(k);
        resp.setHits(hits);
        return resp;
    }

    @Override
    public List<RagChunkHit> searchRaw(Long kbId, String query, int topK) {
        if (query == null || query.isBlank()) return List.of();
        String expr = toBooleanModeExpression(query);
        log.debug("RAG search kb={} topK={} expr={}", kbId, topK, expr);
        return chunkMapper.searchFulltext(kbId, expr, topK);
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
        // 把 CJK 字符两侧补空格，每个汉字单独成 token
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cleaned.length(); i++) {
            char ch = cleaned.charAt(i);
            if (isCjk(ch)) {
                sb.append(' ').append(ch).append(' ');
            } else {
                sb.append(ch);
            }
        }
        String split = sb.toString();
        return Arrays.stream(split.split("\\s+"))
                .filter(s -> !s.isBlank())
                .map(s -> "+" + s)
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
