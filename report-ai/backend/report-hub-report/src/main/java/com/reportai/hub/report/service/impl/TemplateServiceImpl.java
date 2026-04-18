package com.reportai.hub.report.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reportai.hub.common.exception.BusinessException;
import com.reportai.hub.report.entity.ReportTemplate;
import com.reportai.hub.common.llm.LlmClient;
import com.reportai.hub.report.mapper.ReportTemplateMapper;
import com.reportai.hub.report.prompt.Prompts;
import com.reportai.hub.report.service.TemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateServiceImpl extends ServiceImpl<ReportTemplateMapper, ReportTemplate>
        implements TemplateService {

    private static final int MAX_INPUT_CHARS = 10_000;

    private final LlmClient llmClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public ReportTemplate analyzeAndSave(String name, String description,
                                         String content, Long operatorId) {
        if (content == null || content.isBlank()) {
            throw new BusinessException("模板正文不能为空");
        }
        String trimmed = content.length() > MAX_INPUT_CHARS
                ? content.substring(0, MAX_INPUT_CHARS) : content;
        String styleJson = llmClient.complete(Prompts.STYLE_ANALYSIS_SYSTEM, trimmed);

        String sanitized = sanitizeJson(styleJson);
        String friendly = deriveFriendlyDescription(sanitized);

        ReportTemplate t = new ReportTemplate();
        t.setName(name);
        t.setDescription(description);
        t.setContent(content);
        t.setStyleDescription(friendly);
        t.setStructureJson(sanitized);
        t.setIsBuiltin(0);
        t.setCreatedBy(operatorId);
        save(t);
        return t;
    }

    @Override
    public ReportTemplate reanalyze(Long templateId) {
        ReportTemplate t = getById(templateId);
        if (t == null) throw new BusinessException("模板不存在");
        String content = t.getContent();
        if (content == null || content.isBlank()) {
            throw new BusinessException("模板没有保存正文，无法重新分析");
        }
        String trimmed = content.length() > MAX_INPUT_CHARS
                ? content.substring(0, MAX_INPUT_CHARS) : content;
        String styleJson = llmClient.complete(Prompts.STYLE_ANALYSIS_SYSTEM, trimmed);
        String sanitized = sanitizeJson(styleJson);
        t.setStyleDescription(deriveFriendlyDescription(sanitized));
        t.setStructureJson(sanitized);
        updateById(t);
        return t;
    }

    @Override
    public Page<ReportTemplate> listByPage(long current, long size, String keyword) {
        return page(new Page<>(current, size),
                new LambdaQueryWrapper<ReportTemplate>()
                        .like(keyword != null && !keyword.isBlank(),
                                ReportTemplate::getName, keyword)
                        .orderByDesc(ReportTemplate::getIsBuiltin)
                        .orderByDesc(ReportTemplate::getCreatedAt));
    }

    /** LLM 偶尔会带 ```json ... ``` 代码块；剥壳后确认能 parse 成 JSON，否则原样保存。 */
    private String sanitizeJson(String raw) {
        if (raw == null) return "{}";
        String s = raw.trim();
        if (s.startsWith("```")) {
            int first = s.indexOf('\n');
            int last = s.lastIndexOf("```");
            if (first > 0 && last > first) s = s.substring(first + 1, last).trim();
        }
        try {
            mapper.readTree(s);
            return s;
        } catch (Exception e) {
            log.warn("LLM 返回非 JSON，已原样保存：{}", raw.substring(0, Math.min(100, raw.length())));
            return "{\"raw\":" + mapper.valueToTree(raw) + "}";
        }
    }

    /** 从结构化 JSON 派生一句人类可读的 description，UI 展示用。 */
    private String deriveFriendlyDescription(String json) {
        try {
            JsonNode node = mapper.readTree(json);
            String type = node.path("report_type").asText("未识别");
            String tone = node.path("tone").asText("");
            String density = node.path("data_density").asText("");
            return "%s · 语气%s · 数据密度%s".formatted(type, tone, density);
        } catch (Exception e) {
            return "风格分析已记录";
        }
    }
}
