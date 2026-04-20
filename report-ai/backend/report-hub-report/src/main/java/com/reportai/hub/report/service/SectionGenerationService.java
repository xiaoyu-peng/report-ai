package com.reportai.hub.report.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.reportai.hub.common.exception.BusinessException;
import com.reportai.hub.common.llm.LlmClient;
import com.reportai.hub.knowledge.dto.RagChunkHit;
import com.reportai.hub.knowledge.dto.RagSearchQuery;
import com.reportai.hub.knowledge.service.RagSearchService;
import com.reportai.hub.report.entity.Report;
import com.reportai.hub.report.entity.ReportSection;
import com.reportai.hub.report.mapper.ReportMapper;
import com.reportai.hub.report.mapper.ReportSectionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 章节级流式生成（5 杀手锏交互之"大纲拖拽 + 章节级独立流式"）。
 *
 * <p>流程：
 * 1. initSections：拆 outline 为 N 个 report_section 行，全 pending 入库
 * 2. streamSection：单章独立 RAG → 流式 LLM → 持久化 + 解析引用
 * 3. 失败可单章重试，整篇生成不阻塞
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SectionGenerationService {

    private static final int SECTION_RAG_TOP_K = 8;

    private final ReportSectionMapper sectionMapper;
    private final ReportMapper reportMapper;
    private final RagSearchService ragSearchService;
    private final LlmClient llmClient;
    private final CitationParser citationParser;

    /**
     * 把大纲拆成 sections 行入库。按标题合并：新 outline 中已有同标题 done 章节的，
     * 保留原 content / wordCount / citationCount / 时间戳；其余为 pending 待生成。
     *
     * <p>outline 元素：{title, prompt, content?, status?}
     * <ul>
     *   <li>content 非空：以 done 状态直接入库（"导入原报告"场景）</li>
     *   <li>title 命中已有 done 章节：保留原内容（OutlineEditor 增删章节场景）</li>
     *   <li>其余：status=pending</li>
     * </ul>
     * 被删除的旧章节内容不会自动保留——用户在 OutlineEditor 里拿掉一个标题即视为放弃该章。
     */
    @Transactional
    public List<ReportSection> initSections(Long reportId, List<Map<String, String>> outline) {
        if (reportId == null) throw new BusinessException("reportId required");
        if (outline == null || outline.isEmpty()) throw new BusinessException("大纲不能为空");

        Report r = reportMapper.selectById(reportId);
        if (r == null) throw new BusinessException("报告不存在: " + reportId);

        List<ReportSection> existing = sectionMapper.selectList(
                new QueryWrapper<ReportSection>().eq("report_id", reportId));
        Map<String, ReportSection> byTitle = existing.stream()
                .filter(s -> s.getTitle() != null && !s.getTitle().isBlank())
                .collect(Collectors.toMap(
                        s -> s.getTitle().trim(),
                        s -> s,
                        (a, b) -> "done".equals(a.getStatus()) ? a : b));

        sectionMapper.delete(new QueryWrapper<ReportSection>().eq("report_id", reportId));

        List<ReportSection> rows = new ArrayList<>(outline.size());
        int preservedCount = 0, importedCount = 0;
        for (int i = 0; i < outline.size(); i++) {
            Map<String, String> item = outline.get(i);
            ReportSection s = new ReportSection();
            s.setReportId(reportId);
            s.setSectionIndex(i);
            s.setTitle(item == null ? null : item.get("title"));
            s.setPrompt(item == null ? null : item.get("prompt"));

            String title = s.getTitle() == null ? null : s.getTitle().trim();
            ReportSection prev = title != null ? byTitle.get(title) : null;
            String preContent = item == null ? null : item.get("content");

            if (prev != null && "done".equals(prev.getStatus())
                    && prev.getContent() != null && !prev.getContent().isBlank()) {
                s.setContent(prev.getContent());
                s.setStatus("done");
                s.setWordCount(prev.getWordCount());
                s.setCitationCount(prev.getCitationCount());
                s.setStartedAt(prev.getStartedAt());
                s.setFinishedAt(prev.getFinishedAt());
                preservedCount++;
            } else if (preContent != null && !preContent.isBlank()) {
                s.setContent(preContent);
                s.setStatus("done");
                s.setWordCount(preContent.length());
                s.setCitationCount(0);
                s.setFinishedAt(LocalDateTime.now());
                importedCount++;
            } else {
                s.setStatus("pending");
                s.setWordCount(0);
                s.setCitationCount(0);
            }
            sectionMapper.insert(s);
            rows.add(s);
        }
        log.info("init {} sections for report {} (preserved {} / imported {} / pending {})",
                rows.size(), reportId, preservedCount, importedCount,
                rows.size() - preservedCount - importedCount);
        return rows;
    }

    /** 流式生成单章；emit 接受标准 SSE event 字符串（含 event:/data: 行）。 */
    public void streamSection(Long reportId, int sectionIndex, List<Long> kbIds,
                              Consumer<String> emit) {
        ReportSection s = sectionMapper.selectOne(new QueryWrapper<ReportSection>()
                .eq("report_id", reportId).eq("section_index", sectionIndex));
        if (s == null) throw new BusinessException("章节不存在: " + sectionIndex);

        s.setStatus("generating");
        s.setStartedAt(LocalDateTime.now());
        sectionMapper.updateById(s);
        emit.accept(sse("start", "{\"sectionIndex\":" + sectionIndex + "}"));

        try {
            // 1. RAG 检索（绑定 reportId 自动过滤已 excluded chunk）
            RagSearchQuery q = new RagSearchQuery();
            q.setReportId(reportId);
            q.setKbIds(kbIds);
            q.setQuery((s.getTitle() == null ? "" : s.getTitle()) + " " +
                       (s.getPrompt() == null ? "" : s.getPrompt()));
            q.setTopK(SECTION_RAG_TOP_K);
            List<RagChunkHit> hits = ragSearchService.search(q).getHits();

            // 推送命中 chunk 给前端"参考资料"面板
            emit.accept(sse("chunks", chunksJson(hits)));

            // 2. 组 prompt
            String system = sectionSystemPrompt();
            String user = sectionUserPrompt(s, hits);

            // 3. 流式 LLM
            StringBuilder full = new StringBuilder();
            llmClient.stream(system, user, token -> {
                full.append(token);
                emit.accept(sse("token", "\"" + escapeJson(token) + "\""));
            }, () -> {});

            // 4. 收尾：解析引用 + 持久化
            String body = full.toString();
            int citations = citationParser.parseAndPersist(body, hits, reportId, null, sectionIndex);

            s.setContent(body);
            s.setWordCount(body.length());
            s.setCitationCount(citations);
            s.setStatus("done");
            s.setFinishedAt(LocalDateTime.now());
            sectionMapper.updateById(s);

            emit.accept(sse("done",
                    "{\"sectionIndex\":" + sectionIndex +
                    ",\"wordCount\":" + body.length() +
                    ",\"citationCount\":" + citations + "}"));
            log.info("section {} of report {} done: {} chars / {} citations",
                    sectionIndex, reportId, body.length(), citations);
        } catch (RuntimeException e) {
            s.setStatus("failed");
            s.setFinishedAt(LocalDateTime.now());
            sectionMapper.updateById(s);
            emit.accept(sse("error", "\"" + escapeJson(e.getMessage()) + "\""));
            log.error("section {} of report {} failed: {}", sectionIndex, reportId, e.getMessage(), e);
        }
    }

    /** 列出某报告的所有 section（按 sectionIndex 升序）。 */
    public List<ReportSection> list(Long reportId) {
        return sectionMapper.selectList(new QueryWrapper<ReportSection>()
                .eq("report_id", reportId)
                .orderByAsc("section_index"));
    }

    /** 把所有 done 的 section 拼成完整 markdown 写回 report.content（章节流式完成后调用）。 */
    @Transactional
    public String assembleAndSave(Long reportId) {
        List<ReportSection> sections = list(reportId);
        StringBuilder full = new StringBuilder();
        for (ReportSection s : sections) {
            if (!"done".equals(s.getStatus())) continue;
            if (s.getTitle() != null && !s.getTitle().isBlank()) {
                full.append("## ").append(s.getTitle()).append("\n\n");
            }
            full.append(s.getContent() == null ? "" : s.getContent()).append("\n\n");
        }
        String body = full.toString().trim();

        Report r = reportMapper.selectById(reportId);
        if (r != null) {
            r.setContent(body);
            r.setWordCount(body.length());
            r.setStatus("ready");
            reportMapper.updateById(r);
        }
        return body;
    }

    // ---------------- helpers ----------------

    private String sectionSystemPrompt() {
        return """
                你是专业报告写作助手。请基于"参考资料"撰写指定章节的正文。

                【硬约束】
                1. 仅使用参考资料中的事实；具体数字/日期/人名若无支撑用 [待核实] 占位。
                2. 引用规范：每用到一条参考资料，在该句末尾加 [n] 角标，n 是参考资料编号。
                   多条叠加写成 [1][2]，不要写 [1,2]。
                3. 直接输出正文 markdown，**不要重复章节标题**，不要加摘要、不要用代码块包裹。
                """;
    }

    private String sectionUserPrompt(ReportSection s, List<RagChunkHit> hits) {
        String refs = hits.isEmpty() ? "（无）"
                : IntStream.range(0, hits.size())
                .mapToObj(i -> "[%d: %s%s]\n%s".formatted(
                        i + 1,
                        hits.get(i).getFilename() == null ? "" : hits.get(i).getFilename(),
                        hits.get(i).getPageStart() != null
                                ? " 第 " + hits.get(i).getPageStart() + "-" + hits.get(i).getPageEnd() + " 页"
                                : "",
                        hits.get(i).getContent()))
                .collect(Collectors.joining("\n\n"));

        return """
                【章节标题】%s
                【章节要点】%s

                【参考资料】
                %s

                请按 system 约束撰写本章正文。
                """.formatted(
                s.getTitle() == null ? "" : s.getTitle(),
                s.getPrompt() == null || s.getPrompt().isBlank() ? "（无）" : s.getPrompt(),
                refs);
    }

    private String chunksJson(List<RagChunkHit> hits) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < hits.size(); i++) {
            RagChunkHit h = hits.get(i);
            if (i > 0) sb.append(',');
            sb.append('{')
              .append("\"marker\":").append(i + 1).append(',')
              .append("\"chunkId\":").append(h.getChunkId()).append(',')
              .append("\"docId\":").append(h.getDocId()).append(',')
              .append("\"docTitle\":\"").append(escapeJson(h.getFilename())).append('"').append(',')
              .append("\"pageStart\":").append(h.getPageStart()).append(',')
              .append("\"pageEnd\":").append(h.getPageEnd())
              .append('}');
        }
        sb.append(']');
        return sb.toString();
    }

    /** SSE 单事件帧：event:xxx\\ndata:yyy\\n\\n。 */
    private String sse(String event, String data) {
        return "event: " + event + "\ndata: " + data + "\n\n";
    }

    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}]");

    private String escapeJson(String s) {
        if (s == null) return "";
        String r = s.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "");
        // 兜底剔除其他控制字符，避免破坏 SSE 帧
        return CONTROL.matcher(r).replaceAll("");
    }
}
