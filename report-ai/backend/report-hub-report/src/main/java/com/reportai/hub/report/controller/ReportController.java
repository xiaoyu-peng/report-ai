package com.reportai.hub.report.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reportai.hub.common.PageResult;
import com.reportai.hub.common.Result;
import com.reportai.hub.common.context.UserContext;
import com.reportai.hub.report.dto.ReportCreateDTO;
import com.reportai.hub.report.entity.Report;
import com.reportai.hub.report.mapper.ReportMapper;
import com.reportai.hub.report.service.ReportGenerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "报告管理")
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Validated
public class ReportController {

    private final ReportMapper reportMapper;
    private final ReportGenerationService generationService;

    @Operation(summary = "分页列出报告")
    @GetMapping
    public Result<PageResult<Report>> list(
            @RequestParam(defaultValue = "1") @Min(1) Long current,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        Page<Report> page = reportMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<Report>()
                        .like(keyword != null && !keyword.isBlank(), Report::getTitle, keyword)
                        .eq(status != null && !status.isBlank(), Report::getStatus, status)
                        .orderByDesc(Report::getCreatedAt));
        return Result.success(PageResult.of(
                page.getRecords(), page.getTotal(), page.getSize(), page.getCurrent()));
    }

    @Operation(summary = "创建草稿")
    @PostMapping
    public Result<Report> create(@Valid @RequestBody ReportCreateDTO dto) {
        return Result.success(generationService.createDraft(dto, UserContext.getUserId()));
    }

    @Operation(summary = "报告详情")
    @GetMapping("/{id}")
    public Result<Report> detail(@PathVariable Long id) {
        return Result.success(reportMapper.selectById(id));
    }

    @Operation(summary = "保存编辑后的正文")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Report body) {
        Report cur = reportMapper.selectById(id);
        if (cur == null) return Result.error("报告不存在");
        if (body.getTitle() != null) cur.setTitle(body.getTitle());
        if (body.getContent() != null) {
            cur.setContent(body.getContent());
            cur.setWordCount(body.getContent().length());
        }
        if (body.getStatus() != null) cur.setStatus(body.getStatus());
        reportMapper.updateById(cur);
        return Result.success();
    }

    @Operation(summary = "软删除")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        reportMapper.deleteById(id);
        return Result.success();
    }
}
