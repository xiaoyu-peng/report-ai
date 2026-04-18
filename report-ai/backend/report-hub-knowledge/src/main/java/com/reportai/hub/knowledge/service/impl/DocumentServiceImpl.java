package com.reportai.hub.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reportai.hub.common.exception.BusinessException;
import com.reportai.hub.knowledge.entity.KnowledgeChunk;
import com.reportai.hub.knowledge.entity.KnowledgeDocument;
import com.reportai.hub.knowledge.mapper.KnowledgeChunkMapper;
import com.reportai.hub.knowledge.mapper.KnowledgeDocumentMapper;
import com.reportai.hub.knowledge.service.DocumentService;
import com.reportai.hub.knowledge.service.KnowledgeBaseService;
import com.reportai.hub.knowledge.service.TextChunker;
import com.reportai.hub.knowledge.service.TikaParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final TikaParser tikaParser;
    private final TextChunker textChunker;
    private final KnowledgeBaseService baseService;

    @Value("${report-ai.knowledge.url-fetch-timeout-ms:10000}")
    private int urlFetchTimeoutMs;

    @Override
    @Transactional
    public KnowledgeDocument upload(Long kbId, MultipartFile file, Long operatorId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("文件为空");
        }
        String filename = file.getOriginalFilename();
        KnowledgeDocument doc = saveDocumentRow(kbId, filename,
                guessType(filename), file.getSize(), operatorId);

        try (InputStream in = file.getInputStream()) {
            String text = tikaParser.extractText(in, filename);
            finalizeDocument(doc, text);
        } catch (IOException e) {
            doc.setStatus("failed");
            documentMapper.updateById(doc);
            throw new BusinessException("读取上传文件失败：" + e.getMessage());
        }
        return doc;
    }

    @Override
    @Transactional
    public KnowledgeDocument importFromUrl(Long kbId, String url,
                                           String filenameOverride, Long operatorId) {
        String host;
        try {
            host = URI.create(url).getHost();
        } catch (IllegalArgumentException e) {
            throw new BusinessException("URL 非法：" + url);
        }
        if (host == null) throw new BusinessException("URL 缺少 host");

        String html;
        String title;
        try {
            Document webDoc = Jsoup.connect(url)
                    .timeout(urlFetchTimeoutMs)
                    .userAgent("ReportAI/1.0 (+https://github.com/xiaoyu-peng/report-ai)")
                    .get();
            title = webDoc.title();
            html = webDoc.body() == null ? "" : webDoc.body().text();
        } catch (IOException e) {
            throw new BusinessException("抓取 URL 失败：" + e.getMessage());
        }

        String filename = filenameOverride != null && !filenameOverride.isBlank()
                ? filenameOverride
                : (title.isEmpty() ? host : title);

        KnowledgeDocument doc = saveDocumentRow(kbId, filename, "text/html",
                (long) html.getBytes(StandardCharsets.UTF_8).length, operatorId);
        finalizeDocument(doc, html);
        return doc;
    }

    @Override
    @Transactional
    public void deleteCascade(Long docId) {
        KnowledgeDocument doc = documentMapper.selectById(docId);
        if (doc == null) return;
        chunkMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<KnowledgeChunk>()
                .eq("doc_id", docId));
        documentMapper.deleteById(docId);
        baseService.refreshCounters(doc.getKbId());
    }

    // -------------------------------------------------------------------

    private KnowledgeDocument saveDocumentRow(Long kbId, String filename, String fileType,
                                              Long fileSize, Long operatorId) {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setKbId(kbId);
        doc.setFilename(filename);
        doc.setFileType(fileType);
        doc.setFileSize(fileSize);
        doc.setStatus("processing");
        doc.setChunkCount(0);
        doc.setCreatedBy(operatorId);
        documentMapper.insert(doc);
        return doc;
    }

    private void finalizeDocument(KnowledgeDocument doc, String text) {
        if (text == null) text = "";
        doc.setContent(truncateForDb(text));

        List<String> pieces = textChunker.chunk(text);
        List<KnowledgeChunk> chunks = new ArrayList<>(pieces.size());
        for (int i = 0; i < pieces.size(); i++) {
            KnowledgeChunk c = new KnowledgeChunk();
            c.setDocId(doc.getId());
            c.setKbId(doc.getKbId());
            c.setChunkIndex(i);
            c.setContent(pieces.get(i));
            chunks.add(c);
        }
        for (KnowledgeChunk c : chunks) {
            chunkMapper.insert(c);
        }

        doc.setChunkCount(chunks.size());
        doc.setStatus("success");
        documentMapper.updateById(doc);
        baseService.refreshCounters(doc.getKbId());

        log.info("indexed doc {} ({} bytes) -> {} chunks", doc.getFilename(),
                doc.getFileSize(), chunks.size());
    }

    /** knowledge_document.content 是 longtext，但为保险仍截到 8MB 以内。 */
    private String truncateForDb(String s) {
        final int max = 8 * 1024 * 1024;
        return s.length() > max ? s.substring(0, max) : s;
    }

    private String guessType(String filename) {
        if (filename == null) return "application/octet-stream";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".docx"))
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".doc")) return "application/msword";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) return "text/markdown";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html";
        return "application/octet-stream";
    }
}
