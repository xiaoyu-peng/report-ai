package com.reportai.hub.report.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RewriteRequest {
    @NotNull
    private RewriteMode mode;
    /** 额外指示，例：数据更新时给"最新数据"，风格转换时给"目标语言 en"。 */
    private String instruction;
}
