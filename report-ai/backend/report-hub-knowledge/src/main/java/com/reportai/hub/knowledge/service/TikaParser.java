package com.reportai.hub.knowledge.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;

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
}
