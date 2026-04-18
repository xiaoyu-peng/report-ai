package com.reportai.hub.knowledge.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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

@Slf4j
@Component
public class TavilyClient {

    @Value("${tavily.api-key:}")
    private String apiKey;

    @Value("${tavily.base-url:https://api.tavily.com}")
    private String baseUrl;

    private final ObjectMapper mapper = new ObjectMapper();
    private RestTemplate restTemplate;

    @PostConstruct
    void init() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.restTemplate = new RestTemplate(factory);
        log.info("TavilyClient initialized: baseUrl={}", baseUrl);
    }

    public JsonNode search(String query, int maxResults) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("query", query);
            body.put("max_results", Math.min(maxResults, 10));
            body.put("include_answer", true);
            body.put("include_raw_content", false);
            body.put("search_depth", "advanced");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            HttpEntity<String> entity = new HttpEntity<>(mapper.writeValueAsString(body), headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + "/search", HttpMethod.POST, entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("Tavily search failed: status {}", response.getStatusCode());
                return null;
            }
            return mapper.readTree(response.getBody());
        } catch (Exception e) {
            log.error("Tavily search exception: {}", e.getMessage());
            return null;
        }
    }

    public JsonNode extract(String url) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("urls", java.util.List.of(url));
            body.put("extract_depth", "advanced");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            HttpEntity<String> entity = new HttpEntity<>(mapper.writeValueAsString(body), headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + "/extract", HttpMethod.POST, entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("Tavily extract failed: status {}", response.getStatusCode());
                return null;
            }
            return mapper.readTree(response.getBody());
        } catch (Exception e) {
            log.error("Tavily extract exception: {}", e.getMessage());
            return null;
        }
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
