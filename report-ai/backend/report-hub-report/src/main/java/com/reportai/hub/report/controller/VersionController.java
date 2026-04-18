package com.reportai.hub.report.controller;

import com.reportai.hub.common.Result;
import com.reportai.hub.common.context.UserContext;
import com.reportai.hub.report.dto.DiffResult;
import com.reportai.hub.report.entity.ReportVersion;
import com.reportai.hub.report.service.VersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "报告版本管理")
@RestController
@RequestMapping("/api/v1/reports/{reportId}/versions")
@RequiredArgsConstructor
public class VersionController {

    private final VersionService versionService;

    @Operation(summary = "列出所有版本")
    @GetMapping
    public Result<List<ReportVersion>> list(@PathVariable Long reportId) {
        return Result.success(versionService.listVersions(reportId));
    }

    @Operation(summary = "取指定版本")
    @GetMapping("/{versionNum}")
    public Result<ReportVersion> get(@PathVariable Long reportId,
                                     @PathVariable int versionNum) {
        return Result.success(versionService.getVersion(reportId, versionNum));
    }

    @Operation(summary = "两版本 diff（行级）")
    @GetMapping("/{fromVersion}/diff/{toVersion}")
    public Result<DiffResult> diff(@PathVariable Long reportId,
                                   @PathVariable int fromVersion,
                                   @PathVariable int toVersion) {
        return Result.success(versionService.diff(reportId, fromVersion, toVersion));
    }

    @Operation(summary = "回滚到指定版本（不破坏历史，新建一版）")
    @PostMapping("/{versionNum}/restore")
    public Result<ReportVersion> restore(@PathVariable Long reportId,
                                         @PathVariable int versionNum) {
        return Result.success(versionService.restore(reportId, versionNum, UserContext.getUserId()));
    }
}
