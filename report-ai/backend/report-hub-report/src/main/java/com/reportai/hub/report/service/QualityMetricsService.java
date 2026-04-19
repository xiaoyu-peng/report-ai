package com.reportai.hub.report.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reportai.hub.common.exception.BusinessException;
import com.reportai.hub.knowledge.entity.KnowledgeBase;
import com.reportai.hub.knowledge.entity.KnowledgeDocument;
import com.reportai.hub.knowledge.mapper.KnowledgeBaseMapper;
import com.reportai.hub.knowledge.mapper.KnowledgeDocumentMapper;
import com.reportai.hub.report.dto.QualityReport;
import com.reportai.hub.report.entity.Report;
import com.reportai.hub.report.entity.ReportCitation;
import com.reportai.hub.report.entity.ReportQuality;
import com.reportai.hub.report.mapper.ReportCitationMapper;
import com.reportai.hub.report.mapper.ReportMapper;
import com.reportai.hub.report.mapper.ReportQualityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * T5 报告质量体检（覆盖度仪表盘 ④ 的数据源）。
 *
 * <p>三个指标：
 * 1. 覆盖率 = 含 [n] 角标的段落数 / 总段落数
 * 2. KB 命中分布 = 按 doc.kb_id 聚合 citation count
 * 3. 事实性可疑列表 = 复用 QualityCheckService 的 LLM-as-judge factualityIssues
 *
 * <p>结果落 report_quality 表；前端 CoverageDashboard 直接读。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QualityMetricsService {

    private final ReportMapper reportMapper;
    private final ReportCitationMapper citationMapper;
    private final ReportQualityMapper qualityMapper;
    private final KnowledgeDocumentMapper docMapper;
    private final KnowledgeBaseMapper kbMapper;
    private final QualityCheckService llmQualityService;
    private final ObjectMapper jsonMapper = new ObjectMapper();

    @Transactional
    public ReportQuality runCheck(Long reportId) {
        Report report = reportMapper.selectById(reportId);
        if (report == null) throw new BusinessException("报告不存在: " + reportId);
        if (report.getContent() == null || report.getContent().isBlank()) {
            throw new BusinessException("报告正文为空，无可体检");
        }

        // 1. 段落总数（按 \n\n 切，去掉空段）
        String[] paras = report.getContent().split("\\n{2,}");
        int paragraphsTotal = 0;
        for (String p : paras) if (!p.trim().isEmpty()) paragraphsTotal++;

        // 2. 引用统计
        List<ReportCitation> citations = citationMapper.selectList(new QueryWrapper<ReportCitation>()
                .eq("report_id", reportId).eq("accepted", true));
        int citationsTotal = citations.size();
        Set<String> citedParas = citations.stream()
                .map(c -> c.getSectionIndex() + ":" + c.getParagraphIndex())
                .collect(Collectors.toSet());
        int paragraphsCited = citedParas.size();

        BigDecimal coverageRate = paragraphsTotal == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(paragraphsCited * 100.0 / paragraphsTotal)
                        .setScale(2, RoundingMode.HALF_UP);

        // 3. KB 分布：citation → doc → kb_id → kb.name → count
        Map<String, Long> kbCount = computeKbDistribution(citations);
        String kbDistJson = toJson(kbCount, "{}");

        // 4. 事实性可疑：复用 LLM-as-judge 的 factualityIssues
        String suspiciousJson = "[]";
        try {
            QualityReport llm = llmQualityService.check(reportId);
            if (llm != null && llm.getFactualityIssues() != null) {
                suspiciousJson = toJson(llm.getFactualityIssues(), "[]");
            }
        } catch (Exception e) {
            log.warn("LLM quality check failed for report {}: {}", reportId, e.getMessage());
        }

        // 5. 持久化（覆盖式）
        ReportQuality q = new ReportQuality();
        q.setReportId(reportId);
        q.setCoverageRate(coverageRate);
        q.setCitationsTotal(citationsTotal);
        q.setParagraphsTotal(paragraphsTotal);
        q.setParagraphsCited(paragraphsCited);
        q.setKbDistribution(kbDistJson);
        q.setSuspiciousFacts(suspiciousJson);
        q.setCheckedAt(LocalDateTime.now());

        qualityMapper.deleteById(reportId);
        qualityMapper.insert(q);

        log.info("quality check done report={} coverage={}% citations={}/{} paragraphs",
                reportId, coverageRate, paragraphsCited, paragraphsTotal);
        return q;
    }

    public ReportQuality get(Long reportId) {
        return qualityMapper.selectById(reportId);
    }

    private Map<String, Long> computeKbDistribution(List<ReportCitation> citations) {
        if (citations.isEmpty()) return Map.of();
        Set<Long> docIds = citations.stream().map(ReportCitation::getDocId).collect(Collectors.toSet());
        Map<Long, Long> docToKb = docMapper.selectBatchIds(docIds).stream()
                .collect(Collectors.toMap(KnowledgeDocument::getId, KnowledgeDocument::getKbId));
        Set<Long> kbIds = new HashSet<>(docToKb.values());
        Map<Long, String> kbNames = kbIds.isEmpty() ? Map.of()
                : kbMapper.selectBatchIds(kbIds).stream()
                        .collect(Collectors.toMap(KnowledgeBase::getId, KnowledgeBase::getName));

        Map<String, Long> count = new LinkedHashMap<>();
        for (ReportCitation c : citations) {
            Long kbId = docToKb.get(c.getDocId());
            String name = kbId == null ? "其他" : kbNames.getOrDefault(kbId, "未知 KB#" + kbId);
            count.merge(name, 1L, Long::sum);
        }
        return count;
    }

    private String toJson(Object o, String fallback) {
        try { return jsonMapper.writeValueAsString(o); }
        catch (Exception e) { return fallback; }
    }
}
