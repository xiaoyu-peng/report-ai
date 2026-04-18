package com.reportai.hub.report.service;

import com.reportai.hub.report.dto.ReportCreateDTO;
import com.reportai.hub.report.entity.Report;

import java.util.function.Consumer;

public interface ReportGenerationService {

    /** 建草稿（状态 draft）。不生成正文，只分配 id 给前端，后续调 stream 出稿。 */
    Report createDraft(ReportCreateDTO dto, Long operatorId);

    /**
     * 流式生成一份完整报告：
     *   1. 用 topic+keyPoints 当 RAG query，取 top-K chunk；
     *   2. 如果 templateId != null，把 structureJson 注入 system prompt；
     *   3. 调 LLM stream，onToken 实时回调；
     *   4. 结束后把完整正文落 report.content，并写入 report_version v1。
     */
    void streamGenerate(Long reportId, Long operatorId,
                        Consumer<String> onToken, Runnable onDone);
}
