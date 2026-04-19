package com.reportai.hub.knowledge.controller;

import com.reportai.hub.common.Result;
import com.reportai.hub.knowledge.dto.RagSearchQuery;
import com.reportai.hub.knowledge.dto.RagSearchResponse;
import com.reportai.hub.knowledge.service.RagSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "知识检索（RAG）")
@RestController
@RequestMapping("/api/v1/knowledge")
@RequiredArgsConstructor
@Validated
public class RagSearchController {

    private final RagSearchService ragSearchService;

    @Operation(summary = "RAG 检索：返回片段 + 文件名 + 块号 + 相关度（兼容旧接口）")
    @GetMapping("/search")
    public Result<RagSearchResponse> search(
            @NotNull @RequestParam Long kbId,
            @NotBlank @RequestParam String q,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int topK,
            @RequestParam(required = false) String includeKeywords,
            @RequestParam(required = false) String excludeKeywords) {
        return Result.success(ragSearchService.search(kbId, q, topK, includeKeywords, excludeKeywords));
    }

    @Operation(summary = "T5 增强检索：多 KB + 排除引用 + 段落定位 + 高亮区间")
    @PostMapping("/search/enhanced")
    public Result<RagSearchResponse> enhancedSearch(@Valid @RequestBody RagSearchQuery body) {
        return Result.success(ragSearchService.search(body));
    }

    @Operation(summary = "排除某 chunk 不再用于该报告")
    @PostMapping("/exclude")
    public Result<Void> excludeChunk(@RequestParam Long reportId, @RequestParam Long chunkId) {
        ragSearchService.excludeChunk(reportId, chunkId);
        return Result.success();
    }

    @Operation(summary = "取消排除")
    @DeleteMapping("/exclude")
    public Result<Void> includeChunk(@RequestParam Long reportId, @RequestParam Long chunkId) {
        ragSearchService.includeChunk(reportId, chunkId);
        return Result.success();
    }
}
