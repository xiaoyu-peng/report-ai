package com.reportai.hub.report.service;

import com.reportai.hub.report.dto.RewriteMode;
import com.reportai.hub.report.entity.ReportVersion;

import java.util.function.Consumer;

public interface RewriteService {

    /**
     * 对已有 report 执行一次改写：
     *   1. 取 report 最新版本正文作为原稿；
     *   2. 按 mode 组 prompt 调 LLM stream；
     *   3. onToken 实时回调；结束后新写 report_version（versionNum+1，sourceMode=rewrite_xxx）；
     *   4. 主表 report.content 更新为最新；
     */
    ReportVersion streamRewrite(Long reportId,
                                RewriteMode mode,
                                String instruction,
                                Long operatorId,
                                Consumer<String> onToken,
                                Runnable onDone);
}
