package com.reportai.hub.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.util.HashMap;
import java.util.Map;

/**
 * 豆包大模型统一配置。
 *
 * <p>对应 Nacos application.yml 中的 {@code doubao.*} 配置块，
 * 通过 {@code @ConfigurationProperties} 整体绑定，类型安全，启动即校验。
 *
 * <p>新增模型只需在 Nacos {@code doubao.models} 下追加配置，代码无需改动。
 * Map key 使用连字符（如 {@code seed-2-0}），避免 Spring Boot 点号解析歧义。
 */
@Data
@RefreshScope
@Validated
@ConfigurationProperties(prefix = "doubao")
public class DoubaoProperties {

    /** 豆包 API Key，对应 Nacos: doubao.api-key */
    @NotBlank
    private String apiKey;

    /** Ark 服务 baseUrl，对应 Nacos: doubao.base-url */
    @NotBlank
    private String baseUrl;

    /**
     * 默认模型 key，对应 {@code models} 中的 key 名称。
     * 业务层不指定 modelKey 时使用此项。
     */
    private String defaultModel = "seed-2-0";

    /** 模型配置列表，key 为模型别名（如 seed-2-0、pro-think） */
    @NotEmpty
    private Map<String, ModelConfig> models = new HashMap<>();

    @Data
    public static class ModelConfig {
        /**
         * 接入点 ID（ep-xxx），Chat Completions API 使用。
         * 与账号配额绑定，一个接入点对应一个模型版本。
         */
        @NotBlank
        private String endpoint;

        /**
         * 模型名称，Responses API 使用。
         * 与 endpoint 一一对应，两者须指向同一模型。
         */
        @NotBlank
        private String model;
    }

    /**
     * 按 key 获取模型配置，key 不存在时抛出明确异常，便于快速定位配置遗漏。
     */
    public ModelConfig getModelConfig(String modelKey) {
        ModelConfig config = models.get(modelKey);
        if (config == null) {
            throw new IllegalArgumentException("未知的模型 key: [" + modelKey + "]，当前可用: " + models.keySet());
        }
        return config;
    }

    public ModelConfig getDefaultModelConfig() {
        return getModelConfig(defaultModel);
    }
}