package com.reportai.hub.report.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reportai.hub.common.exception.BusinessException;
import com.reportai.hub.report.dto.RewriteMode;
import com.reportai.hub.report.entity.Report;
import com.reportai.hub.report.entity.ReportVersion;
import com.reportai.hub.common.llm.LlmClient;
import com.reportai.hub.report.mapper.ReportMapper;
import com.reportai.hub.report.mapper.ReportVersionMapper;
import com.reportai.hub.report.prompt.Prompts;
import com.reportai.hub.report.service.RewriteService;
import com.reportai.hub.knowledge.mcp.SassMcpService;
import com.reportai.hub.knowledge.mcp.SearchMcpService;
import com.reportai.hub.knowledge.mcp.TavilyClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class RewriteServiceImpl implements RewriteService {

    private final ReportMapper reportMapper;
    private final ReportVersionMapper versionMapper;
    private final LlmClient llmClient;
    private final SassMcpService sassMcpService;
    private final SearchMcpService searchMcpService;
    private final TavilyClient tavilyClient;

    @Override
    public ReportVersion streamRewrite(Long reportId,
                                       RewriteMode mode,
                                       String instruction,
                                       Long operatorId,
                                       Consumer<String> onToken,
                                       Runnable onDone) {
        Report report = reportMapper.selectById(reportId);
        if (report == null) throw new BusinessException("报告不存在: " + reportId);

        ReportVersion latest = versionMapper.selectOne(
                new LambdaQueryWrapper<ReportVersion>()
                        .eq(ReportVersion::getReportId, reportId)
                        .orderByDesc(ReportVersion::getVersionNum)
                        .last("LIMIT 1"));
        String original = latest != null && latest.getContent() != null
                ? latest.getContent()
                : (report.getContent() == null ? "" : report.getContent());
        if (original.isBlank()) {
            throw new BusinessException("原稿为空，无法改写。请先生成首版。");
        }
        int baseVer = latest == null ? 0 : latest.getVersionNum();

        String mcpContext = buildRewriteMcpContext(report, mode, instruction);
        String system = Prompts.rewriteSystemFor(mode, instruction);
        if (mcpContext != null && !mcpContext.isBlank()) {
            system = system + "\n\n--- 以下是来自外部数据源的实时数据，请在改写时参考使用 ---\n" + mcpContext;
        }
        String user = Prompts.buildRewriteUser(original);

        StringBuilder full = new StringBuilder();
        llmClient.stream(system, user, token -> {
            full.append(token);
            onToken.accept(token);
        }, () -> {});

        String llmOut = full.toString();
        String body = (mode == RewriteMode.CONTINUATION)
                ? original + (original.endsWith("\n") ? "\n" : "\n\n") + llmOut.stripLeading()
                : llmOut;
        ReportVersion v = new ReportVersion();
        v.setReportId(reportId);
        v.setVersionNum(baseVer + 1);
        v.setTitle(report.getTitle());
        v.setContent(body);
        v.setSourceMode("rewrite_" + mode.name().toLowerCase(Locale.ROOT));
        v.setWordCount(body.length());
        v.setCreatedBy(operatorId);
        v.setChangeSummary("改写模式：%s".formatted(mode.name()));
        versionMapper.insert(v);

        report.setContent(body);
        report.setWordCount(body.length());
        report.setStatus("ready");
        reportMapper.updateById(report);

        log.info("report {} rewritten ({}): {} chars -> v{}",
                reportId, mode, body.length(), v.getVersionNum());
        onDone.run();
        return v;
    }

    private String buildRewriteMcpContext(Report report, RewriteMode mode, String instruction) {
        String topic = report.getTopic();
        if (topic == null || topic.isBlank()) return null;

        StringBuilder sb = new StringBuilder();
        try {
            switch (mode) {
                case DATA_UPDATE -> {
                    sb.append("【最新舆情数据】\n");
                    try {
                        var overview = sassMcpService.overview(topic, null, null);
                        if (overview != null) sb.append("舆情概览: ").append(overview.toPrettyString()).append("\n");
                    } catch (Exception e) { log.warn("MCP overview failed in rewrite: {}", e.getMessage()); }
                    try {
                        var hotWords = sassMcpService.hotWords(topic, null, null);
                        if (hotWords != null) sb.append("最新热词: ").append(hotWords.toPrettyString()).append("\n");
                    } catch (Exception e) { log.warn("MCP hotWords failed in rewrite: {}", e.getMessage()); }
                    try {
                        var articles = searchMcpService.searchArticles(topic, 1, 5);
                        if (articles != null) sb.append("最新文章: ").append(articles.toPrettyString()).append("\n");
                    } catch (Exception e) { log.warn("MCP search failed in rewrite: {}", e.getMessage()); }
                    if (tavilyClient.isConfigured()) {
                        try {
                            var webResults = tavilyClient.search(topic + " 最新数据 2025", 3);
                            if (webResults != null) sb.append("Web搜索最新数据: ").append(webResults.toPrettyString()).append("\n");
                        } catch (Exception e) { log.warn("Tavily search failed in rewrite: {}", e.getMessage()); }
                    }
                    sb.append("\n请使用以上最新数据替换原稿中的旧数据，保持报告结构和风格不变。");
                }
                case EXPAND -> {
                    sb.append("【扩展参考数据】\n");
                    String expandTopic = (instruction != null && !instruction.isBlank()) ? instruction : topic;
                    try {
                        var articles = searchMcpService.searchArticles(expandTopic, 1, 5);
                        if (articles != null) sb.append("相关文章: ").append(articles.toPrettyString()).append("\n");
                    } catch (Exception e) { log.warn("MCP search failed in expand: {}", e.getMessage()); }
                    if (tavilyClient.isConfigured()) {
                        try {
                            var webResults = tavilyClient.search(expandTopic + " 案例分析", 3);
                            if (webResults != null) sb.append("案例搜索: ").append(webResults.toPrettyString()).append("\n");
                        } catch (Exception e) { log.warn("Tavily search failed in expand: {}", e.getMessage()); }
                    }
                    sb.append("\n请基于以上参考数据扩展报告内容，补充新章节或新案例。");
                }
                case ANGLE_SHIFT -> {
                    sb.append("【不同视角数据】\n");
                    try {
                        var emotional = sassMcpService.emotionalDistribution(topic, null, null);
                        if (emotional != null) sb.append("情感分布(多视角): ").append(emotional.toPrettyString()).append("\n");
                    } catch (Exception e) { log.warn("MCP emotional failed in angle_shift: {}", e.getMessage()); }
                    try {
                        var hotPerson = sassMcpService.callTool("hot-person", java.util.Map.of("searchKeywordType", 1, "mustKeyWord", java.util.List.of(topic), "realTime", 30));
                        if (hotPerson != null) sb.append("关键人物: ").append(hotPerson.toPrettyString()).append("\n");
                    } catch (Exception e) { log.warn("MCP hotPerson failed: {}", e.getMessage()); }
                    sb.append("\n请从不同受众视角（如领导/公众/行业专家）重新组织报告内容。");
                }
                default -> {}
            }
        } catch (Exception e) {
            log.warn("buildRewriteMcpContext failed: {}", e.getMessage());
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    @Override
    public void streamRewriteSection(Long reportId,
                                     String sectionContent,
                                     String mode,
                                     String instruction,
                                     Long operatorId,
                                     Consumer<String> onToken,
                                     Runnable onDone) {
        if (sectionContent == null || sectionContent.isBlank()) {
            throw new BusinessException("段落内容为空，无法改写");
        }

        String systemPrompt = switch (mode) {
            case "expand" -> "你是一位专业报告撰写专家。请对用户给出的段落进行内容扩展，补充更多细节、数据支撑和深度分析，保持原有风格和语气。直接输出扩展后的段落，不要加前缀说明。";
            case "condense" -> "你是一位专业报告撰写专家。请对用户给出的段落进行精简压缩，保留核心观点和关键信息，去除冗余表述，使文字更加凝练有力。直接输出精简后的段落，不要加前缀说明。";
            default -> "你是一位专业报告撰写专家。请对用户给出的段落进行改写优化，提升表达质量和专业度。" +
                    (instruction != null && !instruction.isBlank() ? "改写要求：" + instruction : "") +
                    "直接输出改写后的段落，不要加前缀说明。";
        };

        String userPrompt = "请改写以下段落：\n\n" + sectionContent;

        StringBuilder full = new StringBuilder();
        llmClient.stream(systemPrompt, userPrompt, token -> {
            full.append(token);
            onToken.accept(token);
        }, () -> {});

        String newSection = full.toString().trim();

        Report report = reportMapper.selectById(reportId);
        if (report != null && report.getContent() != null) {
            String updatedContent = report.getContent().replace(sectionContent.trim(), newSection);
            if (!updatedContent.equals(report.getContent())) {
                report.setContent(updatedContent);
                report.setWordCount(updatedContent.length());
                reportMapper.updateById(report);

                ReportVersion latest = versionMapper.selectOne(
                        new LambdaQueryWrapper<ReportVersion>()
                                .eq(ReportVersion::getReportId, reportId)
                                .orderByDesc(ReportVersion::getVersionNum)
                                .last("LIMIT 1"));
                int baseVer = latest == null ? 0 : latest.getVersionNum();
                ReportVersion v = new ReportVersion();
                v.setReportId(reportId);
                v.setVersionNum(baseVer + 1);
                v.setTitle(report.getTitle());
                v.setContent(updatedContent);
                v.setSourceMode("rewrite_section_" + mode);
                v.setWordCount(updatedContent.length());
                v.setCreatedBy(operatorId);
                v.setChangeSummary("段落改写：%s".formatted(mode));
                versionMapper.insert(v);

                log.info("report {} section rewritten ({}): {} chars -> v{}",
                        reportId, mode, updatedContent.length(), v.getVersionNum());
            }
        }

        onDone.run();
    }
}
