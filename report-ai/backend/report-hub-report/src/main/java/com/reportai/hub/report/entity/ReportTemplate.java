package com.reportai.hub.report.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reportai.hub.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("report_template")
public class ReportTemplate extends BaseEntity {
    private String name;
    private String description;
    /** 模板正文（原稿全文，供前端预览）。 */
    private String content;
    /** 风格分析文本（调 LLM 得出的中文概述）。 */
    private String styleDescription;
    /** 风格分析 JSON（结构 / 语气 / 引用模式等结构化字段）。 */
    private String structureJson;
    private Integer isBuiltin;
    private Long createdBy;
}
