package com.reportai.hub.knowledge.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchIndexService {

    public static final String CHUNK_INDEX_NAME = "knowledge_chunks";

    private final ElasticsearchClient esClient;

    @PostConstruct
    public void init() {
        try {
            createChunkIndexIfNotExists();
            log.info("ES indices initialized successfully");
        } catch (Exception e) {
            log.warn("ES index init failed (will retry on first write): {}", e.getMessage());
        }
    }

    public void createChunkIndexIfNotExists() throws Exception {
        boolean exists = esClient.indices()
            .exists(ExistsRequest.of(e -> e.index(CHUNK_INDEX_NAME)))
            .value();

        if (!exists) {
            CreateIndexRequest request = CreateIndexRequest.of(c -> c
                .index(CHUNK_INDEX_NAME)
                .settings(s -> s
                    .analysis(a -> a
                        .analyzer("chinese_analyzer", an -> an
                            .custom(ca -> ca
                                .tokenizer("standard")
                                .filter("lowercase", "asciifolding")
                            )
                        )
                    )
                )
                .mappings(m -> m
                    .properties("id", p -> p.long_(l -> l))
                    .properties("kbId", p -> p.long_(l -> l))
                    .properties("docId", p -> p.long_(l -> l))
                    .properties("docName", p -> p.keyword(k -> k))
                    .properties("content", p -> p.text(t -> t
                        .analyzer("chinese_analyzer")
                        .searchAnalyzer("chinese_analyzer")
                    ))
                    .properties("chunkIndex", p -> p.integer(i -> i))
                    .properties("pageStart", p -> p.integer(i -> i))
                    .properties("pageEnd", p -> p.integer(i -> i))
                    .properties("createdAt", p -> p.date(d -> d))
                )
            );
            esClient.indices().create(request);
            log.info("Created ES index: {}", CHUNK_INDEX_NAME);
        }
    }

    public void ensureIndexReady() {
        try {
            createChunkIndexIfNotExists();
        } catch (Exception e) {
            throw new RuntimeException("Failed to ensure ES index: " + e.getMessage(), e);
        }
    }
}
