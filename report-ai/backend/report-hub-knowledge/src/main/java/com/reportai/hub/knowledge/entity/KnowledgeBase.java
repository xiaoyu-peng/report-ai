package com.reportai.hub.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reportai.hub.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("knowledge_base")
public class KnowledgeBase extends BaseEntity {
    private String name;
    private String description;
    private String status;
    private Integer docCount;
    private Integer chunkCount;
    private Long createdBy;
}
