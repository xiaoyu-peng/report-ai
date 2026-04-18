package com.reportai.hub.department.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "创建部门DTO")
public class DepartmentCreateDTO {
    
    @NotBlank(message = "部门名称不能为空")
    @Schema(description = "部门名称")
    private String name;
    
    @Schema(description = "租户ID")
    private Long tenantId;
    
    @Schema(description = "父部门ID")
    private Long parentId;
    
    @Schema(description = "部门负责人ID")
    private Long managerId;
    
    @Schema(description = "描述")
    private String description;
}
