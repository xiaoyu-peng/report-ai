package com.reportai.hub.report.dto;

import lombok.Data;

import java.util.List;

/** LLM 输出的模板风格分析结构化结果（供后续仿写提示词使用）。 */
@Data
public class StyleAnalysis {
    /** 政策影响 / 行业分析 / 传播分析 / 科技情报 / 专题日报 */
    private String reportType;
    private List<String> sectionHierarchy;
    /** 正式 / 研判 / 速报 */
    private String tone;
    /** 脚注编号 | 行内括号 | 来源标注 */
    private String citationPattern;
    private Integer avgParagraphLengthZh;
    /** 高 / 中 / 低 */
    private String dataDensity;
    private List<String> signaturePhrases;
}
