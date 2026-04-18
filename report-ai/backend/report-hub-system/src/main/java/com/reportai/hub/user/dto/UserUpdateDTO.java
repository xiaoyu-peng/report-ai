package com.reportai.hub.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "更新用户DTO")
public class UserUpdateDTO {
    
    @Schema(description = "邮箱")
    private String email;
    
    @Schema(description = "部门ID")
    private Long deptId;
    
    @Schema(description = "角色ID")
    private Long roleId;
    
    @Schema(description = "手机号")
    private String phone;
    
    @Schema(description = "头像")
    private String avatar;
}
