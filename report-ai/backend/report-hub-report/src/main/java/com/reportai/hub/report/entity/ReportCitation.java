package com.reportai.hub.report.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 报告引用溯源：报告里每个 [n] 角标 → 来源文档+页码+原文片段。前端 popover 用此表渲染溯源卡。 */
@Data
@TableName("report_citation")
public class ReportCitation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long reportId;
    /** 关联到 report_version，版本对比用。null = 当前最新版。 */
    private Long versionId;
    /** 章节索引（章节级 SSE 后启用；整篇生成时统一为 0）。 */
    private Integer sectionIndex;
    /** 章内段落索引（覆盖度计算的最小粒度）。 */
    private Integer paragraphIndex;
    /** 正文里 [n] 的 n 值。 */
    private Integer citationMarker;
    private Long chunkId;
    private Long docId;
    /** 所属知识库 ID，用于跳转到原文。 */
    private Long kbId;
    private String docTitle;
    private Integer pageStart;
    private Integer pageEnd;
    /** 原文片段（截至 200 字），popover 展示用。 */
    private String snippet;
    /** 0 = 用户在 popover 里点过"排除此引用"。 */
    private Boolean accepted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
