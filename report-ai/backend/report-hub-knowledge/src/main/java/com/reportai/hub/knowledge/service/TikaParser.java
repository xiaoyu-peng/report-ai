package com.reportai.hub.knowledge.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.ToXMLContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 对 Apache Tika 的薄封装：识别 PDF / Word / TXT / Markdown / HTML 等并抽正文。 */
@Slf4j
@Component
public class TikaParser {

    /** 单文档大小上限：50 MB（防止 OOM）。 */
    private static final int MAX_CHARS = 20 * 1024 * 1024;

    private final Tika tika = new Tika();

    /** 返回抽取到的正文；Tika 自动识别格式。 */
    public String extractText(InputStream in, String filename) {
        try {
            AutoDetectParser parser = new AutoDetectParser();
            BodyContentHandler handler = new BodyContentHandler(MAX_CHARS);
            Metadata metadata = new Metadata();
            if (filename != null) {
                metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
            }
            parser.parse(in, handler, metadata);
            return handler.toString();
        } catch (IOException | TikaException | SAXException e) {
            log.error("Tika parse failed for {}: {}", filename, e.getMessage());
            throw new RuntimeException("文档解析失败：" + e.getMessage(), e);
        }
    }

    /** 从内容推测 MIME type；优先用 filename 后缀。 */
    public String detectType(InputStream in, String filename) {
        try {
            return tika.detect(in, filename);
        } catch (IOException e) {
            return "application/octet-stream";
        }
    }

    /**
     * 分页抽取。PDF 走 Tika 的 XHTML 输出（PDF parser 发出 &lt;div class="page"&gt;）并按页拆分；
     * 其他格式没有"页"的概念，返回单元素列表（把全文当一页）。
     *
     * <p>返回 List&lt;String&gt; 的 index 对应 1-based 页号：index 0 = 第 1 页。
     */
    public List<String> extractPages(InputStream in, String filename) {
        try {
            AutoDetectParser parser = new AutoDetectParser();
            ToXMLContentHandler handler = new ToXMLContentHandler();
            Metadata metadata = new Metadata();
            if (filename != null) {
                metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
            }
            parser.parse(in, handler, metadata);
            String xhtml = handler.toString();
            List<String> pages = splitXhtmlByPage(xhtml);
            if (pages.isEmpty()) {
                // 不分页的格式（txt / md / docx）：返回单元素
                String fullText = stripTags(xhtml);
                if (fullText.isBlank()) return Collections.emptyList();
                return List.of(fullText);
            }
            return pages;
        } catch (IOException | TikaException | SAXException e) {
            log.error("Tika page-extract failed for {}: {}", filename, e.getMessage());
            throw new RuntimeException("文档解析失败：" + e.getMessage(), e);
        }
    }

    /** 没有捕获组的 <div class="page"> 匹配；缺失意味着非 PDF 源。 */
    private static final Pattern PAGE_DIV = Pattern.compile(
            "<div\\s+class=\"page\"[^>]*>(.*?)</div>",
            Pattern.DOTALL);

    /** 简陋但足够的 tag 剥离；XHTML 输出里没有 script/style 污染。 */
    private static final Pattern TAG = Pattern.compile("<[^>]+>");

    /** 把 Tika 的 XHTML 按 &lt;div class="page"&gt; 切成每页正文。无分页标记时返回空列表。 */
    private List<String> splitXhtmlByPage(String xhtml) {
        List<String> pages = new ArrayList<>();
        Matcher m = PAGE_DIV.matcher(xhtml);
        while (m.find()) {
            String pageHtml = m.group(1);
            String pageText = stripTags(pageHtml).trim();
            pages.add(pageText);
        }
        return pages;
    }

    private String stripTags(String s) {
        return TAG.matcher(s).replaceAll("")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replaceAll("\\s+\n", "\n")
                .replaceAll("\n{3,}", "\n\n");
    }
}
