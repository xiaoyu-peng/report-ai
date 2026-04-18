package com.reportai.hub.report.service;

import com.reportai.hub.report.entity.Report;

import java.io.OutputStream;

/**
 * 把报告正文（Markdown 风格，中文标号）写成 .docx。
 * <p>
 * 赛题模块五要求"支持导出 Word/PDF"。DOCX 在服务端用 Apache POI 生成；
 * PDF 由前端 html2pdf 从预览 DOM 产出（避免引入 iText 等重依赖）。
 */
public interface DocxExportService {
    /** 把 report 的标题 + 正文写入 out（完整 .docx 字节流）。 */
    void writeDocx(Report report, OutputStream out);
}
