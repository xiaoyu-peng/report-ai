package com.reportai.hub.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_chunk")
public class KnowledgeChunk {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long docId;
    private Long kbId;
    private String content;
    private Integer chunkIndex;
    /** PDF 源的起止页码（1-based）；非 PDF 为 null。用于溯源卡"第 X-Y 页"展示。 */
    private Integer pageStart;
    private Integer pageEnd;
    /** 该 chunk 起始段落在原文中的全局序号（0-based），用于"引用溯源到段落"。 */
    private Integer paragraphIndex;
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createdAt;
}
