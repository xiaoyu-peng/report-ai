package com.reportai.hub.common.llm;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 始终启用的 LLM 基础设施配置：只负责绑定 {@link LlmProperties}。
 * 具体 provider（claude / doubao）的客户端 Bean 由各自的
 * {@code @ConditionalOnProperty} 条件装配。
 */
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmAutoConfiguration {
}
