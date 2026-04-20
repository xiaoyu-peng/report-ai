package com.reportai.hub.knowledge.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reportai.hub.common.PageResult;
import com.reportai.hub.common.Result;
import com.reportai.hub.common.context.UserContext;
import com.reportai.hub.common.exception.BusinessException;
import com.reportai.hub.knowledge.dto.UrlImportDTO;
import com.reportai.hub.knowledge.entity.KnowledgeDocument;
import com.reportai.hub.knowledge.mapper.KnowledgeDocumentMapper;
import com.reportai.hub.knowledge.service.DocumentPreviewService;
import com.reportai.hub.knowledge.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Tag(name = "知识文档")
@RestController
@RequestMapping("/api/v1/knowledge")
@RequiredArgsConstructor
@Validated
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentPreviewService previewService;
    private final KnowledgeDocumentMapper documentMapper;

    @Operation(summary = "上传文档（multipart）")
    @PostMapping("/bases/{kbId}/documents")
    public Result<KnowledgeDocument> upload(@PathVariable Long kbId,
                                            @RequestParam("file") MultipartFile file) {
        return Result.success(documentService.upload(kbId, file, UserContext.getUserId()));
    }

    @Operation(summary = "从 URL 抓取入库")
    @PostMapping("/bases/{kbId}/url")
    public Result<KnowledgeDocument> importUrl(@PathVariable Long kbId,
                                               @Valid @RequestBody UrlImportDTO dto) {
        return Result.success(documentService.importFromUrl(
                kbId, dto.getUrl(), dto.getFilename(), UserContext.getUserId()));
    }

    @Operation(summary = "分页列出某知识库下的文档")
    @GetMapping("/bases/{kbId}/documents")
    public Result<PageResult<KnowledgeDocument>> list(@PathVariable Long kbId,
            @RequestParam(defaultValue = "1") @Min(1) Long current,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Long size) {
        Page<KnowledgeDocument> result = documentMapper.selectPage(
                new Page<>(current, size),
                new LambdaQueryWrapper<KnowledgeDocument>()
                        .eq(KnowledgeDocument::getKbId, kbId)
                        .orderByDesc(KnowledgeDocument::getCreatedAt));
        return Result.success(PageResult.of(
                result.getRecords(), result.getTotal(),
                result.getSize(), result.getCurrent()));
    }

    @Operation(summary = "文档详情（含全文）")
    @GetMapping("/documents/{id}")
    public Result<KnowledgeDocument> detail(@PathVariable Long id) {
        return Result.success(documentMapper.selectById(id));
    }

    @Operation(summary = "更新文档：rename + 可选替换正文（替换会重建 chunk）")
    @PutMapping("/documents/{id}")
    public Result<KnowledgeDocument> update(@PathVariable Long id,
                                            @RequestBody DocumentUpdateDTO dto) {
        return Result.success(documentService.update(id, dto.getFilename(),
                dto.getContent(), UserContext.getUserId()));
    }

    @Operation(summary = "删除文档并级联清 chunk")
    @DeleteMapping("/documents/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        documentService.deleteCascade(id);
        return Result.success();
    }

    @Operation(summary = "重新分块（schema 升级补 paragraph_index 等新字段后用）")
    @PostMapping("/documents/{id}/reembed")
    public Result<KnowledgeDocument> reembed(@PathVariable Long id) {
        return Result.success(documentService.reembed(id));
    }

    /**
     * 原文件二进制下载 / 预览。PDF 浏览器原生渲染到 iframe；DOCX 由前端配 `/html` 端点转 HTML 预览。
     * 必须单独查 file_blob，因为实体默认 select=false 省流量。
     */
    @Operation(summary = "下载/预览原始文件（PDF 可直接 iframe）")
    @GetMapping("/documents/{id}/file")
    public ResponseEntity<byte[]> file(@PathVariable Long id,
                                        @RequestParam(value = "download", defaultValue = "false") boolean download) {
        KnowledgeDocument meta = documentMapper.selectById(id);
        if (meta == null) throw new BusinessException("文档不存在: " + id);
        KnowledgeDocument blobRow = documentMapper.selectOne(
                Wrappers.<KnowledgeDocument>lambdaQuery()
                        .select(KnowledgeDocument::getId, KnowledgeDocument::getFileBlob)
                        .eq(KnowledgeDocument::getId, id));
        byte[] bytes = blobRow == null ? null : blobRow.getFileBlob();
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException("原文件不可用（未保留或超过 10MB）");
        }
        String contentType = meta.getFileType();
        if (contentType == null || contentType.isBlank()) contentType = "application/octet-stream";
        String encodedName = URLEncoder.encode(
                meta.getFilename() == null ? ("doc-" + id) : meta.getFilename(),
                StandardCharsets.UTF_8).replace("+", "%20");
        String disposition = (download ? "attachment" : "inline")
                + "; filename=\"" + encodedName + "\"; filename*=UTF-8''" + encodedName;

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=60")
                // PDF 在 iframe 里需要允许同源嵌入；Spring Security 默认 X-Frame-Options: DENY
                .header("X-Frame-Options", "SAMEORIGIN")
                .body(bytes);
    }

    /** DOCX → HTML 内联预览。PDF 也调这个端点会直接 302 到 `/file`，让前端一套逻辑处理。 */
    @Operation(summary = "DOCX → HTML 内联预览")
    @GetMapping(value = "/documents/{id}/html", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> html(@PathVariable Long id) {
        KnowledgeDocument meta = documentMapper.selectById(id);
        if (meta == null) throw new BusinessException("文档不存在: " + id);
        String html = previewService.renderHtml(id, meta);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/html;charset=UTF-8")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=60")
                .body(html);
    }

    @lombok.Data
    public static class DocumentUpdateDTO {
        /** 可选，非空则 rename。 */
        private String filename;
        /** 可选，非 null 则替换正文并重新分块。 */
        private String content;
    }
}
