package com.reportai.hub.knowledge.service;

import com.reportai.hub.knowledge.dto.RagChunkHit;
import com.reportai.hub.knowledge.dto.RagSearchQuery;
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

    /**
     * T5 增强检索：多 KB + 排除已 excluded chunk + 段落定位 + 高亮区间。
     * 用于工作台「参考资料」面板和报告生成的引用埋点。
     */
    RagSearchResponse search(RagSearchQuery query);

    /** 标记某 chunk 在某报告里被排除，下次检索 NOT IN 它。 */
    void excludeChunk(Long reportId, Long chunkId);

    /** 取消排除（点错了想恢复）。 */
    void includeChunk(Long reportId, Long chunkId);
}
