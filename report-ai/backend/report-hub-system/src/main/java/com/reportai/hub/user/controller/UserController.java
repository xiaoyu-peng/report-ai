package com.reportai.hub.user.controller;

import com.reportai.hub.common.PageResult;
import com.reportai.hub.common.Result;
import com.reportai.hub.common.entity.User;
import com.reportai.hub.common.exception.BusinessException;
import com.reportai.hub.user.dto.UserCreateDTO;
import com.reportai.hub.user.dto.UserQueryDTO;
import com.reportai.hub.user.dto.UserUpdateDTO;
import com.reportai.hub.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户管理控制器
 * 提供用户的CRUD、重置密码、状态管理等接口
 *
 * @author skill-hub
 */
@Tag(name = "用户管理", description = "用户CRUD、重置密码、状态管理")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserService userService;

    @Operation(summary = "分页查询用户列表")
    @GetMapping
    public Result<PageResult<User>> list(UserQueryDTO queryDTO,
                                        @RequestParam(defaultValue = "1") @Min(1) Long current,
                                        @RequestParam(defaultValue = "10") @Min(1) @Max(100) Long size) {
        PageResult<User> result = userService.page(queryDTO, current, size);
        return Result.success(result);
    }

    @Operation(summary = "获取用户详情")
    @GetMapping("/{id}")
    public Result<User> getById(@PathVariable Long id) {
        User user = userService.getById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return Result.success(user);
    }

    @Operation(summary = "创建用户")
    @PostMapping
    public Result<User> create(@Valid @RequestBody UserCreateDTO createDTO) {
        User user = userService.create(createDTO);
        return Result.success(user);
    }

    @Operation(summary = "更新用户")
    @PutMapping("/{id}")
    public Result<User> update(@PathVariable Long id, @Valid @RequestBody UserUpdateDTO updateDTO) {
        User user = userService.update(id, updateDTO);
        return Result.success(user);
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        User user = userService.getById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        userService.removeById(id);
        return Result.success();
    }

    @Operation(summary = "重置密码")
    @PutMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String newPassword = body.get("newPassword");
        if (newPassword == null || newPassword.trim().isEmpty()) {
            throw new BusinessException(400, "新密码不能为空");
        }
        userService.resetPassword(id, newPassword);
        return Result.success();
    }

    @Operation(summary = "启用用户")
    @PutMapping("/{id}/enable")
    public Result<Void> enable(@PathVariable Long id) {
        userService.updateStatus(id, "active");
        return Result.success();
    }

    @Operation(summary = "禁用用户")
    @PutMapping("/{id}/disable")
    public Result<Void> disable(@PathVariable Long id) {
        userService.updateStatus(id, "disabled");
        return Result.success();
    }

    @Operation(summary = "锁定用户")
    @PutMapping("/{id}/lock")
    public Result<Void> lock(@PathVariable Long id) {
        userService.updateStatus(id, "locked");
        return Result.success();
    }

    @Operation(summary = "解锁用户")
    @PutMapping("/{id}/unlock")
    public Result<Void> unlock(@PathVariable Long id) {
        userService.updateStatus(id, "active");
        return Result.success();
    }
}
