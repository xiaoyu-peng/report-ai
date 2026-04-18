package com.reportai.hub.department.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "部门查询DTO")
public class DepartmentQueryDTO {
    
    @Schema(description = "关键词")
    private String keyword;
    
    @Schema(description = "租户ID")
    private Long tenantId;
    
    @Schema(description = "父部门ID")
    private Long parentId;
}
