package com.reportai.hub.role.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "角色查询DTO")
public class RoleQueryDTO {
    
    @Schema(description = "关键词")
    private String keyword;
    
    @Schema(description = "租户ID")
    private Long tenantId;
    
    @Schema(description = "是否系统角色")
    private Boolean isSystem;
}
