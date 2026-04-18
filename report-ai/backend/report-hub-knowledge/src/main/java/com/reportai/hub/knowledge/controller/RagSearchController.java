package com.reportai.hub.knowledge.controller;

import com.reportai.hub.common.Result;
import com.reportai.hub.knowledge.dto.RagSearchResponse;
import com.reportai.hub.knowledge.service.RagSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "知识检索（RAG）")
@RestController
@RequestMapping("/api/v1/knowledge")
@RequiredArgsConstructor
@Validated
public class RagSearchController {

    private final RagSearchService ragSearchService;

    @Operation(summary = "RAG 检索：返回片段 + 文件名 + 块号 + 相关度")
    @GetMapping("/search")
    public Result<RagSearchResponse> search(
            @NotNull @RequestParam Long kbId,
            @NotBlank @RequestParam String q,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int topK) {
        return Result.success(ragSearchService.search(kbId, q, topK));
    }
}
