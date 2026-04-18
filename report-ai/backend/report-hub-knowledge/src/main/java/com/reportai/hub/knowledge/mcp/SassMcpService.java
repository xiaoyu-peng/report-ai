package com.reportai.hub.knowledge.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SassMcpService {

    private final QingTianMcpClient client;

    public JsonNode overview(String topic, String startDate, String endDate) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("topic", topic);
        if (startDate != null) params.put("startDate", startDate);
        if (endDate != null) params.put("endDate", endDate);
        return client.callSass("overview", params);
    }

    public JsonNode hotArticle(String topic, String startDate, String endDate, int topN) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("topic", topic);
        if (startDate != null) params.put("startDate", startDate);
        if (endDate != null) params.put("endDate", endDate);
        if (topN > 0) params.put("topN", topN);
        return client.callSass("hot-article", params);
    }

    public JsonNode emotionalDistribution(String topic, String startDate, String endDate) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("topic", topic);
        if (startDate != null) params.put("startDate", startDate);
        if (endDate != null) params.put("endDate", endDate);
        return client.callSass("emotional-distribution", params);
    }

    public JsonNode datasourceSound(String topic, String startDate, String endDate) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("topic", topic);
        if (startDate != null) params.put("startDate", startDate);
        if (endDate != null) params.put("endDate", endDate);
        return client.callSass("datasourceSound", params);
    }

    public JsonNode stageEnvolution(String topic, String startDate, String endDate) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("topic", topic);
        if (startDate != null) params.put("startDate", startDate);
        if (endDate != null) params.put("endDate", endDate);
        return client.callSass("stage-envolution", params);
    }

    public JsonNode generateEventTopicInfo(String topic, String startDate, String endDate) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("topic", topic);
        if (startDate != null) params.put("startDate", startDate);
        if (endDate != null) params.put("endDate", endDate);
        return client.callSass("generate-event-topic-info", params);
    }

    public JsonNode hotWords(String topic, String startDate, String endDate) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("topic", topic);
        if (startDate != null) params.put("startDate", startDate);
        if (endDate != null) params.put("endDate", endDate);
        return client.callSass("hot-words", params);
    }

    public JsonNode callTool(String toolName, Map<String, Object> params) {
        return client.callSass(toolName, params);
    }
}
