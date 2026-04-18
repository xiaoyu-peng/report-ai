package com.reportai.hub.department.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "更新部门DTO")
public class DepartmentUpdateDTO {
    
    @Schema(description = "部门名称")
    private String name;
    
    @Schema(description = "父部门ID")
    private Long parentId;
    
    @Schema(description = "部门负责人ID")
    private Long managerId;
    
    @Schema(description = "描述")
    private String description;
}
