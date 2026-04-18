package com.reportai.hub.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "用户查询DTO")
public class UserQueryDTO {
    
    @Schema(description = "关键词")
    private String keyword;
    
    @Schema(description = "状态")
    private String status;
    
    @Schema(description = "部门ID")
    private Long deptId;
    
    @Schema(description = "角色ID")
    private Long roleId;
    
    @Schema(description = "租户ID")
    private Long tenantId;
}
