package com.reportai.hub.report.service;

import com.reportai.hub.report.dto.QualityReport;

public interface QualityCheckService {

    /**
     * 对已生成的报告跑一次三维度质量检查：覆盖度 / 引用准确性 / 事实性。
     * 赛题模块 3.4 要求。实现走 LLM-as-judge，一次请求返回三维度 JSON。
     */
    QualityReport check(Long reportId);
}
