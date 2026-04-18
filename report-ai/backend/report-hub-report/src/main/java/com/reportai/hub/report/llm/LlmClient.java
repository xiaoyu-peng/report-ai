package com.reportai.hub.report.llm;

import java.util.function.Consumer;

/**
 * LLM 抽象。支持一次性调用（用于风格分析 / diff 摘要）和流式调用（用于报告生成与改写）。
 *
 * <p>同时准备好 Claude 和豆包两个 backend —— 赛场上谁的 key 可用就切换谁，
 * 实现切换不改业务代码。
 */
public interface LlmClient {

    /** 一次性同步调用，返回完整文本（非流）。 */
    String complete(String system, String user);

    /**
     * 流式调用，onToken 在每个增量 token 到达时回调；onDone 在完成时触发。
     * 实现必须阻塞到流结束或异常。
     */
    void stream(String system, String user, Consumer<String> onToken, Runnable onDone);

    /** 仅用于日志 / metrics：返回当前 provider 标识（例：claude-sonnet-4.6 / doubao-pro）。 */
    String providerName();
}
