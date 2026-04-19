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
    /** PDF 源的起止页码（1-based）；非 PDF 为 null。 */
    private Integer pageStart;
    private Integer pageEnd;
    /** MySQL FULLTEXT 返回的相关度；越大越相关。 */
    private Double score;
}
