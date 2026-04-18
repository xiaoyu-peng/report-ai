package com.reportai.hub.report.dto;

/** PRD §S3：四种改写模式，评分 25% 专门考这个。 */
public enum RewriteMode {
    /** 保持结构，替换数据（示例：把 2024 年数据换成 2025 年）。 */
    DATA_UPDATE,
    /** 同内容改观点 / 立场 / 受众（行业报告 → 领导简报）。 */
    ANGLE_SHIFT,
    /** 基于原稿框架，AI 补新分析 / 案例 / 章节。 */
    EXPAND,
    /** 风格转换：正式 ↔ 通俗，中文 ↔ 英文。 */
    STYLE_SHIFT
}
