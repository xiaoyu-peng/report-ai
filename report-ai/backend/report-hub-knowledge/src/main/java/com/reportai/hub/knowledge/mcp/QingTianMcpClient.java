package com.reportai.hub.knowledge.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class QingTianMcpClient {

    @Value("${qingtian.mcp.appkey:}")
    private String appkey;

    @Value("${qingtian.mcp.search-url:https://api-sc.wengegroup.com/search-mcp}")
    private String searchUrl;

    @Value("${qingtian.mcp.sass-url:https://api-sc.wengegroup.com/sass-mcp}")
    private String sassUrl;

    // 调低到演示安全区间：connect 3s + read 8s。
    // 背景：SSE 生成管线里可能串联 4~9 次 MCP 调用，30s 读超时累积到分钟级，评委席会以为页面卡死。
    @Value("${qingtian.mcp.connect-timeout:3}")
    private int connectTimeoutSeconds;

    @Value("${qingtian.mcp.read-timeout:8}")
    private int readTimeoutSeconds;

    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicLong idCounter = new AtomicLong(System.nanoTime());
    private RestTemplate restTemplate;

    @PostConstruct
    void init() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        this.restTemplate = new RestTemplate(factory);
        log.info("QingTianMcpClient initialized: connectTimeout={}s, readTimeout={}s",
                connectTimeoutSeconds, readTimeoutSeconds);
    }

    public JsonNode callSearch(String method, Map<String, Object> params) {
        return callMcp(searchUrl, method, params);
    }

    public JsonNode callSass(String method, Map<String, Object> params) {
        return callMcp(sassUrl, method, params);
    }

    private JsonNode callMcp(String baseUrl, String method, Map<String, Object> params) {
        try {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("jsonrpc", "2.0");
            request.put("id", idCounter.getAndIncrement());
            request.put("method", "tools/call");
            Map<String, Object> mcpParams = new LinkedHashMap<>();
            mcpParams.put("name", method);
            mcpParams.put("arguments", params != null ? params : Map.of());
            request.put("params", mcpParams);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("appkey", appkey);
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

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
