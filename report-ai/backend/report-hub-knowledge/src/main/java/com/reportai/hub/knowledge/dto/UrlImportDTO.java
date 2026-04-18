package com.reportai.hub.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UrlImportDTO {
    @NotBlank
    @Pattern(regexp = "^https?://.+", message = "必须是 http:// 或 https:// 开头的 URL")
    private String url;

    /** 可选：自定义文件名（前端没传时用 URL host + title 当做默认值）。 */
    private String filename;
}
