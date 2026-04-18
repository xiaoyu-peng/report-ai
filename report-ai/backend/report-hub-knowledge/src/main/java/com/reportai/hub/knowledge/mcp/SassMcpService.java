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
public class SassMcpService {

    private final QingTianMcpClient client;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Map<String, Object> buildBaseParams(String topic, String startDate, String endDate) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("searchKeywordType", 1);
        params.put("mustKeyWord", List.of(topic));
        if (startDate != null && endDate != null) {
            params.put("realTime", 0);
            params.put("startPubTime", startDate + " 00:00:00");
            params.put("endPubTime", endDate + " 23:59:59");
        } else {
            params.put("realTime", 30);
        }
        return params;
    }

    public JsonNode overview(String topic, String startDate, String endDate) {
        Map<String, Object> params = buildBaseParams(topic, startDate, endDate);
        return client.callSass("overview", params);
    }

    public JsonNode hotArticle(String topic, String startDate, String endDate, int topN) {
        Map<String, Object> params = buildBaseParams(topic, startDate, endDate);
        if (topN > 0) params.put("topN", topN);
        return client.callSass("hot-article", params);
    }

    public JsonNode emotionalDistribution(String topic, String startDate, String endDate) {
        Map<String, Object> params = buildBaseParams(topic, startDate, endDate);
        params.put("updateFlag", 0);
        params.put("comIndex", 0);
        return client.callSass("emotional-distribution", params);
    }

    public JsonNode datasourceSound(String topic, String startDate, String endDate) {
        Map<String, Object> params = buildBaseParams(topic, startDate, endDate);
        return client.callSass("datasourceSound", params);
    }

    public JsonNode stageEnvolution(String topic, String startDate, String endDate) {
        Map<String, Object> params = buildBaseParams(topic, startDate, endDate);
        return client.callSass("stage-envolution", params);
    }

    public JsonNode generateEventTopicInfo(String topic, String startDate, String endDate) {
        Map<String, Object> params = buildBaseParams(topic, startDate, endDate);
        return client.callSass("generate-event-topic-info", params);
    }

    public JsonNode hotWords(String topic, String startDate, String endDate) {
        Map<String, Object> params = buildBaseParams(topic, startDate, endDate);
        return client.callSass("hot-words", params);
    }

    public JsonNode hotOrg(String topic, String startDate, String endDate) {
        Map<String, Object> params = buildBaseParams(topic, startDate, endDate);
        return client.callSass("hot-org", params);
    }

    public JsonNode hotPerson(String topic, String startDate, String endDate) {
        Map<String, Object> params = buildBaseParams(topic, startDate, endDate);
        return client.callSass("hot-person", params);
    }

    public JsonNode influenceIndexation(String topic, String startDate, String endDate) {
        Map<String, Object> params = buildBaseParams(topic, startDate, endDate);
        return client.callSass("influence-indexation", params);
    }

    public JsonNode contentClassification(String topic, String startDate, String endDate) {
        Map<String, Object> params = buildBaseParams(topic, startDate, endDate);
        return client.callSass("content-classification", params);
    }

    public JsonNode geographicDistribution(String topic, String startDate, String endDate, int mapType) {
        Map<String, Object> params = buildBaseParams(topic, startDate, endDate);
        params.put("mapType", mapType);
        params.put("dataType", 0);
        return client.callSass("geographic-distribution", params);
    }

    public JsonNode callTool(String toolName, Map<String, Object> params) {
        return client.callSass(toolName, params);
    }
}
