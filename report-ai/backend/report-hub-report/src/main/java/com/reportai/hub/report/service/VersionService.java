package com.reportai.hub.report.service;

import com.reportai.hub.report.dto.DiffResult;
import com.reportai.hub.report.entity.ReportVersion;

import java.util.List;

public interface VersionService {

    List<ReportVersion> listVersions(Long reportId);

    ReportVersion getVersion(Long reportId, int versionNum);

    DiffResult diff(Long reportId, int fromVersion, int toVersion);

    /** 回滚：把 targetVersion 的正文复制为新版本（不破坏历史）。 */
    ReportVersion restore(Long reportId, int targetVersion, Long operatorId);
}
