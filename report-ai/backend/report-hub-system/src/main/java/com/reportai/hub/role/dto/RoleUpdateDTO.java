package com.reportai.hub.role.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "更新角色DTO")
public class RoleUpdateDTO {
    
    @Schema(description = "角色名称")
    private String name;
    
    @Schema(description = "权限列表")
    private String permissions;
    
    @Schema(description = "描述")
    private String description;
}
