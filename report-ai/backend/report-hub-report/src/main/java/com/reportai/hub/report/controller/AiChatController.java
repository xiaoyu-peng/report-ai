package com.reportai.hub.report.controller;

import com.reportai.hub.common.Result;
import com.reportai.hub.common.llm.LlmClient;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class AiChatController {

    private final LlmClient llmClient;

    @PostMapping("/ai-chat")
    public Result<Map<String, String>> aiChat(@RequestBody AiChatRequest request) {
        try {
            StringBuilder systemBuilder = new StringBuilder();
            systemBuilder.append("你是一个专业的报告写作助手。请根据用户的需求提供帮助。\n");
            if (request.getContext() != null && !request.getContext().isBlank()) {
                systemBuilder.append("当前报告内容摘要：\n");
                String ctx = request.getContext();
                if (ctx.length() > 2000) {
                    ctx = ctx.substring(0, 2000) + "...";
                }
                systemBuilder.append(ctx).append("\n");
            }
            systemBuilder.append("请用中文回复，保持专业和简洁。");

            StringBuilder userBuilder = new StringBuilder();
            if (request.getMessages() != null) {
                for (Map<String, String> msg : request.getMessages()) {
                    String role = msg.getOrDefault("role", "user");
                    String content = msg.getOrDefault("content", "");
                    if ("user".equals(role)) {
                        userBuilder.append("用户：").append(content).append("\n");
                    } else {
                        userBuilder.append("助手：").append(content).append("\n");
                    }
                }
            }

            String reply = llmClient.complete(systemBuilder.toString(), userBuilder.toString());
            return Result.success(Map.of("content", reply));
        } catch (Exception e) {
            log.error("AI Chat failed", e);
            return Result.success(Map.of("content", "AI 助手暂时不可用，请稍后再试。"));
        }
    }

    @Data
    public static class AiChatRequest {
        private List<Map<String, String>> messages;
        private String context;
    }
}
