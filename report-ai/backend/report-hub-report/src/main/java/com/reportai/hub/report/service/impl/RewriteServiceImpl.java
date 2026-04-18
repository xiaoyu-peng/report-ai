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

        String body = full.toString();
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
}
