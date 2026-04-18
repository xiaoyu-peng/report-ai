package com.reportai.hub.knowledge.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchMcpService {

    private final QingTianMcpClient client;

    public JsonNode searchArticles(String keyword, int page, int pageSize) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("keyword", keyword);
        params.put("page", page);
        params.put("pageSize", pageSize);
        return client.callSearch("article-search", params);
    }

    public JsonNode getArticleDetail(String articleId) {
        Map<String, Object> params = Map.of("articleId", articleId);
        return client.callSearch("article-detail", params);
    }

    public JsonNode searchMediaAccounts(String keyword, int page, int pageSize) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("keyword", keyword);
        params.put("page", page);
        params.put("pageSize", pageSize);
        return client.callSearch("media-account-search", params);
    }
}
