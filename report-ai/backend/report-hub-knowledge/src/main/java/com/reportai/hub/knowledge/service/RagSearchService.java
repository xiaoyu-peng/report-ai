package com.reportai.hub.knowledge.service;

import com.reportai.hub.knowledge.dto.RagChunkHit;
import com.reportai.hub.knowledge.dto.RagSearchResponse;

import java.util.List;

public interface RagSearchService {

    RagSearchResponse search(Long kbId, String query, int topK);

    RagSearchResponse search(Long kbId, String query, int topK, String includeKeywords, String excludeKeywords);

    List<RagChunkHit> searchRaw(Long kbId, String query, int topK);

    /**
     * 赛题 2.3：支持用户手动补充/排除关键词的检索。
     * includeKeywords 里的词会作为 +term 加进 BOOLEAN query；
     * excludeKeywords 里的词作为 -term 剔除不相关分块。
     */
    List<RagChunkHit> searchRaw(Long kbId, String query, int topK,
                                String includeKeywords, String excludeKeywords);
}
