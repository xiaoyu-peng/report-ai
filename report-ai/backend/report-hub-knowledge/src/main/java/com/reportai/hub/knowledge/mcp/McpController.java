package com.reportai.hub.knowledge.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@Tag(name = "晴天 MCP 舆情数据")
@RestController
@RequestMapping("/api/v1/mcp")
@RequiredArgsConstructor
public class McpController {

    private final SearchMcpService searchService;
    private final SassMcpService sassService;

    @Operation(summary = "搜索舆情文章")
    @GetMapping("/search/articles")
    public ResponseEntity<JsonNode> searchArticles(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        JsonNode result = searchService.searchArticles(keyword, page, pageSize);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "获取文章详情")
    @GetMapping("/search/article/{articleId}")
    public ResponseEntity<JsonNode> getArticleDetail(@PathVariable String articleId) {
        JsonNode result = searchService.getArticleDetail(articleId);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "搜索媒体账号")
    @GetMapping("/search/media-accounts")
    public ResponseEntity<JsonNode> searchMediaAccounts(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        JsonNode result = searchService.searchMediaAccounts(keyword, page, pageSize);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "舆情概览（声量/渠道/情感/关键词）")
    @GetMapping("/analysis/overview")
    public ResponseEntity<JsonNode> overview(
            @RequestParam String topic,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        JsonNode result = sassService.overview(topic, startDate, endDate);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "热门文章 Top N")
    @GetMapping("/analysis/hot-article")
    public ResponseEntity<JsonNode> hotArticle(
            @RequestParam String topic,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "10") int topN) {
        JsonNode result = sassService.hotArticle(topic, startDate, endDate, topN);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "情感分布")
    @GetMapping("/analysis/emotional-distribution")
    public ResponseEntity<JsonNode> emotionalDistribution(
            @RequestParam String topic,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        JsonNode result = sassService.emotionalDistribution(topic, startDate, endDate);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "渠道声量")
    @GetMapping("/analysis/datasource-sound")
    public ResponseEntity<JsonNode> datasourceSound(
            @RequestParam String topic,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        JsonNode result = sassService.datasourceSound(topic, startDate, endDate);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "事件阶段演化")
    @GetMapping("/analysis/stage-envolution")
    public ResponseEntity<JsonNode> stageEnvolution(
            @RequestParam String topic,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        JsonNode result = sassService.stageEnvolution(topic, startDate, endDate);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "AI 生成事件概述")
    @GetMapping("/analysis/generate-event-topic-info")
    public ResponseEntity<JsonNode> generateEventTopicInfo(
            @RequestParam String topic,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        JsonNode result = sassService.generateEventTopicInfo(topic, startDate, endDate);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "热门词云")
    @GetMapping("/analysis/hot-words")
    public ResponseEntity<JsonNode> hotWords(
            @RequestParam String topic,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        JsonNode result = sassService.hotWords(topic, startDate, endDate);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "通用 MCP 工具调用")
    @PostMapping("/analysis/{tool}")
    public ResponseEntity<JsonNode> callTool(
            @PathVariable String tool,
            @RequestBody Map<String, Object> params) {
        JsonNode result = sassService.callTool(tool, params);
        return ResponseEntity.ok(result);
    }
}
