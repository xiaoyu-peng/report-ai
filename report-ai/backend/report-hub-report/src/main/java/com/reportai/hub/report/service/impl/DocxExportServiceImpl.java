package com.reportai.hub.report.service.impl;

import com.reportai.hub.common.exception.BusinessException;
import com.reportai.hub.report.entity.Report;
import com.reportai.hub.report.entity.ReportCitation;
import com.reportai.hub.report.service.DocxExportService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown → DOCX 的轻量转换器。只覆盖报告场景必要的语法：
 *   - 空行 = 段落分隔
 *   - `#` / `##` / `###` = 标题层级
 *   - 行首中文标号（"一、"、"（一）"、"第X章"）识别为标题
 *   - 行内 `[n]` 引用角标 → 上标 run
 * 其他 Markdown 语法（列表、代码块、强调等）在报告里几乎不出现，暂不处理。
 */
@Slf4j
@Service
public class DocxExportServiceImpl implements DocxExportService {

    /** 中文顶层编号："一、"至"十、"开头 */
    private static final Pattern TOP_CN = Pattern.compile("^[一二三四五六七八九十百]+[、.．]");
    /** 中文次级编号："（一）"开头 */
    private static final Pattern SUB_CN = Pattern.compile("^（[一二三四五六七八九十]+）");
    /** "第X章 / 第X节" */
    private static final Pattern CHAPTER_CN = Pattern.compile("^第[一二三四五六七八九十0-9]+[章节篇]");
    /** 行内 [n] 角标，捕获编号 */
    private static final Pattern INLINE_CITE = Pattern.compile("\\[(\\d+)]");

    @Override
    public void writeDocx(Report report, OutputStream out) {
        writeDocx(report, Collections.emptyList(), out);
    }

    @Override
    public void writeDocx(Report report, List<ReportCitation> citations, OutputStream out) {
        if (report == null) throw new BusinessException("报告不存在");
        String body = report.getContent() == null ? "" : report.getContent();

        try (XWPFDocument doc = new XWPFDocument()) {
            writeTitle(doc, report.getTitle());
            for (String block : splitBlocks(body)) {
                writeBlock(doc, block);
            }
            if (citations != null && !citations.isEmpty()) {
                writeCitationsAppendix(doc, citations);
            }
            doc.write(out);
        } catch (IOException e) {
            throw new BusinessException("DOCX 生成失败: " + e.getMessage());
        }
    }

    /** 文末「引用列表」附录：每条 [n] = docTitle (第 X-Y 页) + snippet。 */
    private void writeCitationsAppendix(XWPFDocument doc, List<ReportCitation> citations) {
        // 标题
        XWPFParagraph titleP = doc.createParagraph();
        titleP.setSpacingBefore(360);
        titleP.setSpacingAfter(120);
        XWPFRun titleR = titleP.createRun();
        titleR.setBold(true);
        titleR.setFontSize(15);
        titleR.setText("引用列表");

        for (ReportCitation c : citations) {
            XWPFParagraph p = doc.createParagraph();
            p.setSpacingAfter(80);
            p.setSpacingBetween(1.4, org.apache.poi.xwpf.usermodel.LineSpacingRule.AUTO);

            // [n] 编号（蓝色加粗）
            XWPFRun numRun = p.createRun();
            numRun.setBold(true);
            numRun.setColor("409EFF");
            numRun.setText("[" + c.getCitationMarker() + "] ");

            // 文档标题
            XWPFRun titleRun = p.createRun();
            titleRun.setBold(true);
            titleRun.setText(c.getDocTitle() == null ? "未知来源" : c.getDocTitle());

            // 页码
            if (c.getPageStart() != null) {
                XWPFRun pageRun = p.createRun();
                pageRun.setColor("909399");
                pageRun.setText("（第 " + c.getPageStart() +
                        (c.getPageEnd() != null && !c.getPageEnd().equals(c.getPageStart())
                                ? "-" + c.getPageEnd() : "") + " 页）");
            }

            // 原文片段
            if (c.getSnippet() != null && !c.getSnippet().isBlank()) {
                XWPFRun snipRun = p.createRun();
                snipRun.addBreak();
                snipRun.setColor("606266");
                snipRun.setItalic(true);
                snipRun.setText(c.getSnippet());
            }
        }
    }

    private void writeTitle(XWPFDocument doc, String title) {
        if (title == null || title.isBlank()) return;
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        p.setSpacingAfter(360);
        XWPFRun run = p.createRun();
        run.setBold(true);
        run.setFontSize(20);
        run.setText(title);
    }

    /** 按连续空行切块。块内保留单个 \n 作为软换行（大多报告实际写法是空行隔段，不会软换）。 */
    private String[] splitBlocks(String body) {
        if (body.isBlank()) return new String[0];
        return body.split("\\n\\s*\\n+");
    }

    private void writeBlock(XWPFDocument doc, String block) {
        String text = block.strip();
        if (text.isEmpty()) return;

        // Markdown 标题
        int hashes = 0;
        while (hashes < text.length() && text.charAt(hashes) == '#') hashes++;
        if (hashes > 0 && hashes <= 3 && hashes < text.length() && text.charAt(hashes) == ' ') {
            writeHeading(doc, text.substring(hashes + 1).strip(), hashes);
            return;
        }

        // 中文标号 → 标题
        String firstLine = text.contains("\n") ? text.substring(0, text.indexOf('\n')) : text;
        if (TOP_CN.matcher(firstLine).find()) {
            writeHeading(doc, text, 1);
            return;
        }
        if (SUB_CN.matcher(firstLine).find() || CHAPTER_CN.matcher(firstLine).find()) {
            writeHeading(doc, text, 2);
            return;
        }

        writeParagraph(doc, text);
    }

    private void writeHeading(XWPFDocument doc, String text, int level) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(240);
        p.setSpacingAfter(120);
        XWPFRun run = p.createRun();
        run.setBold(true);
        run.setFontSize(switch (level) {
            case 1 -> 16;
            case 2 -> 14;
            default -> 13;
        });
        run.setText(text);
    }

    /** 正文段落：把 [n] 角标抽出来做成 superscript run，其余按普通文本。 */
    private void writeParagraph(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingAfter(120);
        // 行距 1.5：POI 里用 lineRule=AUTO + line=360（240 = 单倍行距，360 = 1.5 倍）
        p.setSpacingBetween(1.5, org.apache.poi.xwpf.usermodel.LineSpacingRule.AUTO);

        Matcher m = INLINE_CITE.matcher(text);
        int cursor = 0;
        while (m.find()) {
            if (m.start() > cursor) {
                addRun(p, text.substring(cursor, m.start()), false);
            }
            addRun(p, "[" + m.group(1) + "]", true);
            cursor = m.end();
        }
        if (cursor < text.length()) {
            addRun(p, text.substring(cursor), false);
        }
    }

    private void addRun(XWPFParagraph p, String chunk, boolean superscript) {
        if (chunk.isEmpty()) return;
        XWPFRun run = p.createRun();
        if (superscript) {
            run.setSubscript(org.apache.poi.xwpf.usermodel.VerticalAlign.SUPERSCRIPT);
            run.setColor("409EFF"); // 与前端角标同色，保持视觉一致
        }
        // 按 \n 切分以保留软换行
        String[] lines = chunk.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) run.addBreak();
            run.setText(lines[i]);
        }
    }
}
