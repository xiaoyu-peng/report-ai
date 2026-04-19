package com.reportai.hub.report.controller;

import com.reportai.hub.common.Result;
import com.reportai.hub.report.dto.QualityReport;
import com.reportai.hub.report.service.QualityCheckService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "报告质量保障")
@RestController
@RequestMapping("/api/v1/reports/{id}/quality")
@RequiredArgsConstructor
public class QualityController {

    private final QualityCheckService qualityCheckService;

    @GetMapping("/check")
    @Operation(summary = "三维度质量检查：覆盖度 + 引用准确性 + 事实性")
    public Result<QualityReport> check(@PathVariable("id") Long id) {
        return Result.success(qualityCheckService.check(id));
    }
}
