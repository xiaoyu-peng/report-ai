package com.reportai.hub.report.controller;

import com.reportai.hub.common.exception.BusinessException;
import com.reportai.hub.report.entity.Report;
import com.reportai.hub.report.mapper.ReportMapper;
import com.reportai.hub.report.service.DocxExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 报告导出。模块五要求导出 Word / PDF；
 * DOCX 在此端点生成；PDF 由前端 html2pdf 从预览视图生成，后端无需对应端点。
 */
@Slf4j
@Tag(name = "报告导出")
@RestController
@RequestMapping("/api/v1/reports/{id}/export")
@RequiredArgsConstructor
public class ExportController {

    private final ReportMapper reportMapper;
    private final DocxExportService docxExportService;

    @GetMapping("/docx")
    @Operation(summary = "导出 .docx —— 浏览器直接下载")
    public void exportDocx(@PathVariable("id") Long id,
                           HttpServletResponse response) throws IOException {
        Report report = reportMapper.selectById(id);
        if (report == null) throw new BusinessException("报告不存在: " + id);

        String filename = safeFilename(report.getTitle(), "report") + ".docx";
        response.setContentType(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        // RFC 5987：中文标题用 filename*=UTF-8''... 通过 URL 编码保留
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded);
        response.setHeader("Cache-Control", "no-store");

        docxExportService.writeDocx(report, response.getOutputStream());
        response.getOutputStream().flush();
    }

    /** 剥掉 Windows / POSIX 都忌讳的字符，留一个安全的基名。 */
    private String safeFilename(String raw, String fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        String cleaned = raw.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "").strip();
        if (cleaned.length() > 80) cleaned = cleaned.substring(0, 80);
        return cleaned.isEmpty() ? fallback : cleaned;
    }

    /** jakarta 的 HttpServletResponse.MediaType 在我们的 boot 版本没暴露；这里直接用字符串以避免误导。 */
    @SuppressWarnings("unused")
    private static final MediaType DOCX_MEDIA =
            MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
}
