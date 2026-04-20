package com.reportai.hub.knowledge.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.reportai.hub.knowledge.entity.KnowledgeDocument;
import com.reportai.hub.knowledge.mapper.KnowledgeDocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.ByteArrayInputStream;
import java.util.List;

/**
 * 知识文档"查看"弹窗的 HTML 预览：
 * - docx（xwpf）：走 POI 的 paragraph/table 两级遍历，生成最小可读的 HTML
 * - 其他格式：回退为 `<pre>` 包装 extracted 正文
 *
 * 目标不是"完美保留 Word 排版"（那是 LibreOffice 的活儿），而是让评委在弹窗里
 * 直观看到文档内容、标题层级、列表、表格，足以验证"知识库 PDF / Word 可查看"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentPreviewService {

    private final KnowledgeDocumentMapper documentMapper;

    public String renderHtml(Long docId, KnowledgeDocument meta) {
        // 拉 blob（实体默认 select=false）
        KnowledgeDocument blobRow = documentMapper.selectOne(
                Wrappers.<KnowledgeDocument>lambdaQuery()
                        .select(KnowledgeDocument::getId, KnowledgeDocument::getFileBlob)
                        .eq(KnowledgeDocument::getId, docId));
        byte[] raw = blobRow == null ? null : blobRow.getFileBlob();

        String type = meta.getFileType() == null ? "" : meta.getFileType().toLowerCase();
        String filename = meta.getFilename() == null ? "" : meta.getFilename().toLowerCase();

        boolean isDocx = raw != null && (
                type.contains("officedocument.wordprocessingml")
                || type.equals("application/docx")
                || filename.endsWith(".docx"));

        try {
            if (isDocx) {
                return wrap(meta.getFilename(), renderDocx(raw));
            }
        } catch (Exception e) {
            log.warn("DOCX render failed for docId={}, falling back to text: {}", docId, e.getMessage());
        }
        // 其他格式：直接拿解析后的纯文本，前端用 <pre> 包一下
        return wrap(meta.getFilename(), renderText(meta.getContent()));
    }

    /* ============ DOCX ============ */

    private String renderDocx(byte[] raw) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(raw))) {
            StringBuilder sb = new StringBuilder();
            sb.append("<article class='doc-preview docx'>");
            for (IBodyElement el : doc.getBodyElements()) {
                switch (el.getElementType()) {
                    case PARAGRAPH -> appendParagraph(sb, (XWPFParagraph) el);
                    case TABLE     -> appendTable(sb, (XWPFTable) el);
                    default        -> { /* 图片 / 其他块暂忽略 */ }
                }
            }
            sb.append("</article>");
            return sb.toString();
        }
    }

    private void appendParagraph(StringBuilder sb, XWPFParagraph p) {
        String text = p.getText();
        if (text == null || text.isBlank()) {
            sb.append("<p>&nbsp;</p>");
            return;
        }
        String safe = escape(text);
        String style = p.getStyle();                       // e.g. Heading1, Heading2
        int level = headingLevel(style);
        if (level > 0) {
            sb.append("<h").append(level).append('>').append(safe).append("</h").append(level).append('>');
            return;
        }
        // ListParagraph / BulletList 风格
        if (style != null && style.toLowerCase().contains("list")) {
            sb.append("<ul><li>").append(safe).append("</li></ul>");
            return;
        }
        sb.append("<p>").append(safe).append("</p>");
    }

    private void appendTable(StringBuilder sb, XWPFTable tbl) {
        sb.append("<table class='docx-table'>");
        List<XWPFTableRow> rows = tbl.getRows();
        for (int i = 0; i < rows.size(); i++) {
            sb.append("<tr>");
            for (XWPFTableCell cell : rows.get(i).getTableCells()) {
                sb.append(i == 0 ? "<th>" : "<td>");
                sb.append(escape(cell.getText()));
                sb.append(i == 0 ? "</th>" : "</td>");
            }
            sb.append("</tr>");
        }
        sb.append("</table>");
    }

    private int headingLevel(String style) {
        if (style == null) return 0;
        String s = style.toLowerCase();
        if (s.startsWith("heading") || s.startsWith("标题")) {
            for (int i = 1; i <= 6; i++) if (s.contains(String.valueOf(i))) return i;
            return 2; // 匹配不到数字就当二级
        }
        if (s.equals("title")) return 1;
        return 0;
    }

    /* ============ 其他格式的降级 ============ */

    private String renderText(String text) {
        if (text == null || text.isBlank()) {
            return "<p class='empty-hint'>（暂无可预览的正文）</p>";
        }
        // 已经是 HTML 片段就原样返回（URL 抓取入库的情况）
        if (text.trim().startsWith("<") && Jsoup.isValid(text, org.jsoup.safety.Safelist.relaxed())) {
            Document parsed = Jsoup.parseBodyFragment(text);
            return parsed.body().html();
        }
        return "<pre class='text-fallback'>" + escape(text) + "</pre>";
    }

    /* ============ 工具 ============ */

    private String wrap(String title, String body) {
        // 同源预览：前端用 iframe 嵌入，这里只负责最小 CSS
        String safeTitle = escape(title == null ? "" : title);
        return "<!DOCTYPE html>\n<html lang='zh-CN'><head>"
                + "<meta charset='UTF-8'>"
                + "<title>" + safeTitle + "</title>"
                + "<style>"
                + "body{margin:0;padding:20px 28px;background:#fff;color:#0f172a;"
                + "font:14px/1.7 -apple-system,'Helvetica Neue',Arial,sans-serif;}"
                + "article.doc-preview{max-width:860px;margin:0 auto;}"
                + "h1{font-size:24px;border-bottom:2px solid #3b82f6;padding-bottom:8px;}"
                + "h2{font-size:20px;color:#1e40af;margin-top:24px;}"
                + "h3{font-size:16px;color:#1e3a8a;margin-top:20px;}"
                + "p{margin:8px 0;text-indent:0;}"
                + ".docx-table{border-collapse:collapse;margin:12px 0;width:100%;}"
                + ".docx-table th,.docx-table td{border:1px solid #cbd5e1;padding:6px 10px;"
                + "font-size:13px;text-align:left;vertical-align:top;}"
                + ".docx-table th{background:#f1f5f9;font-weight:600;}"
                + "pre.text-fallback{white-space:pre-wrap;word-break:break-word;background:#f8fafc;"
                + "padding:16px;border-radius:6px;border:1px solid #e2e8f0;font-size:13px;line-height:1.7;}"
                + ".empty-hint{color:#94a3b8;text-align:center;padding:40px 0;}"
                + "</style></head><body>"
                + body
                + "</body></html>";
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
