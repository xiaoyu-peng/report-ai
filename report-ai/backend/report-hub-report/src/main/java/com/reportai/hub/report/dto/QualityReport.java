package com.reportai.hub.report.dto;

import lombok.Data;

import java.util.List;

/**
 * 报告质量保障结果（赛题 3.4：事实性错误检测 + 引用准确性检查 + 知识库覆盖度分析）。
 * 由 LLM 一次综合打分返回。
 */
@Data
public class QualityReport {

    /** 综合得分 0-100，由 LLM 给出。 */
    private Integer overallScore;
    /** 一句话总评，UI 顶部大字展示。 */
    private String summary;

    // —— 维度一：覆盖度（用户 keyPoints 是否都被正文覆盖） ——
    /** 覆盖率 0-1，1 = 全部覆盖。 */
    private Double coverageScore;
    /** 未被覆盖的要点原文。 */
    private List<String> missingKeyPoints;

    // —— 维度二：引用准确性（正文的 [n] 是否真能从对应 chunk 推出） ——
    private Double citationAccuracyScore;
    private List<CitationIssue> citationIssues;

    // —— 维度三：事实性（报告中有无无源杜撰 / 硬造数据） ——
    private Double factualityScore;
    private List<FactualityIssue> factualityIssues;

    @Data
    public static class CitationIssue {
        /** 可疑的 [n] 编号。 */
        private Integer citedIndex;
        /** 涉嫌出错的原句。 */
        private String sentence;
        /** LLM 给出的判定理由，例如"分块 3 未出现此数据"。 */
        private String reason;
    }

    @Data
    public static class FactualityIssue {
        private String sentence;
        /** 例如 "数据 120 亿无任何分块支撑"。 */
        private String reason;
        /** 建议的处理方式：mark-待核实 / fix-数据错误 / soften-语气弱化。 */
        private String suggestion;
    }
}
