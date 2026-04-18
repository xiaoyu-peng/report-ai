package com.reportai.hub.knowledge.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "外部数据接入（晴天 MCP + Tavily + Fetch）")
@RestController
@RequestMapping("/api/v1/mcp")
@RequiredArgsConstructor
public class McpController {

    private final SearchMcpService searchService;
    private final SassMcpService sassService;
    private final TavilyClient tavilyClient;
    private final FetchClient fetchClient;

    // ========== 晴天信息检索 ==========

    @Operation(summary = "搜索舆情文章（晴天 MCP）")
    @GetMapping("/search/articles")
    public ResponseEntity<JsonNode> searchArticles(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        JsonNode result = searchService.searchArticles(keyword, page, pageSize);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "获取文章详情（晴天 MCP）")
    @GetMapping("/search/article/{uuid}")
    public ResponseEntity<JsonNode> getArticleDetail(
            @PathVariable String uuid,
            @RequestParam String publishTime) {
        JsonNode result = searchService.getArticleDetail(uuid, publishTime);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "搜索媒体账号（晴天 MCP）")
    @GetMapping("/search/media-accounts")
    public ResponseEntity<JsonNode> searchMediaAccounts(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        JsonNode result = searchService.searchMediaAccounts(keyword, page, pageSize);
        return ResponseEntity.ok(result);
    }

    // ========== 晴天分析组件 ==========

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
        if (!ALLOWED_TOOLS.contains(tool)) {
            return ResponseEntity.badRequest().build();
        }
        JsonNode result = sassService.callTool(tool, params);
        return ResponseEntity.ok(result);
    }

    // ========== Tavily Web 搜索 ==========

    @Operation(summary = "Web 搜索（Tavily）——搜索政策原文/行业报告/技术文档")
    @GetMapping("/web/search")
    public ResponseEntity<?> webSearch(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int maxResults) {
        if (!tavilyClient.isConfigured()) {
            return ResponseEntity.ok(Map.of("error", "Tavily API Key 未配置", "results", java.util.List.of()));
        }
        JsonNode result = tavilyClient.search(query, maxResults);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "URL 内容提取（Tavily Extract）")
    @PostMapping("/web/extract")
    public ResponseEntity<?> webExtract(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "url 参数必填"));
        }
        if (!tavilyClient.isConfigured()) {
            return ResponseEntity.ok(Map.of("error", "Tavily API Key 未配置"));
        }
        JsonNode result = tavilyClient.extract(url);
        return ResponseEntity.ok(result);
    }

    // ========== Fetch URL 抓取 ==========

    @Operation(summary = "抓取 URL 内容（无需 API Key，直接解析网页）")
    @GetMapping("/fetch")
    public ResponseEntity<?> fetchUrl(@RequestParam String url) {
        String content = fetchClient.fetchUrl(url);
        if (content == null) {
            return ResponseEntity.ok(Map.of("error", "抓取失败", "url", url));
        }
        return ResponseEntity.ok(Map.of("url", url, "content", content, "length", content.length()));
    }

    private static final java.util.Set<String> ALLOWED_TOOLS = java.util.Set.of(
            "overview", "hot-article", "emotional-distribution", "datasourceSound",
            "stage-envolution", "generate-event-topic-info", "hot-words",
            "hot-org", "hot-person", "hot-theme", "influence-indexation",
            "content-classification", "media-active", "media-influence",
            "regional-distribution", "language-distribution", "sensitive-info"
    );
}
