package com.reportai.hub.knowledge.mcp;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FetchClient {

    public String fetchUrl(String url) {
        try {
            org.jsoup.nodes.Document doc = Jsoup.connect(url)
                    .userAgent("ReportAI/1.0 (Intelligent Report Writing Platform)")
                    .timeout(15000)
                    .followRedirects(true)
                    .maxBodySize(2 * 1024 * 1024)
                    .get();

            doc.select("script, style, nav, footer, header, aside, .ad, .advertisement, .sidebar").remove();

            String title = doc.title() != null ? doc.title().trim() : "";
            String content = doc.body() != null ? doc.body().text().trim() : "";

            StringBuilder sb = new StringBuilder();
            if (!title.isBlank()) {
                sb.append("# ").append(title).append("\n\n");
            }
            sb.append(content);

            if (sb.length() > 50000) {
                sb.setLength(50000);
                sb.append("\n\n[内容已截断，原文超过 50000 字符]");
            }

            return sb.toString();
        } catch (Exception e) {
            log.error("Fetch URL failed: {} - {}", url, e.getMessage());
            return null;
        }
    }
}
