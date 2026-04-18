package com.reportai.hub.report.controller;

import com.reportai.hub.common.context.UserContext;
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
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

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
        sseExecutor.execute(() -> runStream(emitter, "generate",
                (onToken, onDone) ->
                        generationService.streamGenerate(id, operatorId, onToken, onDone)));
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

    @FunctionalInterface
    private interface StreamJob {
        void run(java.util.function.Consumer<String> onToken, Runnable onDone);
    }
}
