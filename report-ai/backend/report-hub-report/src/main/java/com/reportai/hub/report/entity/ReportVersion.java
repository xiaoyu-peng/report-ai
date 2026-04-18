package com.reportai.hub.report.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("report_version")
public class ReportVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long reportId;
    private Integer versionNum;
    private String title;
    private String content;
    private String changeSummary;
    /** initial / regenerate / rewrite_data_update / rewrite_angle_shift /
     *  rewrite_expand / rewrite_style_shift / manual_edit */
    private String sourceMode;
    private Integer wordCount;
    private Long createdBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
