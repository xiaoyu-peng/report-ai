package com.reportai.hub.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.reportai.hub.knowledge.entity.KnowledgeChunk;
import com.reportai.hub.knowledge.dto.RagChunkHit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunk> {

    /**
     * MySQL FULLTEXT 检索 + 溯源。
     * 返回 chunk + 文档元信息 + 相关度分数（大越相关）。
     * 仅在 kbId 匹配的范围内查。
     *
     * 使用 BOOLEAN MODE 以支持中文单字匹配（配合 innodb-ft-min-token-size=1）。
     */
    @Select("""
        SELECT c.id          AS chunkId,
               c.doc_id      AS docId,
               c.kb_id       AS kbId,
               c.chunk_index AS chunkIndex,
               c.content     AS content,
               c.page_start  AS pageStart,
               c.page_end    AS pageEnd,
               d.filename    AS filename,
               d.file_type   AS fileType,
               MATCH(c.content) AGAINST(#{q} IN BOOLEAN MODE) AS score
        FROM knowledge_chunk c
        JOIN knowledge_document d ON d.id = c.doc_id AND d.deleted = 0
        WHERE c.kb_id = #{kbId}
          AND MATCH(c.content) AGAINST(#{q} IN BOOLEAN MODE)
        ORDER BY score DESC
        LIMIT #{topK}
        """)
    List<RagChunkHit> searchFulltext(@Param("kbId") Long kbId,
                                     @Param("q") String q,
                                     @Param("topK") int topK);

    /**
     * 多知识库 + 排除引用 + 段落定位 + KB 名的增强检索。
     * - kbIds 为空 / null 表示不限制；
     * - excludedChunkIds 为空 / null 表示不排除；
     * - 返回字段含 paragraphIndex / kbName，供溯源卡 + 仪表盘使用。
     */
    @Select("""
        <script>
        SELECT c.id              AS chunkId,
               c.doc_id           AS docId,
               c.kb_id            AS kbId,
               c.chunk_index      AS chunkIndex,
               c.content          AS content,
               c.page_start       AS pageStart,
               c.page_end         AS pageEnd,
               c.paragraph_index  AS paragraphIndex,
               d.filename         AS filename,
               d.file_type        AS fileType,
               kb.name            AS kbName,
               MATCH(c.content) AGAINST(#{q} IN BOOLEAN MODE) AS score
        FROM knowledge_chunk c
        JOIN knowledge_document d ON d.id = c.doc_id AND d.deleted = 0
        JOIN knowledge_base kb     ON kb.id = c.kb_id
        WHERE MATCH(c.content) AGAINST(#{q} IN BOOLEAN MODE)
        <if test="kbIds != null and kbIds.size() > 0">
          AND c.kb_id IN
          <foreach collection="kbIds" item="id" open="(" separator="," close=")">#{id}</foreach>
        </if>
        <if test="excludedChunkIds != null and excludedChunkIds.size() > 0">
          AND c.id NOT IN
          <foreach collection="excludedChunkIds" item="id" open="(" separator="," close=")">#{id}</foreach>
        </if>
        ORDER BY score DESC
        LIMIT #{topK}
        </script>
        """)
    List<RagChunkHit> searchFulltextEnhanced(@Param("kbIds") Collection<Long> kbIds,
                                             @Param("q") String q,
                                             @Param("topK") int topK,
                                             @Param("excludedChunkIds") Collection<Long> excludedChunkIds);
}
