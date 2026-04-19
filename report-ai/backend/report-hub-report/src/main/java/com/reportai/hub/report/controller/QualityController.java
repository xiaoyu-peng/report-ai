package com.reportai.hub.report.controller;

import com.reportai.hub.common.Result;
import com.reportai.hub.report.dto.QualityReport;
import com.reportai.hub.report.entity.ReportQuality;
import com.reportai.hub.report.service.QualityCheckService;
import com.reportai.hub.report.service.QualityMetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "报告质量保障")
@RestController
@RequestMapping("/api/v1/reports/{id}/quality")
@RequiredArgsConstructor
public class QualityController {

    private final QualityCheckService qualityCheckService;
    private final QualityMetricsService qualityMetricsService;

    @Operation(summary = "三维度 LLM-as-judge 检查：覆盖度 + 引用准确性 + 事实性 (legacy)")
    @GetMapping("/check")
    public Result<QualityReport> check(@PathVariable("id") Long id) {
        return Result.success(qualityCheckService.check(id));
    }

    @Operation(summary = "T5 覆盖度仪表盘所需：含覆盖率/KB分布/可疑列表（读取最近一次结果）")
    @GetMapping
    public Result<ReportQuality> get(@PathVariable("id") Long id) {
        return Result.success(qualityMetricsService.get(id));
    }

    @Operation(summary = "重新跑一次 T5 体检并落库")
    @PostMapping("/recheck")
    public Result<ReportQuality> recheck(@PathVariable("id") Long id) {
        return Result.success(qualityMetricsService.runCheck(id));
    }
}
