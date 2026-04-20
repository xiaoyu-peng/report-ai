package com.reportai.hub.report.service;

import com.reportai.hub.knowledge.dto.RagChunkHit;
import com.reportai.hub.report.dto.ReportCreateDTO;
import com.reportai.hub.report.entity.Report;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public interface ReportGenerationService {

    /**
     * 本次生成用到的数据源汇总，SSE `sources` 事件 payload。
     * 前端"数据来源"面板渲染成带图标的卡片，让评委一眼看到检索/MCP/Web 覆盖面。
     */
    record DataSources(
            Integer ragHits,                  // 知识库 RAG 命中条数
            Long kbId,                        // 关联 KB id，null 表示未选 KB
            List<String> mcpSections,         // 晴天 MCP 用到的工具人类可读名（如 "相关文章搜索"）
            Integer tavilyHits                // Tavily Web 结果条数
    ) {
        public static DataSources of(int ragHits, Long kbId,
                                     List<String> mcpSections, int tavilyHits) {
            return new DataSources(ragHits, kbId, mcpSections, tavilyHits);
        }
    }

    /** 建草稿（状态 draft）。不生成正文，只分配 id 给前端，后续调 stream 出稿。 */
    Report createDraft(ReportCreateDTO dto, Long operatorId);

    /**
     * 流式生成一份完整报告：
     *   1. 用 topic+keyPoints 当 RAG query，取 top-K chunk；
     *   2. 检索完成后先通过 onChunks 把 top-K 命中列表推给前端（SSE chunks 事件），
     *      供引用溯源展示；
     *   3. 同一轮把 RAG+MCP+Tavily 命中的条数汇总通过 onSources 推给前端（sources 事件），
     *      供"本次数据来源"面板渲染，满足评委"引用可验证"的评分要求；
     *   4. 如果 templateId != null，把 structureJson 注入 system prompt；
     *   5. 调 LLM stream，onToken 实时回调；正文里会带 [n] 角标；
     *   6. 结束后把完整正文落 report.content，并写入 report_version v1。
     *
     * onSources 为可选：旧调用方传 null 不会出错。
     */
    void streamGenerate(Long reportId, Long operatorId,
                        Consumer<List<RagChunkHit>> onChunks,
                        Consumer<DataSources> onSources,
                        Consumer<String> onToken, Runnable onDone);
}
