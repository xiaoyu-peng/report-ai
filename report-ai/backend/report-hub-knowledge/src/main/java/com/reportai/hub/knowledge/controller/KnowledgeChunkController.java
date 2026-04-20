package com.reportai.hub.knowledge.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reportai.hub.common.Result;
import com.reportai.hub.knowledge.entity.KnowledgeChunk;
import com.reportai.hub.knowledge.entity.KnowledgeDocument;
import com.reportai.hub.knowledge.mapper.KnowledgeChunkMapper;
import com.reportai.hub.knowledge.mapper.KnowledgeDocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/chunks")
@RequiredArgsConstructor
public class KnowledgeChunkController {

    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeDocumentMapper documentMapper;

    @GetMapping("/{id}")
    public Result<Map<String, Object>> getChunk(@PathVariable Long id) {
        KnowledgeChunk chunk = chunkMapper.selectById(id);
        if (chunk == null) {
            return Result.error(404, "Chunk not found");
        }

        KnowledgeDocument doc = documentMapper.selectById(chunk.getDocId());

        Map<String, Object> result = new HashMap<>();
        result.put("id", chunk.getId());
        result.put("kbId", chunk.getKbId());
        result.put("docId", chunk.getDocId());
        result.put("docName", doc != null ? doc.getFilename() : null);
        result.put("content", chunk.getContent());
        result.put("chunkIndex", chunk.getChunkIndex());
        result.put("pageStart", chunk.getPageStart());
        result.put("pageEnd", chunk.getPageEnd());
        result.put("paragraphIndex", chunk.getParagraphIndex());
        result.put("createdAt", chunk.getCreatedAt());

        return Result.success(result);
    }

    @GetMapping("/doc/{docId}")
    public Result<List<Map<String, Object>>> getChunksByDocId(@PathVariable Long docId) {
        List<KnowledgeChunk> chunks = chunkMapper.selectList(
            new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getDocId, docId)
                .orderByAsc(KnowledgeChunk::getChunkIndex)
        );

        KnowledgeDocument doc = documentMapper.selectById(docId);

        List<Map<String, Object>> result = chunks.stream().map(chunk -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", chunk.getId());
            m.put("kbId", chunk.getKbId());
            m.put("docId", chunk.getDocId());
            m.put("docName", doc != null ? doc.getFilename() : null);
            m.put("content", chunk.getContent());
            m.put("chunkIndex", chunk.getChunkIndex());
            m.put("pageStart", chunk.getPageStart());
            m.put("pageEnd", chunk.getPageEnd());
            m.put("paragraphIndex", chunk.getParagraphIndex());
            m.put("createdAt", chunk.getCreatedAt());
            return m;
        }).toList();

        return Result.success(result);
    }
}
