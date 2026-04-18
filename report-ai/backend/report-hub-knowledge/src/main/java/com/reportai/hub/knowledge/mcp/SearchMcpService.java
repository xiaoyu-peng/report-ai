package com.reportai.hub.knowledge.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchMcpService {

    private final QingTianMcpClient client;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public JsonNode searchArticles(String keyword, int page, int pageSize) {
        Map<String, Object> searchMode = new LinkedHashMap<>();
        searchMode.put("searchType", 1);
        Map<String, Object> simpleKeyword = new LinkedHashMap<>();
        simpleKeyword.put("anyKeyWord", List.of(keyword));
        searchMode.put("simpleKeyword", simpleKeyword);

        Map<String, Object> searchTime = new LinkedHashMap<>();
        searchTime.put("now", LocalDateTime.now().format(FMT));
        searchTime.put("realTime", 7);

        Map<String, Object> articleInfo = new LinkedHashMap<>();
        articleInfo.put("searchMode", searchMode);
        articleInfo.put("searchTime", searchTime);

        Map<String, Object> pageInfo = new LinkedHashMap<>();
        pageInfo.put("currentPage", page);
        pageInfo.put("pageSize", Math.min(pageSize, 50));

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("articleInfo", articleInfo);
        params.put("pageInfo", pageInfo);

        return client.callSearch("article-search", params);
    }

    public JsonNode searchArticlesAdvanced(List<String> mustKeyWord, List<String> anyKeyWord,
                                            List<String> needExclude, int realTime,
                                            String startPubTime, String endPubTime,
                                            int page, int pageSize) {
        Map<String, Object> searchMode = new LinkedHashMap<>();
        searchMode.put("searchType", 1);
        Map<String, Object> simpleKeyword = new LinkedHashMap<>();
        if (mustKeyWord != null && !mustKeyWord.isEmpty()) simpleKeyword.put("mustKeyWord", mustKeyWord);
        if (anyKeyWord != null && !anyKeyWord.isEmpty()) simpleKeyword.put("anyKeyWord", anyKeyWord);
        if (needExclude != null && !needExclude.isEmpty()) simpleKeyword.put("needExclude", needExclude);
        searchMode.put("simpleKeyword", simpleKeyword);

        Map<String, Object> searchTime = new LinkedHashMap<>();
        searchTime.put("now", LocalDateTime.now().format(FMT));
        searchTime.put("realTime", realTime);
        if (realTime == 0) {
            searchTime.put("startPubTime", startPubTime);
            searchTime.put("endPubTime", endPubTime);
        }

        Map<String, Object> articleInfo = new LinkedHashMap<>();
        articleInfo.put("searchMode", searchMode);
        articleInfo.put("searchTime", searchTime);

        Map<String, Object> pageInfo = new LinkedHashMap<>();
        pageInfo.put("currentPage", page);
        pageInfo.put("pageSize", Math.min(pageSize, 50));

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("articleInfo", articleInfo);
        params.put("pageInfo", pageInfo);

        return client.callSearch("article-search", params);
    }

    public JsonNode getArticleDetail(String uuid, String publishTime) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("uuid", uuid);
        params.put("publishTime", publishTime);
        return client.callSearch("article-detail", params);
    }

    public JsonNode searchMediaAccounts(String keyword, int page, int pageSize) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (keyword != null && !keyword.isBlank()) {
            params.put("userNames", List.of(keyword));
        }
        Map<String, Object> pageInfo = new LinkedHashMap<>();
        pageInfo.put("currentPage", page);
        pageInfo.put("pageSize", Math.min(pageSize, 50));
        params.put("pageInfo", pageInfo);
        return client.callSearch("media-account-search", params);
    }
}
