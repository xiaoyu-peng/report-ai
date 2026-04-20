package com.reportai.hub.report.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class ReportCreateDTO {
    @NotBlank
    private String title;
    @NotBlank
    private String topic;
    /** 可空 —— 未选知识库时 RAG 跳过，依赖模板风格 + MCP / Tavily 自动拉取兜底。 */
    private Long kbId;
    private Long templateId;
    private List<String> keyPoints;
    /** 赛题 2.3：手动补充的检索关键词。支持逗号/空格分隔，空 = 不启用。 */
    private String includeKeywords;
    /** 赛题 2.3：需要排除的关键词。同上。 */
    private String excludeKeywords;
    /** 生成深度：brief / standard / deep；不传等价 standard。 */
    private String generationDepth;
}
