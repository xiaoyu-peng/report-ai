package com.reportai.hub.role.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "创建角色DTO")
public class RoleCreateDTO {
    
    @NotBlank(message = "角色名称不能为空")
    @Schema(description = "角色名称")
    private String name;
    
    @NotBlank(message = "角色编码不能为空")
    @Schema(description = "角色编码")
    private String code;
    
    @Schema(description = "租户ID")
    private Long tenantId;
    
    @Schema(description = "权限列表")
    private String permissions;
    
    @Schema(description = "是否系统角色")
    private Boolean isSystem;
    
    @Schema(description = "描述")
    private String description;
}
