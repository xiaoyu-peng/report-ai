package com.reportai.hub.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 用户在工作台勾掉"不要这条引用"，写入此表，下次检索 NOT IN 过滤。 */
@Data
@TableName("report_excluded_chunk")
public class ReportExcludedChunk {
    private Long reportId;
    private Long chunkId;
}
