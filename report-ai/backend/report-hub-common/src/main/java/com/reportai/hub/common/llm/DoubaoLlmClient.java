package com.reportai.hub.common.llm;

import com.reportai.hub.common.exception.BusinessException;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionChunk;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionResult;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import com.volcengine.ark.runtime.service.ArkService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 豆包 / Volcengine Ark 大模型调用。
 *
 * <p>协议走火山引擎 Ark Chat Completions（OpenAI 兼容，走 {@code /chat/completions}），
 * 调用时 {@code model} 字段传 <b>接入点 ID</b>（ep-xxxxx），而不是模型名——
 * 接入点与账号配额、模型版本一一绑定。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "report-ai.llm", name = "provider", havingValue = "doubao")
public class DoubaoLlmClient implements LlmClient {

    private final LlmProperties props;
    private final ArkService ark;

    public DoubaoLlmClient(LlmProperties props) {
        this.props = props;
        LlmProperties.Doubao c = props.getDoubao();
        this.ark = ArkService.builder()
                .apiKey(c.getApiKey() == null ? "" : c.getApiKey())
                .baseUrl(c.getBaseUrl())
                .timeout(Duration.ofSeconds(c.getTimeoutSeconds()))
                .build();
    }

    @PreDestroy
    public void shutdown() {
        try {
            ark.shutdownExecutor();
        } catch (Exception ignored) {
            // best-effort close
        }
    }

    @Override
    public String providerName() {
        LlmProperties.Doubao c = props.getDoubao();
        return "doubao:" + (c.getModel() == null ? c.getEndpoint() : c.getModel());
    }

    @Override
    public String complete(String system, String user) {
        try {
            ChatCompletionResult resp = ark.createChatCompletion(buildRequest(system, user, false));
            if (resp.getChoices() == null || resp.getChoices().isEmpty()) {
                throw new BusinessException("Doubao 返回空 choices");
            }
            ChatMessage msg = resp.getChoices().get(0).getMessage();
            return msg == null ? "" : msg.stringContent();
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new BusinessException("Doubao complete 失败: " + e.getMessage());
        }
    }

    @Override
    public void stream(String system, String user, Consumer<String> onToken, Runnable onDone) {
        try {
            ark.streamChatCompletion(buildRequest(system, user, true))
                    .blockingForEach(chunk -> {
                        String delta = extractDelta(chunk);
                        if (delta != null && !delta.isEmpty()) {
                            onToken.accept(delta);
                        }
                    });
            onDone.run();
        } catch (Exception e) {
            throw new BusinessException("Doubao stream 失败: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------

    private ChatCompletionRequest buildRequest(String system, String user, boolean stream) {
        LlmProperties.Doubao c = props.getDoubao();
        if (c.getEndpoint() == null || c.getEndpoint().isBlank()) {
            throw new BusinessException("未配置 report-ai.llm.doubao.endpoint");
        }
        List<ChatMessage> messages = new ArrayList<>();
        if (system != null && !system.isBlank()) {
            ChatMessage sys = new ChatMessage();
            sys.setRole(ChatMessageRole.SYSTEM);
            sys.setContent(system);
            messages.add(sys);
        }
        ChatMessage usr = new ChatMessage();
        usr.setRole(ChatMessageRole.USER);
        usr.setContent(user);
        messages.add(usr);

        return ChatCompletionRequest.builder()
                .model(c.getEndpoint())
                .messages(messages)
                .maxTokens(c.getMaxTokens())
                .stream(stream)
                .build();
    }

    private String extractDelta(ChatCompletionChunk chunk) {
        if (chunk.getChoices() == null || chunk.getChoices().isEmpty()) return null;
        ChatMessage msg = chunk.getChoices().get(0).getMessage();
        return msg == null ? null : msg.stringContent();
    }
}
