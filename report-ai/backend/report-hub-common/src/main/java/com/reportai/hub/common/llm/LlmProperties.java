package com.reportai.hub.common.llm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "report-ai.llm")
public class LlmProperties {

    /** claude | doubao */
    private String provider = "doubao";

    private Claude claude = new Claude();

    private Doubao doubao = new Doubao();

    @Data
    public static class Claude {
        private String baseUrl = "https://api.anthropic.com";
        private String apiKey;
        private String model = "claude-sonnet-4-6";
        private int maxTokens = 4096;
        private int timeoutSeconds = 300;
    }

    @Data
    public static class Doubao {
        private String baseUrl = "https://ark.cn-beijing.volces.com/api/v3";
        private String apiKey;
        /** Volcengine Ark 接入点 ID（ep-xxxxx），作为 ChatCompletionRequest.model 传入 */
        private String endpoint;
        /** 可选：模型名（如 doubao-seed-2-0-lite-260215），仅做记录，实际调用以 endpoint 为准 */
        private String model;
        private int maxTokens = 4096;
        private int timeoutSeconds = 300;
    }
}
