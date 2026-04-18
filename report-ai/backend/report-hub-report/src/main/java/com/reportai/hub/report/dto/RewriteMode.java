package com.reportai.hub.report.dto;

/** 赛题模块四：改写模式。前 4 种为赛题明列；CONTINUATION 是赛题"AI 续写能力"的实现。 */
public enum RewriteMode {
    /** 保持结构，替换数据（示例：把 2024 年数据换成 2025 年）。 */
    DATA_UPDATE,
    /** 同内容改观点 / 立场 / 受众（行业报告 → 领导简报）。 */
    ANGLE_SHIFT,
    /** 基于原稿框架，AI 补新分析 / 案例 / 章节。 */
    EXPAND,
    /** 风格转换：正式 ↔ 通俗，中文 ↔ 英文。 */
    STYLE_SHIFT,
    /**
     * 续写新章节：原稿保持不变，在末尾追加与原稿逻辑 / 数据 / 风格一致的新章节。
     * LLM 仅输出新章节；Service 负责拼接入库。
     */
    CONTINUATION
}
