package com.reportai.hub.report.controller;

import com.reportai.hub.common.Result;
import com.reportai.hub.report.entity.ReportSection;
import com.reportai.hub.report.service.SectionGenerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 章节级生成 API（5 杀手锏交互 ⑤ 大纲拖拽 + 章节级独立流式）。
 * 调用顺序：
 *   1. POST /sections/init   提交大纲，全 pending 入库
 *   2. GET  /sections/{idx}/stream   逐章 EventSource，前端可并发或顺序
 *   3. POST /assemble        全部 done 后合成全文写入 report.content
 */
@Slf4j
@Tag(name = "章节生成")
@RestController
@RequestMapping("/api/v1/reports/{reportId}")
@RequiredArgsConstructor
public class SectionController {

    private final SectionGenerationService sectionGenerationService;

    /** 章节流式专用线程池（避免占用 Spring MVC 主线程，断连时可中断）。 */
    private final ExecutorService sseExecutor = Executors.newFixedThreadPool(8, r -> {
        Thread t = new Thread(r, "section-sse");
        t.setDaemon(true);
        return t;
    });

    @Operation(summary = "初始化大纲：把 [{title,prompt}...] 拆成 N 个 pending 的 section 行")
    @PostMapping("/sections/init")
    public Result<List<ReportSection>> init(@PathVariable Long reportId,
                                            @RequestBody List<Map<String, String>> outline) {
        return Result.success(sectionGenerationService.initSections(reportId, outline));
    }

    @Operation(summary = "列出本报告所有章节状态（pending/generating/done/failed）")
    @GetMapping("/sections")
    public Result<List<ReportSection>> list(@PathVariable Long reportId) {
        return Result.success(sectionGenerationService.list(reportId));
    }

    @Operation(summary = "SSE 流式生成单章；事件帧：start / chunks / token / done / error")
    @GetMapping(value = "/sections/{idx}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long reportId,
                             @PathVariable Integer idx,
                             @RequestParam(required = false) List<Long> kbIds) {
        // 180s 上限，足够单章生成；同时避免长连接泄漏
        SseEmitter emitter = new SseEmitter(180_000L);
        sseExecutor.submit(() -> {
            try {
                sectionGenerationService.streamSection(reportId, idx, kbIds, raw -> {
                    try {
                        emitter.send(SseEmitter.event().data(raw, MediaType.TEXT_PLAIN));
                    } catch (IOException ignore) {
                        // 客户端已断开，让上层 service 在下一次 emit 时自然失败
                    }
                });
                emitter.complete();
            } catch (Exception e) {
                log.error("section stream {} idx={} fatal: {}", reportId, idx, e.getMessage(), e);
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    @Operation(summary = "把所有 done 章节拼成完整 markdown 写入 report.content")
    @PostMapping("/sections/assemble")
    public Result<String> assemble(@PathVariable Long reportId) {
        return Result.success(sectionGenerationService.assembleAndSave(reportId));
    }
}
