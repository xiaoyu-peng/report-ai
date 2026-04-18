package com.reportai.hub.knowledge.dto;

import lombok.Data;

/** RAG 检索单条命中：分块内容 + 溯源（文件名 / 块号）+ 相关度分数。 */
@Data
public class RagChunkHit {
    private Long chunkId;
    private Long docId;
    private Long kbId;
    private Integer chunkIndex;
    private String content;
    private String filename;
    private String fileType;
    /** MySQL FULLTEXT 返回的相关度；越大越相关。 */
    private Double score;
}
