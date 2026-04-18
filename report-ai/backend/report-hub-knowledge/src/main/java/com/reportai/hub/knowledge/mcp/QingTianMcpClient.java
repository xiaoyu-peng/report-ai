package com.reportai.hub.knowledge.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class QingTianMcpClient {

    @Value("${qingtian.mcp.appkey:OOn05z7m}")
    private String appkey;

    @Value("${qingtian.mcp.search-url:https://api-sc.wengegroup.com/search-mcp}")
    private String searchUrl;

    @Value("${qingtian.mcp.sass-url:https://api-sc.wengegroup.com/sass-mcp}")
    private String sassUrl;

    @Value("${qingtian.mcp.connect-timeout:5}")
    private int connectTimeoutSeconds;

    @Value("${qingtian.mcp.read-timeout:30}")
    private int readTimeoutSeconds;

    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicLong idCounter = new AtomicLong(1);
    private final RestTemplate restTemplate = new RestTemplate();

    public JsonNode callSearch(String method, Map<String, Object> params) {
        return callMcp(searchUrl, method, params);
    }

    public JsonNode callSass(String method, Map<String, Object> params) {
        return callMcp(sassUrl, method, params);
    }

    private JsonNode callMcp(String baseUrl, String method, Map<String, Object> params) {
        try {
            Map<String, Object> request = Map.of(
                    "jsonrpc", "2.0",
                    "id", idCounter.getAndIncrement(),
                    "method", "tools/call",
                    "params", Map.of(
                            "name", method,
                            "arguments", params != null ? params : Map.of()
                    )
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("appkey", appkey);
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));

            HttpEntity<String> entity = new HttpEntity<>(mapper.writeValueAsString(request), headers);
            ResponseEntity<String> response = restTemplate.exchange(baseUrl, HttpMethod.POST, entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("MCP call failed: {} -> status {}", method, response.getStatusCode());
                return null;
            }

            JsonNode root = mapper.readTree(response.getBody());
            if (root.has("error")) {
                log.warn("MCP error for {}: {}", method, root.get("error"));
                return null;
            }

            JsonNode result = root.get("result");
            if (result != null && result.has("content")) {
                JsonNode content = result.get("content");
                if (content.isArray() && content.size() > 0) {
                    JsonNode first = content.get(0);
                    if (first.has("text")) {
                        String text = first.get("text").asText();
                        try {
                            return mapper.readTree(text);
                        } catch (Exception e) {
                            return mapper.getNodeFactory().textNode(text);
                        }
                    }
                }
                return content;
            }
            return result;
        } catch (Exception e) {
            log.error("MCP call exception for {}: {}", method, e.getMessage());
            return null;
        }
    }
}
