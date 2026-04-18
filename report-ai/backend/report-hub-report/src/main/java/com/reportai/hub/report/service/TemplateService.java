package com.reportai.hub.report.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.reportai.hub.report.entity.ReportTemplate;

public interface TemplateService extends IService<ReportTemplate> {

    /** 调 LLM 对模板正文做风格分析，返回落库后的 template（带 styleDescription / structureJson）。 */
    ReportTemplate analyzeAndSave(String name, String description, String content,
                                  Long operatorId);

    /** 重新对已有模板跑一次风格分析（例：更换 LLM provider 后重跑）。 */
    ReportTemplate reanalyze(Long templateId);

    Page<ReportTemplate> listByPage(long current, long size, String keyword);
}
