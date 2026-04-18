package com.reportai.hub.report.dto;

import lombok.Data;

import java.util.List;

/** 两版本 diff 结果：按行拆分 + 操作码 + 聚合统计。 */
@Data
public class DiffResult {

    public enum Op { EQUAL, INSERT, DELETE, REPLACE }

    @Data
    public static class DiffLine {
        private Op op;
        /** op=INSERT 时为空；op=DELETE/REPLACE 时为原行；EQUAL 为原行。 */
        private String oldLine;
        /** op=DELETE 时为空；op=INSERT/REPLACE 时为新行；EQUAL 为新行。 */
        private String newLine;
    }

    private Long reportId;
    private Integer fromVersion;
    private Integer toVersion;
    private Integer inserts;
    private Integer deletes;
    private Integer replaces;
    private List<DiffLine> lines;
}
