package com.reportai.hub.report.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reportai.hub.common.PageResult;
import com.reportai.hub.common.Result;
import com.reportai.hub.common.context.UserContext;
import com.reportai.hub.report.entity.ReportTemplate;
import com.reportai.hub.report.service.TemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "报告模板中心")
@RestController
@RequestMapping("/api/v1/templates")
@RequiredArgsConstructor
@Validated
public class TemplateController {

    private final TemplateService templateService;

    @Operation(summary = "分页模板列表")
    @GetMapping
    public Result<PageResult<ReportTemplate>> list(
            @RequestParam(defaultValue = "1") @Min(1) Long current,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Long size,
            @RequestParam(required = false) String keyword) {
        Page<ReportTemplate> page = templateService.listByPage(current, size, keyword);
        return Result.success(PageResult.of(
                page.getRecords(), page.getTotal(), page.getSize(), page.getCurrent()));
    }

    @Operation(summary = "创建模板并立即做风格分析")
    @PostMapping
    public Result<ReportTemplate> create(@Validated @RequestBody CreateDto dto) {
        return Result.success(templateService.analyzeAndSave(
                dto.getName(), dto.getDescription(), dto.getContent(), UserContext.getUserId()));
    }

    @Operation(summary = "单独触发风格分析（不保存）—— 前端预览用")
    @PostMapping("/analyze")
    public Result<ReportTemplate> analyze(@Validated @RequestBody CreateDto dto) {
        // 复用 create 逻辑，但 UI 场景希望不落库时可加参数开关；当前先统一存一份
        return Result.success(templateService.analyzeAndSave(
                dto.getName(), dto.getDescription(), dto.getContent(), UserContext.getUserId()));
    }

    @Operation(summary = "模板详情")
    @GetMapping("/{id}")
    public Result<ReportTemplate> detail(@PathVariable Long id) {
        return Result.success(templateService.getById(id));
    }

    @Operation(summary = "重新做风格分析")
    @PostMapping("/{id}/reanalyze")
    public Result<ReportTemplate> reanalyze(@PathVariable Long id) {
        return Result.success(templateService.reanalyze(id));
    }

    @Operation(summary = "更新模板")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody CreateDto dto) {
        ReportTemplate t = templateService.getById(id);
        if (t == null) return Result.error("模板不存在");
        if (dto.getName() != null) t.setName(dto.getName());
        if (dto.getDescription() != null) t.setDescription(dto.getDescription());
        if (dto.getContent() != null) t.setContent(dto.getContent());
        templateService.updateById(t);
        return Result.success();
    }

    @Operation(summary = "软删除")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        templateService.removeById(id);
        return Result.success();
    }

    @Data
    public static class CreateDto {
        @NotBlank
        private String name;
        private String description;
        @NotBlank
        private String content;
    }
}
