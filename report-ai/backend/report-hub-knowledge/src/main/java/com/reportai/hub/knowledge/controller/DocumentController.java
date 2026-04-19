package com.reportai.hub.knowledge.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reportai.hub.common.PageResult;
import com.reportai.hub.common.Result;
import com.reportai.hub.common.context.UserContext;
import com.reportai.hub.knowledge.dto.UrlImportDTO;
import com.reportai.hub.knowledge.entity.KnowledgeDocument;
import com.reportai.hub.knowledge.mapper.KnowledgeDocumentMapper;
import com.reportai.hub.knowledge.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "知识文档")
@RestController
@RequestMapping("/api/v1/knowledge")
@RequiredArgsConstructor
@Validated
public class DocumentController {

    private final DocumentService documentService;
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

    @lombok.Data
    public static class DocumentUpdateDTO {
        /** 可选，非空则 rename。 */
        private String filename;
        /** 可选，非 null 则替换正文并重新分块。 */
        private String content;
    }
}
