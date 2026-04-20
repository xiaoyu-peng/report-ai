package com.reportai.hub.report.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reportai.hub.common.exception.BusinessException;
import com.reportai.hub.knowledge.dto.RagChunkHit;
import com.reportai.hub.knowledge.mcp.SassMcpService;
import com.reportai.hub.knowledge.mcp.SearchMcpService;
import com.reportai.hub.knowledge.mcp.TavilyClient;
import com.reportai.hub.knowledge.service.RagSearchService;
import com.reportai.hub.report.dto.ReportCreateDTO;
import com.reportai.hub.report.entity.Report;
import com.reportai.hub.report.entity.ReportTemplate;
import com.reportai.hub.report.entity.ReportVersion;
import com.reportai.hub.common.llm.LlmClient;
import com.reportai.hub.report.mapper.ReportMapper;
import com.reportai.hub.report.mapper.ReportTemplateMapper;
import com.reportai.hub.report.mapper.ReportVersionMapper;
import com.reportai.hub.report.prompt.Prompts;
import com.reportai.hub.report.service.CitationParser;
import com.reportai.hub.report.service.KeywordExtractorService;
import com.reportai.hub.report.service.QualityMetricsService;
import com.reportai.hub.report.service.ReportGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportGenerationServiceImpl implements ReportGenerationService {

    /** 单报告检索拉取多少 chunk；评分"检索准确性"要求溯源清晰，不要贪多。 */
    private static final int RAG_TOP_K = 8;

    /** MCP 聚合阶段总预算。演示时评委最容不得"SSE 开始前干等"。 */
    private static final int MCP_BUDGET_SECONDS = 20;

    /**
     * MCP 聚合结果：拼好的 prompt 文本 + 成功拉到的小节标签（用于版本 change_summary 的"变更背景"卡）。
     * 赛题 4.7 / 差异化亮点：diff 顶部展示"此版改写用到了哪些实时数据"。
     */
    public record McpResult(String text, java.util.List<String> sections) {
        static McpResult empty() { return new McpResult(null, java.util.List.of()); }
    }

    private final ReportMapper reportMapper;
    private final ReportTemplateMapper templateMapper;
    private final ReportVersionMapper versionMapper;
    private final RagSearchService ragSearchService;
    private final LlmClient llmClient;
    private final SassMcpService sassMcpService;
    private final SearchMcpService searchMcpService;
    private final TavilyClient tavilyClient;
    private final CitationParser citationParser;
    private final QualityMetricsService qualityMetricsService;
    private final KeywordExtractorService keywordExtractorService;
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Report createDraft(ReportCreateDTO dto, Long operatorId) {
        Report r = new Report();
        r.setTitle(dto.getTitle());
        r.setTopic(dto.getTopic());
        r.setKbId(dto.getKbId());
        r.setTemplateId(dto.getTemplateId());
        r.setIncludeKeywords(dto.getIncludeKeywords());
        r.setExcludeKeywords(dto.getExcludeKeywords());
        r.setGenerationDepth(dto.getGenerationDepth() == null ? "standard" : dto.getGenerationDepth());
        r.setStatus("draft");
        r.setWordCount(0);
        r.setCreatedBy(operatorId);
        try {
            r.setKeyPoints(mapper.writeValueAsString(
                    dto.getKeyPoints() == null ? List.of() : dto.getKeyPoints()));
        } catch (Exception e) {
            r.setKeyPoints("[]");
        }
        reportMapper.insert(r);
        return r;
    }

    @Override
    public void streamGenerate(Long reportId, Long operatorId,
                               Consumer<List<RagChunkHit>> onChunks,
                               Consumer<DataSources> onSources,
                               Consumer<String> onToken, Runnable onDone) {
        Report report = reportMapper.selectById(reportId);
        if (report == null) throw new BusinessException("报告不存在: " + reportId);

        // 1. 风格指南
        String styleJson = "{}";
        if (report.getTemplateId() != null) {
            ReportTemplate tpl = templateMapper.selectById(report.getTemplateId());
            if (tpl != null && tpl.getStructureJson() != null) {
                styleJson = tpl.getStructureJson();
            }
        }

        // 2. RAG 检索：用 topic + keyPoints 拼查询；include/exclude（赛题 2.3）由用户提交
        // 生成深度控制 topK：brief=4 / standard=8 / deep=16。
        int topK = topKForDepth(report.getGenerationDepth());
        String query = buildQuery(report);
        List<RagChunkHit> hits = Collections.emptyList();
        if (report.getKbId() != null) {
            hits = ragSearchService.searchRaw(
                    report.getKbId(), query, topK,
                    report.getIncludeKeywords(), report.getExcludeKeywords());
        }
        // 2.1 先把命中的 chunk 清单推给前端（SSE chunks 事件），供"引用溯源"展示。
        //     失败不阻塞主流程 —— 溯源只是增强，生成本身更重要。
        if (onChunks != null) {
            try {
                onChunks.accept(hits);
            } catch (RuntimeException ex) {
                log.warn("push chunks event failed, will continue generation: {}", ex.getMessage());
            }
        }
        String ragContext = renderRagContext(hits);

        // 2.5 MCP 实时数据注入（传播分析/政策影响/行业分析类模板自动拉取舆情数据）
        McpResult mcpResult = buildMcpContext(report);
        String fullContext = ragContext;
        if (mcpResult.text() != null && !mcpResult.text().isBlank()) {
            fullContext = ragContext + "\n\n--- 以下是来自晴天舆情 MCP 的实时数据 ---\n" + mcpResult.text();
        }

        // 2.6 数据源汇总推给前端 sources 事件：评委/用户能直接看到 "本次用了多少 KB/MCP/Web"
        if (onSources != null) {
            try {
                List<String> mcpSections = mcpResult.sections() == null
                        ? java.util.List.of() : mcpResult.sections();
                int tavilyHits = (int) mcpSections.stream()
                        .filter(s -> s != null && s.toLowerCase().contains("tavily")).count();
                // Tavily 以 section 名称记录（buildMcpContext 里加的是 "Tavily Web 搜索"）；
                // RAG hits 直接用列表大小；mcpSections 对 UI 而言就是"用了哪些工具"的标签。
                onSources.accept(DataSources.of(
                        hits == null ? 0 : hits.size(),
                        report.getKbId(),
                        mcpSections,
                        tavilyHits));
            } catch (RuntimeException ex) {
                log.warn("push sources event failed: {}", ex.getMessage());
            }
        }

        // 3. 组 prompt（深度三档把目标字数提示拼到 user 末尾）
        String keyPointsBullets = renderKeyPoints(report);
        String user = Prompts.buildGenerationUser(
                report.getTopic(), keyPointsBullets, styleJson, fullContext);
        String depthHint = depthHintForPrompt(report.getGenerationDepth());
        if (!depthHint.isEmpty()) user = user + "\n\n" + depthHint;

        // 4. 流式调用
        StringBuilder full = new StringBuilder();
        report.setStatus("generating");
        reportMapper.updateById(report);

        try {
            llmClient.stream(Prompts.GENERATION_SYSTEM, user, token -> {
                full.append(token);
                onToken.accept(token);
            }, () -> {});
        } catch (RuntimeException e) {
            report.setStatus("draft");
            reportMapper.updateById(report);
            throw e;
        }

        // 5. 落库 + v1 版本
        String body = full.toString();
        report.setContent(body);
        report.setWordCount(body.length());
        report.setStatus("ready");
        reportMapper.updateById(report);

        ReportVersion v = new ReportVersion();
        v.setReportId(reportId);
        v.setVersionNum(1);
        v.setTitle(report.getTitle());
        v.setContent(body);
        v.setSourceMode("initial");
        v.setWordCount(body.length());
        v.setCreatedBy(operatorId);
        v.setChangeSummary(buildInitialChangeSummary(report, topK, hits, mcpResult, body.length()));
        versionMapper.insert(v);

        // T5：解析正文里所有 [n] → 写入 report_citation。前端 popover 直接读这张表。
        try {
            int n = citationParser.parseAndPersist(body, hits, reportId, v.getId(), 0);
            log.info("citations persisted for report {}: {}", reportId, n);
        } catch (RuntimeException ex) {
            log.warn("citation persist failed for report {}: {}", reportId, ex.getMessage());
        }

        // T5：异步触发质量体检（覆盖率 + KB 分布 + 事实性可疑），不阻塞 SSE
        CompletableFuture.runAsync(() -> {
            try {
                qualityMetricsService.runCheck(reportId);
            } catch (Exception ex) {
                log.warn("quality metrics async run failed for report {}: {}", reportId, ex.getMessage());
            }
        });

        log.info("report {} generated: {} chars, provider={}",
                reportId, body.length(), llmClient.providerName());

        onDone.run();
    }

    // ---------------- helpers ----------------

    /**
     * 组装 v1 的 change_summary —— "变更背景"卡展示给评委看"此版本从哪来"。
     * 典型："首次生成 · RAG 命中 7/8 条 · MCP: 舆情概览, 热门文章 · 深度 standard · 2341 字"
     */
    private String buildInitialChangeSummary(Report report, int topK,
                                             List<RagChunkHit> hits, McpResult mcp, int wordCount) {
        StringBuilder s = new StringBuilder("首次生成");
        if (hits != null && !hits.isEmpty()) {
            s.append(" · RAG 命中 ").append(hits.size()).append("/").append(topK).append(" 条");
        } else if (report.getKbId() != null) {
            s.append(" · RAG 未命中");
        }
        if (mcp != null && !mcp.sections().isEmpty()) {
            s.append(" · MCP: ").append(String.join(", ", mcp.sections()));
        }
        String depth = report.getGenerationDepth();
        if (depth != null && !"standard".equals(depth)) {
            s.append(" · 深度 ").append(depth);
        }
        s.append(" · ").append(wordCount).append(" 字");
        return s.toString();
    }

    /** 生成深度 → RAG topK。null/未识别走 standard（8）。 */
    private int topKForDepth(String depth) {
        if (depth == null) return 8;
        return switch (depth) {
            case "brief" -> 4;
            case "deep" -> 16;
            default -> 8;
        };
    }

    /** 生成深度 → 追加到 user prompt 末尾的字数提示，让 LLM 控制篇幅。 */
    private String depthHintForPrompt(String depth) {
        if (depth == null) return "";
        return switch (depth) {
            case "brief" -> "【篇幅要求】 控制在 800 字内，言简意赅，只讲核心结论与关键数据；章节不超过 3 节。";
            case "deep" -> "【篇幅要求】 目标 3500-4500 字。每节要有背景 + 数据 + 分析 + 案例 + 启示；引用密度要高。";
            default -> ""; // standard = 默认
        };
    }

    private String buildQuery(Report report) {
        StringBuilder sb = new StringBuilder();
        sb.append(report.getTopic() == null ? "" : report.getTopic());
        String kp = report.getKeyPoints();
        if (kp != null && !kp.isBlank()) {
            try {
                List<String> list = mapper.readValue(kp, new TypeReference<List<String>>() {});
                list.forEach(s -> sb.append(' ').append(s));
            } catch (Exception ignore) {
                sb.append(' ').append(kp);
            }
        }
        return sb.toString();
    }

    private String renderKeyPoints(Report report) {
        String kp = report.getKeyPoints();
        if (kp == null || kp.isBlank()) return "（无）";
        try {
            List<String> list = mapper.readValue(kp, new TypeReference<List<String>>() {});
            if (list.isEmpty()) return "（无）";
            return list.stream().map(s -> "- " + s).collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "- " + kp;
        }
    }

    private String renderRagContext(List<RagChunkHit> hits) {
        if (hits == null || hits.isEmpty()) return "（无）";
        return IntStream.range(0, hits.size())
                .mapToObj(i -> {
                    RagChunkHit h = hits.get(i);
                    return "[%d: %s / 第 %d 段]\n%s".formatted(
                            i + 1,
                            h.getFilename() == null ? "" : h.getFilename(),
                            h.getChunkIndex() == null ? 0 : h.getChunkIndex(),
                            h.getContent() == null ? "" : h.getContent());
                })
                .collect(Collectors.joining("\n\n"));
    }

    /** 外层：总预算 {@value #MCP_BUDGET_SECONDS}s，超时/异常一律降级为 empty，不阻塞 SSE。 */
    private McpResult buildMcpContext(Report report) {
        McpResult r = withBudget(() -> buildMcpContextInner(report), MCP_BUDGET_SECONDS, "generation-mcp");
        return r == null ? McpResult.empty() : r;
    }

    private McpResult buildMcpContextInner(Report report) {
        String topic = report.getTopic();
        if (topic == null || topic.isBlank()) return McpResult.empty();

        String title = report.getTitle();
        List<String> keywords = keywordExtractorService.extractKeywordsForSearch(title, topic);
        log.info("Auto-extracted keywords for report '{}': {}", title, keywords);

        String route = classifyRoute(getTemplateName(report), topic);

        String endDate = LocalDate.now().toString();
        String startDate = LocalDate.now().minusDays(30).toString();

        StringBuilder mcpData = new StringBuilder();
        java.util.List<String> used = new java.util.ArrayList<>();

        try {
            switch (route) {
                case "propagation" -> {
                    appendMcpResult(mcpData, used, "舆情概览", sassMcpService.overview(topic, startDate, endDate));
                    appendMcpResult(mcpData, used, "热门文章", sassMcpService.hotArticle(topic, startDate, endDate, 5));
                    appendMcpResult(mcpData, used, "情感分布", sassMcpService.emotionalDistribution(topic, startDate, endDate));
                    appendMcpResult(mcpData, used, "渠道声量", sassMcpService.datasourceSound(topic, startDate, endDate));
                    appendMcpResult(mcpData, used, "事件阶段演化", sassMcpService.stageEnvolution(topic, startDate, endDate));
                    appendMcpResult(mcpData, used, "事件概述", sassMcpService.generateEventTopicInfo(topic, startDate, endDate));
                    
                    for (String kw : keywords) {
                        if (!kw.equals(topic) && used.size() < 12) {
                            appendTavilyResult(mcpData, used, kw, 3);
                        }
                    }
                }
                case "policy", "industry" -> {
                    appendMcpResult(mcpData, used, "相关文章搜索", searchMcpService.searchArticles(topic, 1, 10));
                    appendMcpResult(mcpData, used, "热门词云", sassMcpService.hotWords(topic, startDate, endDate));
                    appendTavilyResult(mcpData, used, topic, 5);
                    
                    for (String kw : keywords) {
                        if (!kw.equals(topic) && used.size() < 10) {
                            appendMcpResult(mcpData, used, "相关搜索-" + kw, searchMcpService.searchArticles(kw, 1, 3));
                        }
                    }
                }
                case "tech" -> {
                    appendTavilyResult(mcpData, used, topic, 8);
                    appendMcpResult(mcpData, used, "相关文章搜索", searchMcpService.searchArticles(topic, 1, 5));
                    
                    for (String kw : keywords) {
                        if (!kw.equals(topic) && used.size() < 10) {
                            appendTavilyResult(mcpData, used, kw, 3);
                        }
                    }
                }
                default -> {
                    appendMcpResult(mcpData, used, "相关文章搜索", searchMcpService.searchArticles(topic, 1, 5));
                    appendTavilyResult(mcpData, used, topic, 5);
                    
                    for (String kw : keywords) {
                        if (!kw.equals(topic) && used.size() < 8) {
                            appendTavilyResult(mcpData, used, kw, 2);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("MCP data fetch failed for topic '{}' (route={}): {}", topic, route, e.getMessage());
        }

        String text = mcpData.length() > 0 ? mcpData.toString() : null;
        return new McpResult(text, used);
    }

    /**
     * 路由决策：模板名优先（用户显式选的意图最强），否则按 topic 关键词匹配，再不行走通用兜底。
     * 判断用包含匹配（模板名/主题都是短串），简单够用。
     */
    private String classifyRoute(String templateName, String topic) {
        String s = ((templateName == null ? "" : templateName) + " " + topic).toLowerCase();
        if (containsAny(s, "传播", "舆情", "品牌", "声量", "public opinion"))         return "propagation";
        if (containsAny(s, "政策", "法规", "条例", "办法", "通知", "policy"))             return "policy";
        if (containsAny(s, "行业", "产业", "市场", "赛道", "market", "industry"))        return "industry";
        if (containsAny(s, "科技", "技术", "ai", "模型", "算法", "tech", "technology")) return "tech";
        return "general";
    }

    private static boolean containsAny(String s, String... needles) {
        for (String n : needles) if (s.contains(n)) return true;
        return false;
    }

    /** Tavily 搜索结果拼成 prompt 文本。只取 title/url/content 前 200 字，防止撑爆 context。 */
    private void appendTavilyResult(StringBuilder mcpData, java.util.List<String> used, String topic, int maxResults) {
        if (!tavilyClient.isConfigured()) {
            log.debug("Tavily API key not configured, skipping Tavily search for: {}", topic);
            return;
        }
        try {
            JsonNode r = tavilyClient.search(topic, maxResults);
            if (r == null || r.isNull()) return;
            StringBuilder block = new StringBuilder();
            JsonNode results = r.path("results");
            if (!results.isArray() || results.isEmpty()) return;
            block.append("Tavily Web 搜索结果：\n");
            for (int i = 0; i < results.size() && i < maxResults; i++) {
                JsonNode it = results.get(i);
                String title = it.path("title").asText("");
                String url   = it.path("url").asText("");
                String snip  = it.path("content").asText("");
                if (snip.length() > 200) snip = snip.substring(0, 200) + "…";
                block.append("- [").append(title).append("](").append(url).append(")\n  ")
                     .append(snip).append("\n");
            }
            String ans = r.path("answer").asText("");
            if (!ans.isBlank()) block.append("\nTavily 概览：").append(ans).append("\n");
            mcpData.append("\n### Tavily Web 搜索\n").append(block).append("\n");
            used.add("Tavily Web 搜索");
        } catch (Exception e) {
            log.warn("Tavily enrichment skipped: {}", e.getMessage());
        }
    }

    /**
     * 总预算熔断：给任意 Supplier 一个硬性时间上限。超时 / 异常都降级为 null。
     * 用 CompletableFuture.supplyAsync 在 ForkJoin 公共池里跑，不抢 Spring MVC 线程。
     */
    static <T> T withBudget(Supplier<T> supplier, int budgetSeconds, String label) {
        CompletableFuture<T> f = CompletableFuture.supplyAsync(supplier);
        try {
            return f.get(budgetSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            org.slf4j.LoggerFactory.getLogger(ReportGenerationServiceImpl.class)
                    .warn("{} budget {}s exceeded, fallback to null", label, budgetSeconds);
            f.cancel(true);
            return null;
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(ReportGenerationServiceImpl.class)
                    .warn("{} failed: {}", label, e.getMessage());
            return null;
        }
    }

    private String getTemplateName(Report report) {
        if (report.getTemplateId() == null) return null;
        try {
            ReportTemplate tpl = templateMapper.selectById(report.getTemplateId());
            return tpl != null ? tpl.getName() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void appendMcpResult(StringBuilder sb, java.util.List<String> used,
                                 String label, JsonNode data) {
        if (data == null) return;
        used.add(label); // 记录成功拉到的小节，供版本 change_summary 展示
        sb.append("\n### ").append(label).append("\n");
        try {
            sb.append(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(data));
        } catch (Exception e) {
            sb.append(data.toString());
        }
        sb.append("\n");
    }
}
