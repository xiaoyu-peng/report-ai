package com.reportai.hub.report.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reportai.hub.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("report")
public class Report extends BaseEntity {
    private String title;
    private String content;
    /** draft / generating / ready / archived */
    private String status;
    private Long kbId;
    private Long templateId;
    private String topic;
    /** JSON array of key-points string list. */
    private String keyPoints;
    private Integer wordCount;
    private Long createdBy;
}
