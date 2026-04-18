package com.reportai.hub.utils;

import com.volcengine.ark.runtime.model.responses.response.ResponseObject;
import com.volcengine.ark.runtime.service.ArkService;
import com.wenge.xinghe.cloud.llm.api.config.DoubaoProperties;
import com.wenge.xinghe.cloud.llm.api.config.DoubaoProperties.ModelConfig;
import com.wenge.xinghe.cloud.llm.api.model.llm.LlmThinkAndOutputResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.context.event.EventListener;

/**
 *  豆包大模型统一调用门面，业务层唯一入口。
 */
@Slf4j
@RefreshScope
@RequiredArgsConstructor
public class DoubaoClient {

    private final ArkService doubaoArkService;
    private final DoubaoProperties properties;

    /**
     * 快速调用（Chat Completions + thinking disabled）
     * 适合：情绪分类、内容审核、翻译、结构化 JSON 提取
     * @param prompt
     * @return
     */
    public String chatFast(String prompt) {
        return chatFast(prompt, properties.getDefaultModel());
    }

    /**
     * 深度思考（Responses API）
     * 适合：深度分析、报告生成、事件研判、脉络生成
     * @param prompt
     * @return
     */
    public LlmThinkAndOutputResultVO chatThink(String prompt) {
        return chatThink(prompt, properties.getDefaultModel());
    }

    /**
     * 联网搜索（Responses API + WebSearch）
     * 适合：值班快报、热点话题、实时事件
     * @param prompt
     * @return
     */
    public LlmThinkAndOutputResultVO chatWithWebSearch(String prompt) {
        return chatWithWebSearch(prompt, properties.getDefaultModel());
    }


    public String chatFast(String prompt, String modelKey) {
        ModelConfig config = properties.getModelConfig(modelKey);
        return getArkClient(config.getEndpoint()).chatTextNoThinking(prompt);
    }

    public LlmThinkAndOutputResultVO chatThink(String prompt, String modelKey) {
        ModelConfig config = properties.getModelConfig(modelKey);
        // Responses API 同样需要接入点 ID，而非模型名称
        ResponseObject resp = getArkClient(config.getEndpoint()).createResponse(prompt);
        return ArkLlmUtil.parseThinkAndOutputFromResponseObject(resp);
    }

    public LlmThinkAndOutputResultVO chatWithWebSearch(String prompt, String modelKey) {
        ModelConfig config = properties.getModelConfig(modelKey);
        // Responses API 同样需要接入点 ID，联网搜索需在接入点开启"联网搜索"插件
        ResponseObject resp = getArkClient(config.getEndpoint()).createResponseWithWebSearch(prompt);
        return ArkLlmUtil.parseThinkAndOutputFromResponseObject(resp);
    }

    private ArkClient getArkClient(String modelOrEndpoint) {
        return ArkClient.getInstance(properties.getApiKey(), modelOrEndpoint);
    }

    /**
     * Nacos 配置刷新后清理 ArkClient 连接池缓存，防止旧实例泄漏。
     * ArkService Bean 由 @RefreshScope 重建，ArkClient 静态缓存需手动清理。
     */
    @EventListener(RefreshScopeRefreshedEvent.class)
    public void onRefresh(RefreshScopeRefreshedEvent event) {
        log.info("[DoubaoClient] Nacos 配置刷新，清理 ArkClient 连接池缓存");
        ArkClient.clearInstances();
    }
}