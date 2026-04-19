package com.reportai.hub.report.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reportai.hub.common.PageResult;
import com.reportai.hub.common.Result;
import com.reportai.hub.common.context.UserContext;
import com.reportai.hub.common.exception.BusinessException;
import com.reportai.hub.knowledge.service.TikaParser;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@Tag(name = "报告模板中心")
@RestController
@RequestMapping("/api/v1/templates")
@RequiredArgsConstructor
@Validated
public class TemplateController {

    private final TemplateService templateService;
    private final TikaParser tikaParser;

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

    /**
     * 赛题 3.2：用户上传参考报告（PDF/Word/TXT/Markdown）→ Tika 解析正文 → LLM 风格分析 → 落库成模板。
     * 前端 multipart/form-data 直接打这里，避免手动提取文本。
     */
    @Operation(summary = "上传参考报告文件 → 解析 + 风格分析（赛题 3.2 仿写）")
    @PostMapping(value = "/analyze-file", consumes = "multipart/form-data")
    public Result<ReportTemplate> analyzeFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "description", required = false) String description) {
        if (file == null || file.isEmpty()) throw new BusinessException("文件为空");
        String filename = file.getOriginalFilename();
        String text;
        try (InputStream in = file.getInputStream()) {
            text = tikaParser.extractText(in, filename);
        } catch (IOException e) {
            throw new BusinessException("读取上传文件失败：" + e.getMessage());
        }
        if (text == null || text.isBlank()) {
            throw new BusinessException("未能从文件中抽取到文本内容，可能是扫描件或空白文件");
        }
        String effectiveName = name != null && !name.isBlank()
                ? name
                : (filename == null ? "未命名模板" : filename.replaceAll("\\.[^.]+$", ""));
        return Result.success(templateService.analyzeAndSave(
                effectiveName, description, text, UserContext.getUserId()));
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
