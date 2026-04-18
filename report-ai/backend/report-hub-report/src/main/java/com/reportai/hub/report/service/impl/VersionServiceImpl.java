package com.reportai.hub.report.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reportai.hub.common.exception.BusinessException;
import com.reportai.hub.report.dto.DiffResult;
import com.reportai.hub.report.entity.Report;
import com.reportai.hub.report.entity.ReportVersion;
import com.reportai.hub.report.mapper.ReportMapper;
import com.reportai.hub.report.mapper.ReportVersionMapper;
import com.reportai.hub.report.service.VersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VersionServiceImpl implements VersionService {

    private final ReportVersionMapper versionMapper;
    private final ReportMapper reportMapper;

    @Override
    public List<ReportVersion> listVersions(Long reportId) {
        return versionMapper.selectList(
                new LambdaQueryWrapper<ReportVersion>()
                        .eq(ReportVersion::getReportId, reportId)
                        .orderByAsc(ReportVersion::getVersionNum));
    }

    @Override
    public ReportVersion getVersion(Long reportId, int versionNum) {
        return versionMapper.selectOne(
                new LambdaQueryWrapper<ReportVersion>()
                        .eq(ReportVersion::getReportId, reportId)
                        .eq(ReportVersion::getVersionNum, versionNum));
    }

    @Override
    public DiffResult diff(Long reportId, int fromVersion, int toVersion) {
        ReportVersion a = getVersion(reportId, fromVersion);
        ReportVersion b = getVersion(reportId, toVersion);
        if (a == null || b == null) throw new BusinessException("版本不存在");

        List<DiffResult.DiffLine> lines = computeLineDiff(
                nz(a.getContent()), nz(b.getContent()));

        int ins = 0, del = 0, rep = 0;
        for (DiffResult.DiffLine l : lines) {
            switch (l.getOp()) {
                case INSERT -> ins++;
                case DELETE -> del++;
                case REPLACE -> rep++;
                default -> {}
            }
        }

        DiffResult r = new DiffResult();
        r.setReportId(reportId);
        r.setFromVersion(fromVersion);
        r.setToVersion(toVersion);
        r.setInserts(ins);
        r.setDeletes(del);
        r.setReplaces(rep);
        r.setLines(lines);
        return r;
    }

    @Override
    public ReportVersion restore(Long reportId, int targetVersion, Long operatorId) {
        ReportVersion target = getVersion(reportId, targetVersion);
        if (target == null) throw new BusinessException("目标版本不存在");

        ReportVersion latest = versionMapper.selectOne(
                new LambdaQueryWrapper<ReportVersion>()
                        .eq(ReportVersion::getReportId, reportId)
                        .orderByDesc(ReportVersion::getVersionNum)
                        .last("LIMIT 1"));
        int next = (latest == null ? 0 : latest.getVersionNum()) + 1;

        ReportVersion copy = new ReportVersion();
        copy.setReportId(reportId);
        copy.setVersionNum(next);
        copy.setTitle(target.getTitle());
        copy.setContent(target.getContent());
        copy.setSourceMode("restore_from_v" + targetVersion);
        copy.setWordCount(target.getWordCount());
        copy.setCreatedBy(operatorId);
        copy.setChangeSummary("从 v%d 回滚".formatted(targetVersion));
        versionMapper.insert(copy);

        Report report = reportMapper.selectById(reportId);
        if (report != null) {
            report.setContent(target.getContent());
            report.setWordCount(target.getWordCount());
            reportMapper.updateById(report);
        }
        return copy;
    }

    // ---------- LCS-based line diff（朴素实现，对 200 行内正文足够）----------

    private List<DiffResult.DiffLine> computeLineDiff(String oldText, String newText) {
        String[] a = splitLines(oldText);
        String[] b = splitLines(newText);
        int n = a.length, m = b.length;
        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                lcs[i][j] = a[i].equals(b[j])
                        ? lcs[i + 1][j + 1] + 1
                        : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }
        List<DiffResult.DiffLine> out = new ArrayList<>();
        int i = 0, j = 0;
        while (i < n && j < m) {
            if (a[i].equals(b[j])) {
                out.add(line(DiffResult.Op.EQUAL, a[i], b[j]));
                i++; j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                // 看下一步是 delete 还是 replace（若紧接着是 insert，合并为 replace）
                if (i + 1 <= n && j < m && !a[i].equals(b[j])
                        && (i + 1 == n || !a[i + 1].equals(b[j]))) {
                    // 若 newline 也要前进，合并为 replace
                    if (j + 1 <= m && a[i].equals(b[j + 1])) {
                        out.add(line(DiffResult.Op.INSERT, null, b[j]));
                        j++;
                    } else {
                        out.add(line(DiffResult.Op.DELETE, a[i], null));
                        i++;
                    }
                } else {
                    out.add(line(DiffResult.Op.DELETE, a[i], null));
                    i++;
                }
            } else {
                out.add(line(DiffResult.Op.INSERT, null, b[j]));
                j++;
            }
        }
        while (i < n) out.add(line(DiffResult.Op.DELETE, a[i++], null));
        while (j < m) out.add(line(DiffResult.Op.INSERT, null, b[j++]));

        // 合并相邻 DELETE + INSERT 为 REPLACE 以便前端高亮
        return mergeReplaces(out);
    }

    private List<DiffResult.DiffLine> mergeReplaces(List<DiffResult.DiffLine> in) {
        List<DiffResult.DiffLine> out = new ArrayList<>(in.size());
        for (int k = 0; k < in.size(); k++) {
            DiffResult.DiffLine cur = in.get(k);
            DiffResult.DiffLine next = k + 1 < in.size() ? in.get(k + 1) : null;
            if (cur.getOp() == DiffResult.Op.DELETE
                    && next != null && next.getOp() == DiffResult.Op.INSERT) {
                DiffResult.DiffLine rep = new DiffResult.DiffLine();
                rep.setOp(DiffResult.Op.REPLACE);
                rep.setOldLine(cur.getOldLine());
                rep.setNewLine(next.getNewLine());
                out.add(rep);
                k++;
            } else {
                out.add(cur);
            }
        }
        return out;
    }

    private DiffResult.DiffLine line(DiffResult.Op op, String oldL, String newL) {
        DiffResult.DiffLine l = new DiffResult.DiffLine();
        l.setOp(op);
        l.setOldLine(oldL);
        l.setNewLine(newL);
        return l;
    }

    private String[] splitLines(String s) {
        return s == null ? new String[0] : s.split("\\r?\\n", -1);
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }
}
