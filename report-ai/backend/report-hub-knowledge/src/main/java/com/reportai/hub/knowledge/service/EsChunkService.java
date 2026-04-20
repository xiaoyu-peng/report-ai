package com.reportai.hub.knowledge.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.reportai.hub.knowledge.dto.EsChunkDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EsChunkService {

    private final ElasticsearchClient esClient;
    private final ElasticsearchIndexService indexService;

    public void indexChunk(EsChunkDocument doc) {
        try {
            indexService.ensureIndexReady();
            esClient.index(i -> i
                .index(ElasticsearchIndexService.CHUNK_INDEX_NAME)
                .id(String.valueOf(doc.getId()))
                .document(doc)
            );
            log.debug("Indexed chunk {} to ES", doc.getId());
        } catch (IOException e) {
            log.error("Failed to index chunk {}: {}", doc.getId(), e.getMessage());
        }
    }

    public void bulkIndexChunks(List<EsChunkDocument> docs) {
        if (docs == null || docs.isEmpty()) return;
        try {
            indexService.ensureIndexReady();
            BulkRequest.Builder br = new BulkRequest.Builder();
            for (EsChunkDocument doc : docs) {
                br.operations(op -> op
                    .index(idx -> idx
                        .index(ElasticsearchIndexService.CHUNK_INDEX_NAME)
                        .id(String.valueOf(doc.getId()))
                        .document(doc)
                    )
                );
            }
            BulkResponse response = esClient.bulk(br.build());
            if (response.errors()) {
                log.warn("Bulk index had errors: {}", response.items().stream()
                    .filter(i -> i.error() != null)
                    .map(i -> i.id() + ": " + i.error().reason())
                    .toList());
            } else {
                log.info("Bulk indexed {} chunks to ES", docs.size());
            }
        } catch (IOException e) {
            log.error("Failed to bulk index chunks: {}", e.getMessage());
        }
    }

    public void deleteByDocId(Long docId) {
        try {
            esClient.deleteByQuery(d -> d
                .index(ElasticsearchIndexService.CHUNK_INDEX_NAME)
                .query(q -> q
                    .term(t -> t.field("docId").value(docId))
                )
            );
            log.debug("Deleted chunks for doc {} from ES", docId);
        } catch (IOException e) {
            log.error("Failed to delete chunks for doc {}: {}", docId, e.getMessage());
        }
    }

    public void deleteByKbId(Long kbId) {
        try {
            esClient.deleteByQuery(d -> d
                .index(ElasticsearchIndexService.CHUNK_INDEX_NAME)
                .query(q -> q
                    .term(t -> t.field("kbId").value(kbId))
                )
            );
            log.debug("Deleted chunks for kb {} from ES", kbId);
        } catch (IOException e) {
            log.error("Failed to delete chunks for kb {}: {}", kbId, e.getMessage());
        }
    }

    public List<EsChunkDocument> search(Long kbId, String query, int topK) {
        try {
            boolean exists = esClient.indices()
                .exists(e -> e.index(ElasticsearchIndexService.CHUNK_INDEX_NAME))
                .value();
            if (!exists) {
                log.debug("ES index does not exist, returning empty results");
                return List.of();
            }
        } catch (IOException e) {
            log.warn("Failed to check ES index existence: {}", e.getMessage());
            return List.of();
        }

        try {
            SearchResponse<EsChunkDocument> response = esClient.search(s -> s
                .index(ElasticsearchIndexService.CHUNK_INDEX_NAME)
                .size(topK)
                .query(q -> q
                    .bool(b -> {
                        b.must(m -> m
                            .term(t -> t.field("kbId").value(kbId))
                        );
                        if (query != null && !query.isBlank()) {
                            b.must(m -> m
                                .match(mt -> mt
                                    .field("content")
                                    .query(query)
                                )
                            );
                        }
                        return b;
                    })
                )
                .highlight(h -> h
                    .fields("content", f -> f
                        .preTags("<em>")
                        .postTags("</em>")
                    )
                ),
                EsChunkDocument.class
            );

            List<EsChunkDocument> results = new ArrayList<>();
            for (Hit<EsChunkDocument> hit : response.hits().hits()) {
                if (hit.source() != null) {
                    results.add(hit.source());
                }
            }
            log.debug("ES search returned {} results for kbId={}, query={}", results.size(), kbId, query);
            return results;
        } catch (IOException e) {
            log.error("ES search failed: {}", e.getMessage());
            return List.of();
        }
    }

    public List<EsChunkDocument> searchWithIncludeExclude(Long kbId, String query,
            List<String> includeKeywords, List<String> excludeKeywords, int topK) {
        try {
            boolean exists = esClient.indices()
                .exists(e -> e.index(ElasticsearchIndexService.CHUNK_INDEX_NAME))
                .value();
            if (!exists) {
                log.debug("ES index does not exist, returning empty results");
                return List.of();
            }
        } catch (IOException e) {
            log.warn("Failed to check ES index existence: {}", e.getMessage());
            return List.of();
        }

        try {
            SearchResponse<EsChunkDocument> response = esClient.search(s -> s
                .index(ElasticsearchIndexService.CHUNK_INDEX_NAME)
                .size(topK)
                .query(q -> q
                    .bool(b -> {
                        b.must(m -> m
                            .term(t -> t.field("kbId").value(kbId))
                        );
                        if (query != null && !query.isBlank()) {
                            b.must(m -> m
                                .match(mt -> mt
                                    .field("content")
                                    .query(query)
                                )
                            );
                        }
                        if (includeKeywords != null) {
                            for (String kw : includeKeywords) {
                                if (kw != null && !kw.isBlank()) {
                                    b.must(m -> m
                                        .match(mt -> mt.field("content").query(kw))
                                    );
                                }
                            }
                        }
                        if (excludeKeywords != null) {
                            for (String kw : excludeKeywords) {
                                if (kw != null && !kw.isBlank()) {
                                    b.mustNot(m -> m
                                        .match(mt -> mt.field("content").query(kw))
                                    );
                                }
                            }
                        }
                        return b;
                    })
                )
                .highlight(h -> h
                    .fields("content", f -> f
                        .preTags("<em>")
                        .postTags("</em>")
                    )
                ),
                EsChunkDocument.class
            );

            List<EsChunkDocument> results = new ArrayList<>();
            for (Hit<EsChunkDocument> hit : response.hits().hits()) {
                if (hit.source() != null) {
                    results.add(hit.source());
                }
            }
            log.debug("ES search with filters returned {} results", results.size());
            return results;
        } catch (IOException e) {
            log.error("ES search with filters failed: {}", e.getMessage());
            return List.of();
        }
    }
}
