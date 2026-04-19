package com.reportai.hub.report.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.reportai.hub.common.Result;
import com.reportai.hub.report.entity.ReportCitation;
import com.reportai.hub.report.mapper.ReportCitationMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 报告引用溯源 API：前端 hover/click [n] 角标弹出 popover 时读这里。
 * 接受/排除单条引用：影响下次重新生成时的检索过滤（写入 report_excluded_chunk）。
 */
@Tag(name = "报告引用溯源")
@RestController
@RequestMapping("/api/v1/reports/{reportId}/citations")
@RequiredArgsConstructor
public class CitationController {

    private final ReportCitationMapper citationMapper;

    @Operation(summary = "列出该报告所有 accepted 的引用（按 marker 排序）")
    @GetMapping
    public Result<List<ReportCitation>> list(@PathVariable Long reportId) {
        List<ReportCitation> rows = citationMapper.selectList(
                new QueryWrapper<ReportCitation>()
                        .eq("report_id", reportId)
                        .eq("accepted", true)
                        .orderByAsc("section_index", "citation_marker"));
        return Result.success(rows);
    }

    @Operation(summary = "用户在 popover 里点「排除此引用」")
    @PostMapping("/{marker}/exclude")
    public Result<Void> exclude(@PathVariable Long reportId, @PathVariable Integer marker) {
        citationMapper.update(null, new UpdateWrapper<ReportCitation>()
                .eq("report_id", reportId)
                .eq("citation_marker", marker)
                .set("accepted", false));
        return Result.success();
    }

    @Operation(summary = "撤销排除（点错了想恢复）")
    @PostMapping("/{marker}/restore")
    public Result<Void> restore(@PathVariable Long reportId, @PathVariable Integer marker) {
        citationMapper.update(null, new UpdateWrapper<ReportCitation>()
                .eq("report_id", reportId)
                .eq("citation_marker", marker)
                .set("accepted", true));
        return Result.success();
    }
}
