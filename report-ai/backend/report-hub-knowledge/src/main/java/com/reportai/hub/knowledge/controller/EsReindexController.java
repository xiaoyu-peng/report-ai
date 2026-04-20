package com.reportai.hub.knowledge.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reportai.hub.common.Result;
import com.reportai.hub.knowledge.dto.EsChunkDocument;
import com.reportai.hub.knowledge.entity.KnowledgeChunk;
import com.reportai.hub.knowledge.entity.KnowledgeDocument;
import com.reportai.hub.knowledge.mapper.KnowledgeChunkMapper;
import com.reportai.hub.knowledge.mapper.KnowledgeDocumentMapper;
import com.reportai.hub.knowledge.service.EsChunkService;
import com.reportai.hub.knowledge.service.ElasticsearchIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/es")
@RequiredArgsConstructor
public class EsReindexController {

    private final ElasticsearchIndexService indexService;
    private final EsChunkService esChunkService;
    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeDocumentMapper documentMapper;

    @PostMapping("/reindex")
    public Result<Map<String, Object>> reindex() {
        log.info("Starting ES reindex...");
        long start = System.currentTimeMillis();

        indexService.ensureIndexReady();

        List<KnowledgeDocument> docs = documentMapper.selectList(
            new LambdaQueryWrapper<KnowledgeDocument>()
        );
        Map<Long, String> docNames = docs.stream()
            .collect(Collectors.toMap(KnowledgeDocument::getId, KnowledgeDocument::getFilename));

        List<KnowledgeChunk> chunks = chunkMapper.selectList(
            new LambdaQueryWrapper<KnowledgeChunk>()
        );

        List<EsChunkDocument> esDocs = new ArrayList<>();
        for (KnowledgeChunk c : chunks) {
            String docName = docNames.getOrDefault(c.getDocId(), "unknown");
            esDocs.add(EsChunkDocument.from(c, docName));
        }

        esChunkService.bulkIndexChunks(esDocs);

        long elapsed = System.currentTimeMillis() - start;
        log.info("ES reindex completed: {} chunks indexed in {}ms", esDocs.size(), elapsed);

        return Result.success(Map.of(
            "indexedChunks", esDocs.size(),
            "elapsedMs", elapsed
        ));
    }

    @PostMapping("/ensure-index")
    public Result<String> ensureIndex() {
        indexService.ensureIndexReady();
        return Result.success("Index created/verified");
    }
}
