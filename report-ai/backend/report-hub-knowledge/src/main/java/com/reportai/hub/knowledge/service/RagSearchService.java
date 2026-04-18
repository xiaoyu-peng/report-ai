package com.reportai.hub.knowledge.service;

import com.reportai.hub.knowledge.dto.RagChunkHit;
import com.reportai.hub.knowledge.dto.RagSearchResponse;

import java.util.List;

public interface RagSearchService {

    RagSearchResponse search(Long kbId, String query, int topK);

    RagSearchResponse search(Long kbId, String query, int topK, String includeKeywords, String excludeKeywords);

    List<RagChunkHit> searchRaw(Long kbId, String query, int topK);
}
