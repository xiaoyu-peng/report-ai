package com.reportai.hub.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reportai.hub.common.exception.BusinessException;
import com.reportai.hub.knowledge.dto.EsChunkDocument;
import com.reportai.hub.knowledge.entity.KnowledgeChunk;
import com.reportai.hub.knowledge.entity.KnowledgeDocument;
import com.reportai.hub.knowledge.mapper.KnowledgeChunkMapper;
import com.reportai.hub.knowledge.mapper.KnowledgeDocumentMapper;
import com.reportai.hub.knowledge.service.DocumentService;
import com.reportai.hub.knowledge.service.EsChunkService;
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
    private final EsChunkService esChunkService;

    @Value("${report-ai.knowledge.url-fetch-timeout-ms:10000}")
    private int urlFetchTimeoutMs;

    private static final long MAX_BLOB_BYTES = 10L * 1024 * 1024;

    @Override
    @Transactional
    public KnowledgeDocument upload(Long kbId, MultipartFile file, Long operatorId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("文件为空");
        }
        String filename = file.getOriginalFilename();

        // 小于 10MB 的原文件读入内存，用于「查看」时前端直接预览 PDF / DOCX
        byte[] raw = null;
        if (file.getSize() > 0 && file.getSize() <= MAX_BLOB_BYTES) {
            try (InputStream bin = file.getInputStream()) {
                raw = bin.readAllBytes();
            } catch (IOException e) {
                throw new BusinessException("读取上传文件失败：" + e.getMessage());
            }
        }

        KnowledgeDocument doc = saveDocumentRow(kbId, filename,
                guessType(filename), file.getSize(), operatorId);

        // Tika 解析用**缓存好的字节**，避免再次从 MultipartFile 读（临时文件可能已释放）
        try (InputStream in = raw != null
                ? new java.io.ByteArrayInputStream(raw)
                : file.getInputStream()) {
            // 分页抽取：PDF 每页一个元素；其他格式单元素即全文。下游 chunker 依此计算 page_start/page_end。
            List<String> pages = tikaParser.extractPages(in, filename);
            finalizeDocument(doc, pages);
        } catch (IOException e) {
            doc.setStatus("failed");
            documentMapper.updateById(doc);
            throw new BusinessException("读取上传文件失败：" + e.getMessage());
        }

        if (raw != null) {
            doc.setFileBlob(raw);
            documentMapper.updateById(doc);
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
        // URL 抓取的 HTML 没有页的概念，整体当"第 1 页"处理，page_start/page_end 自然为 1。
        finalizeDocument(doc, List.of(html));
        return doc;
    }

    @Override
    @Transactional
    public void deleteCascade(Long docId) {
        KnowledgeDocument doc = documentMapper.selectById(docId);
        if (doc == null) return;
        chunkMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<KnowledgeChunk>()
                .eq("doc_id", docId));
        esChunkService.deleteByDocId(docId);
        documentMapper.deleteById(docId);
        baseService.refreshCounters(doc.getKbId());
    }

    @Override
    @Transactional
    public KnowledgeDocument update(Long docId, String filename, String content, Long operatorId) {
        KnowledgeDocument doc = documentMapper.selectById(docId);
        if (doc == null) throw new BusinessException("文档不存在：id=" + docId);

        if (filename != null && !filename.isBlank()) {
            doc.setFilename(filename.trim());
        }

        boolean contentChanged = content != null && !content.equals(doc.getContent());
        if (contentChanged) {
            chunkMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<KnowledgeChunk>()
                    .eq("doc_id", docId));
            esChunkService.deleteByDocId(docId);
            doc.setContent(truncateForDb(content));
            doc.setFileSize((long) content.getBytes(StandardCharsets.UTF_8).length);
            List<com.reportai.hub.knowledge.service.TextChunker.PageAwareChunk> pieces =
                    textChunker.chunkByPage(List.of(content));
            List<EsChunkDocument> esDocs = new ArrayList<>();
            for (int i = 0; i < pieces.size(); i++) {
                var pc = pieces.get(i);
                KnowledgeChunk c = new KnowledgeChunk();
                c.setDocId(docId);
                c.setKbId(doc.getKbId());
                c.setChunkIndex(i);
                c.setContent(pc.text());
                c.setParagraphIndex(pc.paragraphIndex());
                chunkMapper.insert(c);
                esDocs.add(EsChunkDocument.from(c, doc.getFilename()));
            }
            esChunkService.bulkIndexChunks(esDocs);
            doc.setChunkCount(pieces.size());
            doc.setStatus("success");
        }

        documentMapper.updateById(doc);
        if (contentChanged) baseService.refreshCounters(doc.getKbId());
        log.info("doc {} updated by {} (rename={}, rewrite={})",
                docId, operatorId, filename != null, contentChanged);
        return doc;
    }

    @Override
    @Transactional
    public KnowledgeDocument reembed(Long docId) {
        KnowledgeDocument doc = documentMapper.selectById(docId);
        if (doc == null) throw new BusinessException("文档不存在：id=" + docId);

        chunkMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<KnowledgeChunk>()
                .eq("doc_id", docId));
        esChunkService.deleteByDocId(docId);

        String content = doc.getContent() == null ? "" : doc.getContent();
        List<com.reportai.hub.knowledge.service.TextChunker.PageAwareChunk> pieces =
                textChunker.chunkByPage(List.of(content));
        List<EsChunkDocument> esDocs = new ArrayList<>();
        for (int i = 0; i < pieces.size(); i++) {
            var pc = pieces.get(i);
            KnowledgeChunk c = new KnowledgeChunk();
            c.setDocId(docId);
            c.setKbId(doc.getKbId());
            c.setChunkIndex(i);
            c.setContent(pc.text());
            c.setParagraphIndex(pc.paragraphIndex());
            chunkMapper.insert(c);
            esDocs.add(EsChunkDocument.from(c, doc.getFilename()));
        }
        esChunkService.bulkIndexChunks(esDocs);

        doc.setChunkCount(pieces.size());
        doc.setStatus("success");
        documentMapper.updateById(doc);
        baseService.refreshCounters(doc.getKbId());

        log.info("reembedded doc {} -> {} chunks (with paragraph_index)", docId, pieces.size());
        return doc;
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

    private void finalizeDocument(KnowledgeDocument doc, List<String> pages) {
        if (pages == null) pages = List.of();
        String joined = String.join("\n\n", pages);
        doc.setContent(truncateForDb(joined));

        List<com.reportai.hub.knowledge.service.TextChunker.PageAwareChunk> pieces =
                textChunker.chunkByPage(pages);
        List<KnowledgeChunk> chunks = new ArrayList<>(pieces.size());
        List<EsChunkDocument> esDocs = new ArrayList<>();
        for (int i = 0; i < pieces.size(); i++) {
            var pc = pieces.get(i);
            KnowledgeChunk c = new KnowledgeChunk();
            c.setDocId(doc.getId());
            c.setKbId(doc.getKbId());
            c.setChunkIndex(i);
            c.setContent(pc.text());
            if (pages.size() > 1) {
                c.setPageStart(pc.pageStart());
                c.setPageEnd(pc.pageEnd());
            }
            c.setParagraphIndex(pc.paragraphIndex());
            chunks.add(c);
        }
        for (KnowledgeChunk c : chunks) {
            chunkMapper.insert(c);
            esDocs.add(EsChunkDocument.from(c, doc.getFilename()));
        }
        esChunkService.bulkIndexChunks(esDocs);

        doc.setChunkCount(chunks.size());
        doc.setStatus("success");
        documentMapper.updateById(doc);
        baseService.refreshCounters(doc.getKbId());

        log.info("indexed doc {} ({} bytes) -> {} chunks across {} pages",
                doc.getFilename(), doc.getFileSize(), chunks.size(), pages.size());
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
