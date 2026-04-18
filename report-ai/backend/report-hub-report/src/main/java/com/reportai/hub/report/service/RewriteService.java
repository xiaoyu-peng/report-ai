package com.reportai.hub.report.service;

import com.reportai.hub.report.dto.RewriteMode;
import com.reportai.hub.report.entity.ReportVersion;

import java.util.function.Consumer;

public interface RewriteService {

    ReportVersion streamRewrite(Long reportId,
                                RewriteMode mode,
                                String instruction,
                                Long operatorId,
                                Consumer<String> onToken,
                                Runnable onDone);

    void streamRewriteSection(Long reportId,
                              String sectionContent,
                              String mode,
                              String instruction,
                              Long operatorId,
                              Consumer<String> onToken,
                              Runnable onDone);
}
