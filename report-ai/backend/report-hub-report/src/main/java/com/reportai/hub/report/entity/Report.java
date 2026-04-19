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
    /** 赛题 2.3：用户补充的检索关键词（空白分隔）。生成时会拼进 RAG BOOLEAN query 作为 +term。 */
    private String includeKeywords;
    /** 赛题 2.3：用户要排除的关键词（逗号/空白分隔）。生成时拼进 BOOLEAN query 作为 -term。 */
    private String excludeKeywords;
    /** 生成深度：brief（简洁 800 字 / topK=4）/ standard（默认 2000 字 / topK=8）/ deep（深度 4000 字 / topK=16）。 */
    private String generationDepth;
    private Integer wordCount;
    private Long createdBy;
}
