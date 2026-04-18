package com.reportai.hub.config;

import com.volcengine.ark.runtime.service.ArkService;
import com.wenge.xinghe.cloud.llm.api.utils.DoubaoClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 豆包大模型自动配置。
 *
 * <p>职责：
 * <ol>
 *   <li>创建全局唯一 {@link ArkService} Bean，所有模型共用连接池</li>
 *   <li>创建 {@link DoubaoClient} Bean，供业务模块注入使用</li>
 * </ol>
 *
 * <p>仅当 Nacos 中存在 {@code doubao.api-key} 配置时才激活，
 * 未配置该 key 的服务模块（如 ES、Iceberg）不受影响。
 */
@Configuration
@ConditionalOnProperty(prefix = "doubao", name = "api-key")
@EnableConfigurationProperties(DoubaoProperties.class)
public class DoubaoAutoConfiguration {

    /**
     * 全局唯一 ArkService 实例。
     * apiKey + baseUrl 是连接级参数，model 是请求级参数，因此一个实例可服务所有模型。
     * baseUrl 显式注入，不依赖 SDK 默认值，避免 SDK 升级引起隐式变更。
     */
    @Bean
    @RefreshScope
    public ArkService doubaoArkService(DoubaoProperties properties) {
        return ArkService.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(properties.getBaseUrl())
                .build();
    }

    @Bean
    public DoubaoClient doubaoClient(ArkService doubaoArkService, DoubaoProperties properties) {
        return new DoubaoClient(doubaoArkService, properties);
    }
}