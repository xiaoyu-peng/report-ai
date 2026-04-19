package com.reportai.hub.report.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reportai.hub.common.context.UserContext;
import com.reportai.hub.knowledge.dto.RagChunkHit;
import com.reportai.hub.report.dto.RewriteMode;
import com.reportai.hub.report.dto.RewriteRequest;
import com.reportai.hub.report.service.ReportGenerationService;
import com.reportai.hub.report.service.RewriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

@Slf4j
@Tag(name = "报告生成 / 改写（SSE 流式）")
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportStreamController {

    /** 10 分钟：长报告（5000+ 字）也留足余量，避免 nginx/前端 timeout 截断。 */
    private static final long SSE_TIMEOUT_MS = 10 * 60 * 1000L;

    private final ReportGenerationService generationService;
    private final RewriteService rewriteService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Executor sseExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "report-sse-" + System.nanoTime());
        t.setDaemon(true);
        return t;
    });

    @Operation(summary = "SSE 流式生成报告正文")
    @GetMapping(value = "/{id}/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generate(@PathVariable Long id, HttpServletResponse response) {
        applySseHeaders(response);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        Long operatorId = UserContext.getUserId();
        sseExecutor.execute(() -> {
            sendSafely(emitter, "progress", "{\"step\":\"检索知识库\",\"stepIndex\":1,\"totalSteps\":5}");
            runStream(emitter, "generate",
                    (onToken, onDone) -> {
                        sendSafely(emitter, "progress", "{\"step\":\"分析风格与结构\",\"stepIndex\":2,\"totalSteps\":5}");
                        sendSafely(emitter, "progress", "{\"step\":\"获取舆情数据\",\"stepIndex\":3,\"totalSteps\":5}");
                        generationService.streamGenerate(id, operatorId,
                                hits -> {
                                    sendSafely(emitter, "progress", "{\"step\":\"AI 撰写报告\",\"stepIndex\":4,\"totalSteps\":5}");
                                    sendSafely(emitter, "chunks", toChunksJson(hits));
                                },
                                onToken,
                                () -> {
                                    sendSafely(emitter, "progress", "{\"step\":\"完稿与版本保存\",\"stepIndex\":5,\"totalSteps\":5}");
                                    onDone.run();
                                });
                    });
        });
        return emitter;
    }

    @Operation(summary = "SSE 流式改写报告（4 模式：数据更新/视角调整/内容扩展/风格转换）")
    @PostMapping(value = "/{id}/rewrite", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter rewrite(@PathVariable Long id,
                              @Valid @RequestBody RewriteRequest req,
                              HttpServletResponse response) {
        applySseHeaders(response);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        Long operatorId = UserContext.getUserId();
        RewriteMode mode = req.getMode();
        String instruction = req.getInstruction();
        sseExecutor.execute(() -> runStream(emitter, "rewrite:" + mode,
                (onToken, onDone) ->
                        rewriteService.streamRewrite(id, mode, instruction, operatorId, onToken, onDone)));
        return emitter;
    }

    @Operation(summary = "SSE 流式段落改写（对指定段落进行改写/扩写/精简）")
    @PostMapping(value = "/{id}/rewrite-section", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter rewriteSection(@PathVariable Long id,
                                     @RequestBody Map<String, String> body,
                                     HttpServletResponse response) {
        applySseHeaders(response);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        Long operatorId = UserContext.getUserId();
        String sectionContent = body.getOrDefault("content", "");
        String mode = body.getOrDefault("mode", "rewrite");
        String instruction = body.getOrDefault("instruction", "");
        sseExecutor.execute(() -> runStream(emitter, "rewrite-section:" + mode,
                (onToken, onDone) ->
                        rewriteService.streamRewriteSection(id, sectionContent, mode, instruction, operatorId, onToken, onDone)));
        return emitter;
    }

    @Operation(summary = "同步段落改写（Tiptap BubbleMenu 用，一次返回完整文本）")
    @PostMapping(value = "/{id}/rewrite/section")
    public com.reportai.hub.common.Result<String> rewriteSectionSync(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        Long operatorId = UserContext.getUserId();
        String sectionContent = body.getOrDefault("content", "");
        String mode = body.getOrDefault("mode", "rewrite");
        String instruction = body.getOrDefault("instruction", "");
        StringBuilder sb = new StringBuilder();
        rewriteService.streamRewriteSection(id, sectionContent, mode, instruction, operatorId,
                sb::append, () -> {});
        return com.reportai.hub.common.Result.success(sb.toString().trim());
    }

    /**
     * 指示 nginx/中间代理不要缓冲 SSE 流，并禁止 HTTP 缓存。
     * X-Accel-Buffering: no 对 nginx / ingress-nginx 是"强制立刻刷到客户端"的信号，
     * 是 SSE 能在代理后依然逐 token 到达的关键。
     */
    private void applySseHeaders(HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("Connection", "keep-alive");
    }

    // -----------------------------------------------------------------

    private void runStream(SseEmitter emitter, String event, StreamJob job) {
        try {
            job.run(
                    token -> sendSafely(emitter, "token", token),
                    () -> {
                        sendSafely(emitter, "done", event);
                        emitter.complete();
                    });
        } catch (RuntimeException e) {
            log.error("SSE {} failed", event, e);
            sendSafely(emitter, "error", e.getMessage());
            emitter.completeWithError(e);
        }
    }

    private void sendSafely(SseEmitter emitter, String name, String data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data == null ? "" : data));
        } catch (IOException ex) {
            // 客户端已断开，放弃后续 send；不向外抛。
            log.debug("SSE client disconnected: {}", ex.getMessage());
        }
    }

    /**
     * 把 RAG 命中的 chunk 列表序列化成 SSE chunks 事件的 JSON payload。
     * 字段与前端"引用溯源"面板约定：index（1-based，对应正文 [n]）、id、filename、
     * chunkIndex、content、score。content 适度截断，避免 SSE 单事件过大。
     */
    private String toChunksJson(List<RagChunkHit> hits) {
        if (hits == null || hits.isEmpty()) return "[]";
        List<Map<String, Object>> payload = IntStream.range(0, hits.size())
                .mapToObj(i -> {
                    RagChunkHit h = hits.get(i);
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("index", i + 1);
                    m.put("id", h.getChunkId());
                    m.put("filename", h.getFilename());
                    m.put("fileType", h.getFileType());
                    m.put("chunkIndex", h.getChunkIndex());
                    // 赛题 2.2：页码/段落溯源。null 表示源不是 PDF，前端不展示页码。
                    m.put("pageStart", h.getPageStart());
                    m.put("pageEnd", h.getPageEnd());
                    m.put("content", truncate(h.getContent(), 400));
                    m.put("score", h.getScore());
                    return m;
                })
                .toList();
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("serialize chunks failed: {}", e.getMessage());
            return "[]";
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    @FunctionalInterface
    private interface StreamJob {
        void run(java.util.function.Consumer<String> onToken, Runnable onDone);
    }
}
