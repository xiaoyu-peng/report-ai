package com.reportai.hub.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ReportCreateDTO {
    @NotBlank
    private String title;
    @NotBlank
    private String topic;
    @NotNull
    private Long kbId;
    private Long templateId;
    private List<String> keyPoints;
}
