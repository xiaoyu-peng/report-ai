package com.reportai.hub.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableField;
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
    /**
     * 原始文件二进制（<=10MB）。默认不随 selectById 等通用查询返回——太重；
     * 想拿必须单独查。`select=false` 让 MyBatis-Plus 跳过列。
     */
    @TableField(value = "file_blob", select = false)
    private byte[] fileBlob;
    /** processing / success / failed */
    private String status;
    private Integer chunkCount;
    private Long createdBy;
}
