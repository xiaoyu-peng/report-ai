package com.reportai.hub.knowledge.dto;

import lombok.Data;

import java.util.List;

/** RAG 检索单条命中：分块内容 + 溯源（文件名 / 块号 / 段落）+ 相关度分数 + 高亮区间。 */
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
    /** 段落级定位（0-based），用于"引用溯源到段落"。 */
    private Integer paragraphIndex;
    /** MySQL FULLTEXT 返回的相关度；越大越相关。 */
    private Double score;
    /** 命中关键词在 content 中的字符区间 [[start, end], ...]，前端高亮用。 */
    private List<int[]> highlightSpans;
    /** 所属知识库展示名（仪表盘 + 引用卡分组用）。 */
    private String kbName;
}
