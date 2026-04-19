package com.reportai.hub.report.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reportai.hub.common.exception.BusinessException;
import com.reportai.hub.common.llm.LlmClient;
import com.reportai.hub.knowledge.dto.RagChunkHit;
import com.reportai.hub.knowledge.service.RagSearchService;
import com.reportai.hub.report.dto.QualityReport;
import com.reportai.hub.report.entity.Report;
import com.reportai.hub.report.mapper.ReportMapper;
import com.reportai.hub.report.prompt.Prompts;
import com.reportai.hub.report.service.QualityCheckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 赛题 3.4 质量保障：LLM-as-judge 一次调用产出三维度评估 JSON。
 * <p>实现特点：
 * <ul>
 *   <li>复用生成侧的 keyPoints / RAG 渲染逻辑（与生成时保持一致的上下文）；</li>
 *   <li>LLM 可能返回带 Markdown 代码块的 JSON，手动剥掉后再反序列化；</li>
 *   <li>解析失败时给一个降级的 QualityReport，避免 500。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QualityCheckServiceImpl implements QualityCheckService {

    private static final int RAG_TOP_K = 8;

    private final ReportMapper reportMapper;
    private final LlmClient llmClient;
    private final RagSearchService ragSearchService;
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public QualityReport check(Long reportId) {
        Report report = reportMapper.selectById(reportId);
        if (report == null) throw new BusinessException("报告不存在: " + reportId);
        if (report.getContent() == null || report.getContent().isBlank()) {
            throw new BusinessException("报告正文为空，无可检查");
        }

        String keyPoints = renderKeyPoints(report);
        List<RagChunkHit> hits = Collections.emptyList();
        if (report.getKbId() != null) {
            String query = buildQuery(report);
            try {
                hits = ragSearchService.searchRaw(report.getKbId(), query, RAG_TOP_K);
            } catch (Exception e) {
                log.warn("quality-check RAG search failed, fall back to empty context: {}", e.getMessage());
            }
        }
        String ragContext = renderRagContext(hits);

        String system = Prompts.QUALITY_CHECK_SYSTEM;
        String user = Prompts.buildQualityCheckUser(keyPoints, report.getContent(), ragContext);

        String raw = llmClient.complete(system, user);
        return parseOrFallback(raw);
    }

    // --- helpers (与 ReportGenerationServiceImpl 保持一致的渲染规则) ---

    private String buildQuery(Report report) {
        StringBuilder sb = new StringBuilder();
        if (report.getTopic() != null) sb.append(report.getTopic()).append(' ');
        if (report.getKeyPoints() != null) sb.append(report.getKeyPoints());
        return sb.toString().trim();
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

    /** 剥可能的 Markdown 代码围栏，返回 QualityReport。解析失败时给降级报告。 */
    private QualityReport parseOrFallback(String raw) {
        String json = stripCodeFence(raw == null ? "" : raw.trim());
        try {
            return mapper.readValue(json, QualityReport.class);
        } catch (Exception e) {
            log.warn("quality JSON parse failed, giving fallback: {} | raw head: {}",
                    e.getMessage(), json.length() > 200 ? json.substring(0, 200) + "..." : json);
            QualityReport fallback = new QualityReport();
            fallback.setOverallScore(null);
            fallback.setSummary("质量检查模型返回了非 JSON 结果；请重试或检查模型配置。");
            fallback.setCoverageScore(null);
            fallback.setCitationAccuracyScore(null);
            fallback.setFactualityScore(null);
            fallback.setMissingKeyPoints(List.of());
            fallback.setCitationIssues(List.of());
            fallback.setFactualityIssues(List.of());
            return fallback;
        }
    }

    private String stripCodeFence(String s) {
        if (s.startsWith("```")) {
            int nl = s.indexOf('\n');
            if (nl >= 0) s = s.substring(nl + 1);
            if (s.endsWith("```")) s = s.substring(0, s.length() - 3);
        }
        return s.trim();
    }
}
