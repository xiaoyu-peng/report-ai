package com.reportai.hub.log.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reportai.hub.common.PageResult;
import com.reportai.hub.common.Result;
import com.reportai.hub.common.entity.OperationLog;
import com.reportai.hub.common.service.OperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "日志管理", description = "操作日志查询")
@RestController
@RequestMapping("/api/v1/logs")
@RequiredArgsConstructor
@Validated
public class LogController {

    private final OperationLogService operationLogService;

    @Operation(summary = "分页查询操作日志")
    @GetMapping("/operations")
    public Result<PageResult<OperationLog>> listOperationLogs(
            @RequestParam(defaultValue = "1") @Min(1) Long current,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Long size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String status) {

        Page<OperationLog> page = new Page<>(current, size);
        IPage<OperationLog> result = operationLogService.page(page,
            new LambdaQueryWrapper<OperationLog>()
                .like(username != null, OperationLog::getUsername, username)
                .eq(action != null, OperationLog::getAction, action)
                .eq(status != null, OperationLog::getStatus, status)
                .orderByDesc(OperationLog::getCreatedAt)
        );

        return Result.success(PageResult.of(
            result.getRecords(), result.getTotal(),
            result.getSize(), result.getCurrent()));
    }
}
