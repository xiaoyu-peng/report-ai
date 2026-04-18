package com.reportai.hub.common.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.reportai.hub.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * Anthropic Messages API 调用（支持 SSE 流式）。
 *
 * <p>协议参考：https://docs.anthropic.com/en/api/messages-streaming
 * <p>为避免引入 anthropic-sdk 版本绑定，这里直接走 JDK HttpClient + Jackson 解析 SSE。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "report-ai.llm", name = "provider", havingValue = "claude")
public class ClaudeLlmClient implements LlmClient {

    private final LlmProperties props;
    private final ObjectMapper mapper = new ObjectMapper();

    private HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override
    public String providerName() {
        return "claude:" + props.getClaude().getModel();
    }

    @Override
    public String complete(String system, String user) {
        try {
            ObjectNode body = buildBody(system, user, false);
            HttpRequest req = baseRequest()
                    .POST(HttpRequest.BodyPublishers.ofString(
                            mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = httpClient().send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new BusinessException("Claude API 错误 " + resp.statusCode() + ": " + resp.body());
            }
            JsonNode root = mapper.readTree(resp.body());
            // Messages API 的一次性响应：content[0].text
            return root.path("content").path(0).path("text").asText();
        } catch (Exception e) {
            if (e instanceof BusinessException be) throw be;
            throw new BusinessException("Claude complete 失败: " + e.getMessage());
        }
    }

    @Override
    public void stream(String system, String user, Consumer<String> onToken, Runnable onDone) {
        try {
            ObjectNode body = buildBody(system, user, true);
            HttpRequest req = baseRequest()
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<java.io.InputStream> resp = httpClient()
                    .send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() / 100 != 2) {
                String err = new String(resp.body().readAllBytes(), StandardCharsets.UTF_8);
                throw new BusinessException("Claude SSE 错误 " + resp.statusCode() + ": " + err);
            }
            parseSse(resp.body(), onToken);
            onDone.run();
        } catch (Exception e) {
            if (e instanceof BusinessException be) throw be;
            throw new BusinessException("Claude stream 失败: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------

    private HttpRequest.Builder baseRequest() {
        var c = props.getClaude();
        return HttpRequest.newBuilder()
                .uri(URI.create(c.getBaseUrl() + "/v1/messages"))
                .timeout(Duration.ofSeconds(c.getTimeoutSeconds()))
                .header("x-api-key", c.getApiKey() == null ? "" : c.getApiKey())
                .header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json");
    }

    private ObjectNode buildBody(String system, String user, boolean stream) {
        var c = props.getClaude();
        ObjectNode body = mapper.createObjectNode();
        body.put("model", c.getModel());
        body.put("max_tokens", c.getMaxTokens());
        if (stream) body.put("stream", true);
        if (system != null && !system.isBlank()) {
            body.put("system", system);
        }
        ArrayNode messages = body.putArray("messages");
        ObjectNode msg = messages.addObject();
        msg.put("role", "user");
        msg.put("content", user);
        return body;
    }

    /**
     * 解析 SSE 流。仅关心 `event: content_block_delta` 的 `text_delta`，
     * 其它事件（message_start、content_block_stop 等）忽略。
     */
    private void parseSse(java.io.InputStream in, Consumer<String> onToken) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || !line.startsWith("data:")) continue;
                String data = line.substring(5).trim();
                if (data.equals("[DONE]")) break;
                try {
                    JsonNode node = mapper.readTree(data);
                    String type = node.path("type").asText();
                    if ("content_block_delta".equals(type)) {
                        String delta = node.path("delta").path("text").asText("");
                        if (!delta.isEmpty()) onToken.accept(delta);
                    } else if ("message_stop".equals(type)) {
                        break;
                    }
                } catch (Exception parseErr) {
                    log.debug("skip malformed SSE frame: {}", data);
                }
            }
        }
    }
}
