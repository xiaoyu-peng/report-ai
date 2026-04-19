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
            // 分页抽取：PDF 每页一个元素；其他格式单元素即全文。下游 chunker 依此计算 page_start/page_end。
            List<String> pages = tikaParser.extractPages(in, filename);
            finalizeDocument(doc, pages);
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
            // 正文变了就重建 chunk：先清旧，再按新内容走一遍 chunker。
            // 编辑路径进来的是纯文本（无页码概念），所以 pages 只放一段。
            chunkMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<KnowledgeChunk>()
                    .eq("doc_id", docId));
            doc.setContent(truncateForDb(content));
            doc.setFileSize((long) content.getBytes(StandardCharsets.UTF_8).length);
            List<com.reportai.hub.knowledge.service.TextChunker.PageAwareChunk> pieces =
                    textChunker.chunkByPage(List.of(content));
            for (int i = 0; i < pieces.size(); i++) {
                var pc = pieces.get(i);
                KnowledgeChunk c = new KnowledgeChunk();
                c.setDocId(docId);
                c.setKbId(doc.getKbId());
                c.setChunkIndex(i);
                c.setContent(pc.text());
                c.setParagraphIndex(pc.paragraphIndex());
                chunkMapper.insert(c);
            }
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

        // 重新分块用 knowledge_document.content（已是 finalize 时存好的全文）。
        // 注意：失去原始页边界，所以页码统一按"单页"处理；段落序号能完整重建。
        String content = doc.getContent() == null ? "" : doc.getContent();
        List<com.reportai.hub.knowledge.service.TextChunker.PageAwareChunk> pieces =
                textChunker.chunkByPage(List.of(content));
        for (int i = 0; i < pieces.size(); i++) {
            var pc = pieces.get(i);
            KnowledgeChunk c = new KnowledgeChunk();
            c.setDocId(docId);
            c.setKbId(doc.getKbId());
            c.setChunkIndex(i);
            c.setContent(pc.text());
            c.setParagraphIndex(pc.paragraphIndex());
            // reembed 没有原始页号信息，PDF 也保留原 page_start 不准确，所以 null
            chunkMapper.insert(c);
        }

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
        // knowledge_document.content 存全文（页间用 \n\n 分），便于后台搜查/预览
        String joined = String.join("\n\n", pages);
        doc.setContent(truncateForDb(joined));

        // 页码感知切块：PDF 得到 pageStart/pageEnd=实际页号；其他格式 pageStart=pageEnd=1
        List<com.reportai.hub.knowledge.service.TextChunker.PageAwareChunk> pieces =
                textChunker.chunkByPage(pages);
        List<KnowledgeChunk> chunks = new ArrayList<>(pieces.size());
        for (int i = 0; i < pieces.size(); i++) {
            var pc = pieces.get(i);
            KnowledgeChunk c = new KnowledgeChunk();
            c.setDocId(doc.getId());
            c.setKbId(doc.getKbId());
            c.setChunkIndex(i);
            c.setContent(pc.text());
            // 只有一页（text/html、txt、md）时页码不具备展示价值，留 null 让前端不展示"第 1 页"。
            if (pages.size() > 1) {
                c.setPageStart(pc.pageStart());
                c.setPageEnd(pc.pageEnd());
            }
            c.setParagraphIndex(pc.paragraphIndex());
            chunks.add(c);
        }
        for (KnowledgeChunk c : chunks) {
            chunkMapper.insert(c);
        }

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
