package com.reportai.hub.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 章节级流式生成的状态机：pending → generating → done / failed。 */
@Data
@TableName("report_section")
public class ReportSection {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long reportId;
    private Integer sectionIndex;
    private String title;
    private String prompt;
    private String status;            // pending/generating/done/failed
    private String content;
    private Integer wordCount;
    private Integer citationCount;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
