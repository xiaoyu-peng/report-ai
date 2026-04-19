package com.reportai.hub.knowledge.dto;

import lombok.Data;

import java.util.List;

/**
 * RAG 检索请求体（赛题 2.3 + 5 个杀手锏交互之"引用溯源"所需）。
 * 兼具：多 KB 范围、include/exclude 关键词、绑定到某报告（自动过滤已排除引用块）。
 */
@Data
public class RagSearchQuery {
    /** 绑定报告：用于读取 report_excluded_chunk，不传则不过滤已排除项。 */
    private Long reportId;
    /** 检索范围；空 = 所有 KB。 */
    private List<Long> kbIds;
    /** 用户检索词。 */
    private String query;
    /** 默认 8。 */
    private Integer topK;
    /** 必须包含（追加进 BOOLEAN MODE 表达式）。 */
    private List<String> includeKeywords;
    /** 不要包含（追加 -term 进 BOOLEAN MODE 表达式）。 */
    private List<String> excludeKeywords;
}
