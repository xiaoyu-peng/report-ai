package com.reportai.hub.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reportai.hub.common.Result;
import com.reportai.hub.knowledge.mapper.KnowledgeBaseMapper;
import com.reportai.hub.report.entity.Report;
import com.reportai.hub.report.mapper.ReportMapper;
import com.reportai.hub.user.mapper.UserMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final ReportMapper reportMapper;
    private final KnowledgeBaseMapper kbMapper;
    private final UserMapper userMapper;

    @GetMapping("/stats")
    public Result<DashboardStats> getStats() {
        DashboardStats stats = new DashboardStats();
        stats.setTotalReports(reportMapper.selectCount(null).intValue());
        stats.setTotalKnowledgeBases(kbMapper.selectCount(null).intValue());
        stats.setTotalUsers(userMapper.selectCount(null).intValue());
        stats.setTodayGenerated(reportMapper.selectCount(
                new LambdaQueryWrapper<Report>()
                        .ge(Report::getCreatedAt, LocalDate.now().atStartOfDay())
        ).intValue());
        return Result.success(stats);
    }

    @Data
    public static class DashboardStats {
        private Integer totalReports;
        private Integer totalKnowledgeBases;
        private Integer totalUsers;
        private Integer todayGenerated;
    }
}
