package com.reportai.hub.utils;

import com.beust.jcommander.internal.Lists;
import com.volcengine.ark.runtime.model.completion.chat.*;
import com.volcengine.ark.runtime.model.responses.request.CreateResponsesRequest;
import com.volcengine.ark.runtime.model.responses.request.ResponsesInput;
import com.volcengine.ark.runtime.model.responses.response.ResponseObject;
import com.volcengine.ark.runtime.model.responses.tool.ResponsesTool;
import com.volcengine.ark.runtime.model.responses.tool.ToolWebSearch;
import com.volcengine.ark.runtime.service.ArkService;
import lombok.Getter;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 豆包对话模型 Ark Chat API 客户端工具类，封装了与大模型交互的常用方法
 * 参考文档：https://www.volcengine.com/docs/82379/1302010
 */
public class ArkClient {
    private static final Map<String, ArkClient> instances = new ConcurrentHashMap<>();

    /** 关闭 thinking 的参数常量，避免每次调用 chatTextNoThinking 时重复分配对象 */
    private static final Map<String, Object> THINKING_DISABLED_PARAMS = Collections.singletonMap(
            "thinking", new ChatCompletionRequest.ChatCompletionRequestThinking("disabled"));

    private final ArkService service;

    @Getter
    private final String endpoint;

    @Getter
    private final String apiKey;


    /**
     * 获取单例实例，指定 API Key 和模型
     */
    public static ArkClient getInstance(String apiKey, String endpoint) {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("API Key cannot be null or empty");
        }
        if (endpoint == null || endpoint.isEmpty()) {
            throw new IllegalArgumentException("Model cannot be null or empty");
        }

        String key = apiKey + ":" + endpoint;
        return instances.computeIfAbsent(key, k -> new ArkClient(apiKey, endpoint));
    }

    /**
     * 清除所有实例
     */
    public static void clearInstances() {
        instances.values().forEach(ArkClient::shutdown);
        instances.clear();
    }

    /**
     * 构建一个默认配置的 ArkChatClient 实例
     */
    private ArkClient(String apiKey, String endpoint) {
        this(apiKey, endpoint, new Dispatcher(), new ConnectionPool());
    }

    /**
     * 构建一个自定义配置的 ArkChatClient 实例
     */
    private ArkClient(String apiKey, String endpoint, Dispatcher dispatcher,
                      ConnectionPool connectionPool) {
        this.apiKey = apiKey;
        this.endpoint = endpoint;
        this.service = ArkService.builder()
                .dispatcher(dispatcher)
                .connectionPool(connectionPool)
                .apiKey(apiKey)
                .build();
    }

    /**
     * 发送单轮对话请求
     */
    public ChatCompletionResult chat(String userMessage) {
        return chat(userMessage, null, null);
    }

    /**
     * 发送单轮对话请求，可指定系统消息
     */
    public ChatCompletionResult chat(String userMessage, String systemMessage) {
        return chat(userMessage, systemMessage, null);
    }

    /**
     * 发送单轮对话请求，可指定请求参数
     */
    public ChatCompletionResult chat(String userMessage, Map<String, Object> params) {
        return chat(userMessage, null, params);
    }

    /**
     * 发送单轮对话请求，可指定系统消息和请求参数
     */
    public ChatCompletionResult chat(String userMessage, String systemMessage, Map<String, Object> params) {
        List<ChatMessage> messages = new ArrayList<>();

        // 添加系统消息（如果有）
        if (systemMessage != null && !systemMessage.isEmpty()) {
            messages.add(ChatMessage.builder()
                    .role(ChatMessageRole.SYSTEM)
                    .content(systemMessage)
                    .build());
        }

        // 添加用户消息
        messages.add(ChatMessage.builder()
                .role(ChatMessageRole.USER)
                .content(userMessage)
                .build());

        return executeChatCompletion(messages, params);
    }

    /**
     * 发送多轮对话请求
     */
    public ChatCompletionResult chat(List<ChatMessage> messages) {
        return executeChatCompletion(messages, null);
    }

    /**
     * 发送多轮对话请求，可指定请求参数
     */
    public ChatCompletionResult chat(List<ChatMessage> messages, Map<String, Object> params) {
        return executeChatCompletion(messages, params);
    }

    /**
     * 发送单轮对话，直接返回模型输出的纯文本（null-safe）。
     * 不设置 thinking 参数，由调用方通过 params 控制。
     *
     * @return 模型返回文本；调用失败或内容为空时返回 null
     */
    public String chatText(String prompt) {
        return chatText(prompt, null);
    }

    /**
     * 发送单轮对话（带参数），直接返回模型输出的纯文本（null-safe）。
     *
     * @return 模型返回文本；调用失败或内容为空时返回 null
     */
    public String chatText(String prompt, Map<String, Object> params) {
        return chatText(prompt, null, params);
    }

    /**
     * 发送单轮对话（带系统消息和参数），直接返回模型输出的纯文本（null-safe）。
     *
     * @param systemMessage 系统消息，为 null 时不添加
     * @return 模型返回文本；调用失败或内容为空时返回 null
     */
    public String chatText(String prompt, String systemMessage, Map<String, Object> params) {
        ChatCompletionResult result = chat(prompt, systemMessage, params);
        if (result == null || result.getChoices() == null || result.getChoices().isEmpty()) {
            return null;
        }
        ChatCompletionChoice choice = result.getChoices().get(0);
        if (choice.getMessage() == null || choice.getMessage().getContent() == null) {
            return null;
        }
        return choice.getMessage().getContent().toString();
    }

    /**
     * 关闭 thinking 的快捷调用，适用于结构化提取、分类、翻译等对延迟敏感的任务。
     * 关闭 thinking 可将响应时间从 ~100s 降至 ~10s。
     *
     * @return 模型返回文本；调用失败或内容为空时返回 null
     */
    public String chatTextNoThinking(String prompt) {
        return chatText(prompt, THINKING_DISABLED_PARAMS);
    }

    /**
     * 执行对话请求并返回结果
     */
    private ChatCompletionResult executeChatCompletion(List<ChatMessage> messages, Map<String, Object> params) {
        ChatCompletionRequest.Builder requestBuilder = ChatCompletionRequest.builder()
                .model(endpoint)
                .messages(messages);


        // 应用自定义参数（覆盖默认参数）
        if (params != null && !params.isEmpty()) {
            applyChatParams(requestBuilder, params);
        }
        Map<String, String> headers = new HashMap<>();
        headers.put("x-ark-moderation-scene", "skip-ark-moderation");
        return service.createChatCompletion(requestBuilder.build(), headers);
    }


    public ResponseObject createResponse(String userMessage) {
        return createResponse(userMessage, null);
    }

    public ResponseObject createResponseWithWebSearch(String userMessage) {
        Map<String, Object> llmParams = new HashMap<>();
        // 必须用 Builder 构造，否则 type 字段不会被 SDK 序列化
        llmParams.put("tools", Lists.newArrayList(ToolWebSearch.builder().build()));
        return createResponse(userMessage, llmParams);
    }

    public ResponseObject createResponse(String userMessage , Map<String, Object> params) {
        ResponsesInput.Builder inputBuilder = ResponsesInput.builder()
                .stringValue(userMessage);
        return executeCreateResponse(inputBuilder.build(), params);
    }



    private ResponseObject executeCreateResponse(ResponsesInput input, Map<String, Object> params){
        CreateResponsesRequest.Builder requestBuilder = CreateResponsesRequest.builder()
                .model(endpoint)
                .input(input);
        // 应用自定义参数（覆盖默认参数）
        if (params != null && !params.isEmpty()) {
            applyCreateResponseParams(requestBuilder, params);
        }
        Map<String, String> headers = new HashMap<>();
        headers.put("x-ark-moderation-scene", "skip-ark-moderation");
        return service.createResponse(requestBuilder.build(), headers);
    }

    /**
     * 应用请求参数到构建器
     */
    private void applyCreateResponseParams(CreateResponsesRequest.Builder builder, Map<String, Object> params) {
        if (params.containsKey("temperature")) {
            builder.temperature((Double) params.get("temperature"));
        }
        if (params.containsKey("topP")) {
            builder.topP((Double) params.get("topP"));
        }
        if(params.containsKey("tools")){
            builder.tools((List<ResponsesTool>) params.get("tools"));
        }
        // 根据实际 SDK 添加更多参数...
    }

    /**
     * 应用请求参数到构建器
     */
    private void applyChatParams(ChatCompletionRequest.Builder builder, Map<String, Object> params) {
        if (params.containsKey("temperature")) {
            builder.temperature((Double) params.get("temperature"));
        }
        if (params.containsKey("topP")) {
            builder.topP((Double) params.get("topP"));
        }
        if (params.containsKey("n")) {
            builder.n((Integer) params.get("n"));
        }
        if (params.containsKey("maxTokens")) {
            builder.maxTokens((Integer) params.get("maxTokens"));
        }
        if (params.containsKey("presencePenalty")) {
            builder.presencePenalty((Double) params.get("presencePenalty"));
        }
        if (params.containsKey("frequencyPenalty")) {
            builder.frequencyPenalty((Double) params.get("frequencyPenalty"));
        }
        if (params.containsKey("stop")) {
            builder.stop((List<String>) params.get("stop"));
        }
        if (params.containsKey("thinking")) {
            builder.thinking((ChatCompletionRequest.ChatCompletionRequestThinking) params.get("thinking"));
        }
    }


    /**
     * 关闭客户端服务
     */
    public void shutdown() {
        service.shutdownExecutor();
        // 从实例缓存中移除
        String key = apiKey + ":" + endpoint;
        instances.remove(key);
    }

}
