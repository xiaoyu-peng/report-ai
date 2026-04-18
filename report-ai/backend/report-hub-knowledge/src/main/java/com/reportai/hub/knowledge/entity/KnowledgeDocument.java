package com.reportai.hub.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reportai.hub.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("knowledge_document")
public class KnowledgeDocument extends BaseEntity {
    private Long kbId;
    private String filename;
    private String fileType;
    private Long fileSize;
    private String content;
    /** processing / success / failed */
    private String status;
    private Integer chunkCount;
    private Long createdBy;
}
