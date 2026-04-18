package com.reportai.hub.knowledge.service;

import com.reportai.hub.knowledge.dto.RagChunkHit;
import com.reportai.hub.knowledge.dto.RagSearchResponse;

import java.util.List;

public interface RagSearchService {

    /**
     * 在知识库 kbId 范围内做 FULLTEXT 检索，返回按相关度降序的片段。
     *
     * @param kbId 知识库 ID
     * @param query 自然语言查询（内部会转成 BOOLEAN MODE 表达式）
     * @param topK 返回条数上限（>=1 <=50）
     */
    RagSearchResponse search(Long kbId, String query, int topK);

    /** 纯粹的检索原语，供报告生成阶段复用（不包装响应结构）。 */
    List<RagChunkHit> searchRaw(Long kbId, String query, int topK);
}
