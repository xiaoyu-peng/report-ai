package com.reportai.hub.knowledge.dto;

import lombok.Data;

import java.util.List;

/** 对外统一的 RAG 检索响应结构。 */
@Data
public class RagSearchResponse {
    private String query;
    private Long kbId;
    private int topK;
    private List<RagChunkHit> hits;
}
