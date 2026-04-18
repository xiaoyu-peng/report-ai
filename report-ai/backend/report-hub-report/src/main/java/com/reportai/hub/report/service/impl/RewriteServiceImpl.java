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

        String system = Prompts.rewriteSystemFor(mode, instruction);
        String user = Prompts.buildRewriteUser(original);

        StringBuilder full = new StringBuilder();
        llmClient.stream(system, user, token -> {
            full.append(token);
            onToken.accept(token);
        }, () -> {});

        // CONTINUATION 下，LLM 只产出新章节；服务端负责把原稿保持不变地拼到前面。
        // 前端同步约定：continuation 时不清空正文，只把新 token 追加到末尾 —— 这样
        // SSE 流出来的内容恰好是"新章节"，视觉上像是在原文末尾接着写。
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
