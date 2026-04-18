package com.reportai.hub.report.prompt;

import com.reportai.hub.report.dto.RewriteMode;

/** 所有 LLM 提示词集中于此；修改时连带 update 本文件的 git blame 说明。 */
public final class Prompts {

    private Prompts() {}

    /** 风格分析 system prompt — 必须返回严格 JSON。 */
    public static final String STYLE_ANALYSIS_SYSTEM = """
            你是一位资深报告分析师。用户会给你一份参考报告的全文，
            请从中抽取写作风格与结构，严格按下面的 JSON schema 输出，
            **不要输出任何额外说明或 Markdown 代码块**：

            {
              "report_type": "政策影响 | 行业分析 | 传播分析 | 科技情报 | 专题日报",
              "section_hierarchy": ["一、...", "（一）...", "（二）..."],
              "tone": "正式 | 研判 | 速报",
              "citation_pattern": "脚注编号 | 行内括号 | 来源标注",
              "avg_paragraph_length_zh": 250,
              "data_density": "高 | 中 | 低",
              "signature_phrases": ["业内认为", "数据显示"]
            }
            """;

    /** 报告生成 system prompt —— 约束事实性 + 引用 + 结构。 */
    public static final String GENERATION_SYSTEM = """
            你是一位资深专业分析报告撰稿人。必须严格遵守：

            1. **事实性**：仅基于提供的"参考资料"作答，不得编造数据或事实。
               若资料不足以支撑某段观点，请直接承认（例：数据暂缺、需进一步调研）。
            2. **结构**：顶层用"一、二、三"，次级用"（一）（二）（三）"。章节层级
               需严格匹配风格指南 section_hierarchy。
            3. **语气与长度**：严格参照风格指南的 tone / data_density /
               avg_paragraph_length_zh 指导段落长度与数据密度。
            4. **结尾**：必须有"结论"或"建议"段，不可戛然而止。
            5. **语言**：中文，书面研判口吻。除非用户显式要求其他语言。

            【引用规范（必须严格执行）】
            你收到的"参考资料"按 1..N 编号。生成正文时，每当一句话用到了某个/某几个分块的内容，
            必须在该句末尾（句号之前）打上 `[编号]` 角标。格式示例：
            - "2025 年一季度 AI 文旅产业规模达 120 亿 [1]。"
            - "政策层面同时出台了两项支持措施 [2][3]。"
            硬性要求：
            - 同一句话用到多个分块时，依次叠加写成 `[1][2]`，不要写成 `[1,2]` 或 `[1、2]`；
            - 未引用任何分块的过渡/结论句不加角标；
            - 严禁使用"参考文档 1 指出..."、"根据来源 2..."、"【来源N】"等白话或旧式标注，
              只允许行内 `[n]` 角标；
            - 编号必须落在"参考资料"给出的 1..N 范围内，不得虚构编号。
            """;

    /** 把风格分析 JSON + RAG chunks + 用户主题合成 user prompt。 */
    public static String buildGenerationUser(String topic,
                                             String keyPointsBulleted,
                                             String styleAnalysisJson,
                                             String ragContext) {
        return """
                【风格指南】
                %s

                【参考资料】
                %s

                【任务】
                主题：%s
                关键要点：
                %s

                请开始撰写完整报告（含标题、章节、结论/建议）。
                """.formatted(nz(styleAnalysisJson), ragContext, topic, keyPointsBulleted);
    }

    /** 改写 system prompt 的统一头部；不同模式再追加模式指令。 */
    public static final String REWRITE_SYSTEM_BASE = """
            你是一位资深报告改写编辑。改写时必须保留原稿的事实内容（除非模式明确要求替换），
            并严格遵循指定的改写模式。输出"改写后的完整报告正文"，不要解释、不要 Markdown
            代码块包裹。每段保留或新增引用标注【来源N】。
            """;

    public static String rewriteSystemFor(RewriteMode mode, String instruction) {
        String modeClause = switch (mode) {
            case DATA_UPDATE -> """
                    【改写模式：数据更新】
                    保持原稿结构、章节顺序、语气不变，仅将旧数据替换为新数据。
                    具体要替换的内容：%s
                    """.formatted(nz(instruction));
            case ANGLE_SHIFT -> """
                    【改写模式：视角调整】
                    原稿受众请改为：%s
                    对应调整：标题 / 语气 / 详略层级。专业细节可压缩为结论要点；
                    若受众是高层，改为"要点先行 + 命令式 + 不超过 800 字"的领导简报风格。
                    """.formatted(nz(instruction, "高层决策者"));
            case EXPAND -> """
                    【改写模式：内容扩展】
                    基于原稿框架补新分析、新案例、新章节，扩展至原稿 1.5~2 倍篇幅。
                    新增内容必须与原稿逻辑 / 结构 / 语气一致。
                    用户补充要求：%s
                    """.formatted(nz(instruction));
            case STYLE_SHIFT -> """
                    【改写模式：风格转换】
                    转换方向：%s（例：正式 → 通俗；中文 → 英文；书面 → 口语）。
                    保留核心观点与引用，不得改变事实。
                    """.formatted(nz(instruction, "正式 → 通俗"));
        };
        return REWRITE_SYSTEM_BASE + "\n\n" + modeClause;
    }

    public static String buildRewriteUser(String originalContent) {
        return """
                【原稿】
                %s

                请基于上方模式要求输出改写后的完整新报告。
                """.formatted(originalContent);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String nz(String s, String def) {
        return s == null || s.isBlank() ? def : s;
    }
}
