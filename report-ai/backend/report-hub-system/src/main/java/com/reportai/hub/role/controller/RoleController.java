package com.reportai.hub.role.controller;

import com.reportai.hub.common.PageResult;
import com.reportai.hub.common.Result;
import com.reportai.hub.common.entity.Role;
import com.reportai.hub.common.exception.BusinessException;
import com.reportai.hub.role.dto.RoleCreateDTO;
import com.reportai.hub.role.dto.RoleQueryDTO;
import com.reportai.hub.role.dto.RoleUpdateDTO;
import com.reportai.hub.role.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 角色管理控制器
 * 提供角色的CRUD、权限分配等接口
 *
 * @author skill-hub
 */
@Tag(name = "角色管理", description = "角色CRUD、权限分配")
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@Validated
public class RoleController {

    private final RoleService roleService;

    @Operation(summary = "分页查询角色列表")
    @GetMapping
    public Result<PageResult<Role>> list(RoleQueryDTO queryDTO,
                                        @RequestParam(defaultValue = "1") @Min(1) Long current,
                                        @RequestParam(defaultValue = "10") @Min(1) @Max(100) Long size) {
        PageResult<Role> result = roleService.page(queryDTO, current, size);
        return Result.success(result);
    }

    @Operation(summary = "获取角色详情")
    @GetMapping("/{id}")
    public Result<Role> getById(@PathVariable Long id) {
        Role role = roleService.getById(id);
        if (role == null) {
            throw new BusinessException(404, "角色不存在");
        }
        return Result.success(role);
    }

    @Operation(summary = "创建角色")
    @PostMapping
    public Result<Role> create(@Valid @RequestBody RoleCreateDTO createDTO) {
        Role role = roleService.create(createDTO);
        return Result.success(role);
    }

    @Operation(summary = "更新角色")
    @PutMapping("/{id}")
    public Result<Role> update(@PathVariable Long id, @Valid @RequestBody RoleUpdateDTO updateDTO) {
        Role role = roleService.update(id, updateDTO);
        return Result.success(role);
    }

    @Operation(summary = "删除角色")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Role role = roleService.getById(id);
        if (role == null) {
            throw new BusinessException(404, "角色不存在");
        }

        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw new BusinessException(400, "系统角色不允许删除");
        }

        roleService.removeById(id);
        return Result.success();
    }

    @Operation(summary = "分配权限")
    @PutMapping("/{id}/permissions")
    public Result<Void> assignPermissions(@PathVariable Long id, @RequestBody Map<String, List<String>> body) {
        List<String> permissions = body.get("permissions");
        if (permissions == null) {
            throw new BusinessException(400, "权限列表不能为空");
        }
        roleService.assignPermissions(id, permissions);
        return Result.success();
    }

    @Operation(summary = "获取租户下的所有角色")
    @GetMapping("/tenant/{tenantId}")
    public Result<List<Role>> getByTenantId(@PathVariable Long tenantId) {
        List<Role> roles = roleService.getByTenantId(tenantId);
        return Result.success(roles);
    }
}
