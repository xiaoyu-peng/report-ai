package com.reportai.hub.report.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reportai.hub.common.exception.BusinessException;
import com.reportai.hub.knowledge.dto.RagChunkHit;
import com.reportai.hub.knowledge.service.RagSearchService;
import com.reportai.hub.report.dto.ReportCreateDTO;
import com.reportai.hub.report.entity.Report;
import com.reportai.hub.report.entity.ReportTemplate;
import com.reportai.hub.report.entity.ReportVersion;
import com.reportai.hub.common.llm.LlmClient;
import com.reportai.hub.report.mapper.ReportMapper;
import com.reportai.hub.report.mapper.ReportTemplateMapper;
import com.reportai.hub.report.mapper.ReportVersionMapper;
import com.reportai.hub.report.prompt.Prompts;
import com.reportai.hub.report.service.ReportGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportGenerationServiceImpl implements ReportGenerationService {

    /** 单报告检索拉取多少 chunk；评分"检索准确性"要求溯源清晰，不要贪多。 */
    private static final int RAG_TOP_K = 8;

    private final ReportMapper reportMapper;
    private final ReportTemplateMapper templateMapper;
    private final ReportVersionMapper versionMapper;
    private final RagSearchService ragSearchService;
    private final LlmClient llmClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Report createDraft(ReportCreateDTO dto, Long operatorId) {
        Report r = new Report();
        r.setTitle(dto.getTitle());
        r.setTopic(dto.getTopic());
        r.setKbId(dto.getKbId());
        r.setTemplateId(dto.getTemplateId());
        r.setStatus("draft");
        r.setWordCount(0);
        r.setCreatedBy(operatorId);
        try {
            r.setKeyPoints(mapper.writeValueAsString(
                    dto.getKeyPoints() == null ? List.of() : dto.getKeyPoints()));
        } catch (Exception e) {
            r.setKeyPoints("[]");
        }
        reportMapper.insert(r);
        return r;
    }

    @Override
    public void streamGenerate(Long reportId, Long operatorId,
                               Consumer<String> onToken, Runnable onDone) {
        Report report = reportMapper.selectById(reportId);
        if (report == null) throw new BusinessException("报告不存在: " + reportId);

        // 1. 风格指南
        String styleJson = "{}";
        if (report.getTemplateId() != null) {
            ReportTemplate tpl = templateMapper.selectById(report.getTemplateId());
            if (tpl != null && tpl.getStructureJson() != null) {
                styleJson = tpl.getStructureJson();
            }
        }

        // 2. RAG 检索：用 topic + keyPoints 拼查询
        String query = buildQuery(report);
        List<RagChunkHit> hits = Collections.emptyList();
        if (report.getKbId() != null) {
            hits = ragSearchService.searchRaw(report.getKbId(), query, RAG_TOP_K);
        }
        String ragContext = renderRagContext(hits);

        // 3. 组 prompt
        String keyPointsBullets = renderKeyPoints(report);
        String user = Prompts.buildGenerationUser(
                report.getTopic(), keyPointsBullets, styleJson, ragContext);

        // 4. 流式调用
        StringBuilder full = new StringBuilder();
        report.setStatus("generating");
        reportMapper.updateById(report);

        try {
            llmClient.stream(Prompts.GENERATION_SYSTEM, user, token -> {
                full.append(token);
                onToken.accept(token);
            }, () -> {});
        } catch (RuntimeException e) {
            report.setStatus("draft");
            reportMapper.updateById(report);
            throw e;
        }

        // 5. 落库 + v1 版本
        String body = full.toString();
        report.setContent(body);
        report.setWordCount(body.length());
        report.setStatus("ready");
        reportMapper.updateById(report);

        ReportVersion v = new ReportVersion();
        v.setReportId(reportId);
        v.setVersionNum(1);
        v.setTitle(report.getTitle());
        v.setContent(body);
        v.setSourceMode("initial");
        v.setWordCount(body.length());
        v.setCreatedBy(operatorId);
        v.setChangeSummary("首次 AI 生成");
        versionMapper.insert(v);

        log.info("report {} generated: {} chars, provider={}",
                reportId, body.length(), llmClient.providerName());

        onDone.run();
    }

    // ---------------- helpers ----------------

    private String buildQuery(Report report) {
        StringBuilder sb = new StringBuilder();
        sb.append(report.getTopic() == null ? "" : report.getTopic());
        String kp = report.getKeyPoints();
        if (kp != null && !kp.isBlank()) {
            try {
                List<String> list = mapper.readValue(kp, new TypeReference<List<String>>() {});
                list.forEach(s -> sb.append(' ').append(s));
            } catch (Exception ignore) {
                sb.append(' ').append(kp);
            }
        }
        return sb.toString();
    }

    private String renderKeyPoints(Report report) {
        String kp = report.getKeyPoints();
        if (kp == null || kp.isBlank()) return "（无）";
        try {
            List<String> list = mapper.readValue(kp, new TypeReference<List<String>>() {});
            if (list.isEmpty()) return "（无）";
            return list.stream().map(s -> "- " + s).collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "- " + kp;
        }
    }

    private String renderRagContext(List<RagChunkHit> hits) {
        if (hits == null || hits.isEmpty()) return "（无）";
        return IntStream.range(0, hits.size())
                .mapToObj(i -> {
                    RagChunkHit h = hits.get(i);
                    return "[%d: %s / 第 %d 段]\n%s".formatted(
                            i + 1,
                            h.getFilename() == null ? "" : h.getFilename(),
                            h.getChunkIndex() == null ? 0 : h.getChunkIndex(),
                            h.getContent() == null ? "" : h.getContent());
                })
                .collect(Collectors.joining("\n\n"));
    }
}
