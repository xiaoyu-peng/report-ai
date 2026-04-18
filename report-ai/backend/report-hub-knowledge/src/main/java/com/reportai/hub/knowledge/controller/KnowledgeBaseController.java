package com.reportai.hub.knowledge.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reportai.hub.common.PageResult;
import com.reportai.hub.common.Result;
import com.reportai.hub.common.context.UserContext;
import com.reportai.hub.knowledge.dto.KnowledgeBaseCreateDTO;
import com.reportai.hub.knowledge.entity.KnowledgeBase;
import com.reportai.hub.knowledge.service.KnowledgeBaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "知识库管理")
@RestController
@RequestMapping("/api/v1/knowledge/bases")
@RequiredArgsConstructor
@Validated
public class KnowledgeBaseController {

    private final KnowledgeBaseService baseService;

    @Operation(summary = "分页列出知识库")
    @GetMapping
    public Result<PageResult<KnowledgeBase>> list(
            @RequestParam(defaultValue = "1") @Min(1) Long current,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Long size,
            @RequestParam(required = false) String keyword) {
        Page<KnowledgeBase> page = baseService.listByPage(current, size, keyword);
        return Result.success(PageResult.of(
                page.getRecords(), page.getTotal(), page.getSize(), page.getCurrent()));
    }

    @Operation(summary = "创建知识库")
    @PostMapping
    public Result<KnowledgeBase> create(@Valid @RequestBody KnowledgeBaseCreateDTO dto) {
        KnowledgeBase kb = baseService.create(dto.getName(), dto.getDescription(),
                UserContext.getUserId());
        return Result.success(kb);
    }

    @Operation(summary = "知识库详情")
    @GetMapping("/{id}")
    public Result<KnowledgeBase> detail(@PathVariable Long id) {
        return Result.success(baseService.getById(id));
    }

    @Operation(summary = "更新知识库")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id,
                               @Valid @RequestBody KnowledgeBaseCreateDTO dto) {
        KnowledgeBase kb = baseService.getById(id);
        if (kb == null) return Result.error("知识库不存在");
        kb.setName(dto.getName());
        kb.setDescription(dto.getDescription());
        baseService.updateById(kb);
        return Result.success();
    }

    @Operation(summary = "软删除知识库")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        baseService.removeById(id);
        return Result.success();
    }
}
