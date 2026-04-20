package com.reportai.hub.report.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.reportai.hub.knowledge.dto.RagChunkHit;
import com.reportai.hub.report.entity.ReportCitation;
import com.reportai.hub.report.mapper.ReportCitationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把生成的报告 markdown 里的 [n] 角标解析回来源 chunk，写入 report_citation 表。
 * 前端 hover [n] 弹出 popover 时直接读这张表。
 *
 * <p>注意：n 是 1-based，对应 RAG 命中列表 hits[n-1]。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CitationParser {

    /** 匹配 [1] [12] 等行内角标；保守起见只匹配纯数字，避免与 markdown 链接 [text](url) 混淆。 */
    private static final Pattern MARKER = Pattern.compile("\\[(\\d{1,3})\\](?!\\()");

    private final ReportCitationMapper citationMapper;

    /**
     * 解析正文中所有 [n]，把"用到了第 n 条 chunk"持久化为一行 report_citation。
     * 同一报告 + 同一 marker + 同一 chunk 仅入库一次（dedup）。
     *
     * @param markdown      生成的报告全文
     * @param hits          生成时使用的 RAG 命中列表（顺序 = 编号 - 1）
     * @param reportId      报告 ID
     * @param versionId     可选：关联到具体版本（章节级生成时为该 section 的版本）
     * @param sectionIndex  章节索引（整篇生成统一为 0）
     * @return 实际入库的 citation 行数
     */
    @Transactional
    public int parseAndPersist(String markdown, List<RagChunkHit> hits,
                               Long reportId, Long versionId, int sectionIndex) {
        if (markdown == null || markdown.isBlank() || hits == null || hits.isEmpty()) return 0;

        // 先清掉本报告 + 本 section 的旧引用，避免重复生成 / 重新嵌入时残留
        citationMapper.delete(new QueryWrapper<ReportCitation>()
                .eq("report_id", reportId)
                .eq("section_index", sectionIndex));

        // 段落定位：按双换行切段，记录每个 [n] 落在第几段
        String[] paragraphs = markdown.split("\\n{2,}");
        Set<String> dedup = new HashSet<>();   // key = marker + ":" + chunkId
        int inserted = 0;

        for (int pIdx = 0; pIdx < paragraphs.length; pIdx++) {
            Matcher m = MARKER.matcher(paragraphs[pIdx]);
            while (m.find()) {
                int marker;
                try {
                    marker = Integer.parseInt(m.group(1));
                } catch (NumberFormatException nfe) { continue; }
                if (marker < 1 || marker > hits.size()) {
                    log.debug("citation marker [{}] out of range (hits={}), skip", marker, hits.size());
                    continue;
                }
                RagChunkHit hit = hits.get(marker - 1);
                if (hit == null) continue;

                String key = marker + ":" + hit.getChunkId();
                if (!dedup.add(key)) continue;  // 同段内多次提到，只持久化一次

                ReportCitation row = new ReportCitation();
                row.setReportId(reportId);
                row.setVersionId(versionId);
                row.setSectionIndex(sectionIndex);
                row.setParagraphIndex(pIdx);
                row.setCitationMarker(marker);
                row.setChunkId(hit.getChunkId());
                row.setDocId(hit.getDocId());
                row.setKbId(hit.getKbId());
                row.setDocTitle(hit.getFilename());
                row.setPageStart(hit.getPageStart());
                row.setPageEnd(hit.getPageEnd());
                row.setSnippet(truncateSnippet(hit.getContent()));
                row.setAccepted(true);
                citationMapper.insert(row);
                inserted++;
            }
        }
        log.info("persisted {} citations for report={} section={}", inserted, reportId, sectionIndex);
        return inserted;
    }

    private String truncateSnippet(String text) {
        if (text == null) return null;
        return text.length() > 200 ? text.substring(0, 200) + "..." : text;
    }
}
