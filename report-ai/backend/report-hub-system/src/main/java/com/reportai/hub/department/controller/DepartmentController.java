package com.reportai.hub.department.controller;

import com.reportai.hub.common.PageResult;
import com.reportai.hub.common.Result;
import com.reportai.hub.common.entity.Department;
import com.reportai.hub.common.exception.BusinessException;
import com.reportai.hub.department.dto.DepartmentCreateDTO;
import com.reportai.hub.department.dto.DepartmentQueryDTO;
import com.reportai.hub.department.dto.DepartmentUpdateDTO;
import com.reportai.hub.department.service.DepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 部门管理控制器
 * 提供部门的CRUD、树形结构查询等接口
 *
 * @author skill-hub
 */
@Tag(name = "部门管理", description = "部门CRUD、树形结构查询")
@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
@Validated
public class DepartmentController {

    private final DepartmentService departmentService;

    @Operation(summary = "分页查询部门列表")
    @GetMapping
    public Result<PageResult<Department>> list(DepartmentQueryDTO queryDTO,
                                              @RequestParam(defaultValue = "1") @Min(1) Long current,
                                              @RequestParam(defaultValue = "10") @Min(1) @Max(100) Long size) {
        PageResult<Department> result = departmentService.page(queryDTO, current, size);
        return Result.success(result);
    }

    @Operation(summary = "获取部门树形结构")
    @GetMapping("/tree")
    public Result<List<Department>> tree(@RequestParam(required = false) Long tenantId) {
        List<Department> tree = departmentService.tree(tenantId);
        return Result.success(tree);
    }

    @Operation(summary = "获取部门详情")
    @GetMapping("/{id}")
    public Result<Department> getById(@PathVariable Long id) {
        Department dept = departmentService.getById(id);
        if (dept == null) {
            throw new BusinessException(404, "部门不存在");
        }
        departmentService.fillDepartmentInfo(dept);
        return Result.success(dept);
    }

    @Operation(summary = "创建部门")
    @PostMapping
    public Result<Department> create(@Valid @RequestBody DepartmentCreateDTO createDTO) {
        Department dept = departmentService.create(createDTO);
        return Result.success(dept);
    }

    @Operation(summary = "更新部门")
    @PutMapping("/{id}")
    public Result<Department> update(@PathVariable Long id, @Valid @RequestBody DepartmentUpdateDTO updateDTO) {
        Department dept = departmentService.update(id, updateDTO);
        return Result.success(dept);
    }

    @Operation(summary = "删除部门")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Department dept = departmentService.getById(id);
        if (dept == null) {
            throw new BusinessException(404, "部门不存在");
        }

        List<Department> children = departmentService.getChildren(id);
        if (!children.isEmpty()) {
            throw new BusinessException(400, "该部门下存在子部门，无法删除");
        }

        departmentService.removeById(id);
        return Result.success();
    }

    @Operation(summary = "获取子部门列表")
    @GetMapping("/{id}/children")
    public Result<List<Department>> getChildren(@PathVariable Long id) {
        List<Department> children = departmentService.getChildren(id);
        return Result.success(children);
    }

    @Operation(summary = "获取租户下的所有部门")
    @GetMapping("/tenant/{tenantId}")
    public Result<List<Department>> getByTenantId(@PathVariable Long tenantId) {
        List<Department> depts = departmentService.getByTenantId(tenantId);
        return Result.success(depts);
    }
}
