package com.reportai.hub.report.llm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "report-ai.llm")
public class LlmProperties {

    /** claude | doubao */
    private String provider = "claude";

    private Claude claude = new Claude();

    @Data
    public static class Claude {
        private String baseUrl = "https://api.anthropic.com";
        private String apiKey;
        private String model = "claude-sonnet-4-6";
        private int maxTokens = 4096;
        private int timeoutSeconds = 120;
    }
}
