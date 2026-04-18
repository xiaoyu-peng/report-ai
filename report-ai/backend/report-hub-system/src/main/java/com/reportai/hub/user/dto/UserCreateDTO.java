package com.reportai.hub.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "创建用户DTO")
public class UserCreateDTO {
    
    @NotBlank(message = "用户名不能为空")
    @Schema(description = "用户名")
    private String username;
    
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Schema(description = "邮箱")
    private String email;
    
    @NotBlank(message = "密码不能为空")
    @Schema(description = "密码")
    private String password;
    
    @Schema(description = "租户ID")
    private Long tenantId;
    
    @Schema(description = "部门ID")
    private Long deptId;
    
    @Schema(description = "角色ID")
    private Long roleId;
    
    @Schema(description = "手机号")
    private String phone;
    
    @Schema(description = "头像")
    private String avatar;
}
