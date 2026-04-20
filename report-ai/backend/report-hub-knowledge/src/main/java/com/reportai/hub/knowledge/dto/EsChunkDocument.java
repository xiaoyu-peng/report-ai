package com.reportai.hub.knowledge.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.format.DateTimeFormatter;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EsChunkDocument {
    private Long id;
    private Long kbId;
    private Long docId;
    private String docName;
    private String content;
    private Integer chunkIndex;
    private Integer pageStart;
    private Integer pageEnd;

    private String createdAt;

    /** ES `_score`：只在查询返回时被 EsChunkService 填充，不写入索引，不参与反序列化。 */
    @JsonIgnore
    private transient Double score;

    private static final DateTimeFormatter ES_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public static EsChunkDocument from(com.reportai.hub.knowledge.entity.KnowledgeChunk chunk, String docName) {
        return EsChunkDocument.builder()
            .id(chunk.getId())
            .kbId(chunk.getKbId())
            .docId(chunk.getDocId())
            .docName(docName)
            .content(chunk.getContent())
            .chunkIndex(chunk.getChunkIndex())
            .pageStart(chunk.getPageStart())
            .pageEnd(chunk.getPageEnd())
            .createdAt(chunk.getCreatedAt() != null ? chunk.getCreatedAt().format(ES_DATE_FORMAT) : null)
            .build();
    }
}
