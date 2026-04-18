package com.reportai.hub.department.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.reportai.hub.common.PageResult;
import com.reportai.hub.common.entity.Department;
import com.reportai.hub.common.exception.BusinessException;
import com.reportai.hub.department.dto.DepartmentCreateDTO;
import com.reportai.hub.department.dto.DepartmentQueryDTO;
import com.reportai.hub.department.dto.DepartmentUpdateDTO;
import com.reportai.hub.department.mapper.DepartmentMapper;
import com.reportai.hub.department.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl extends ServiceImpl<DepartmentMapper, Department> implements DepartmentService {

    private static final Long DEFAULT_TENANT_ID = 1L;

    @Override
    public PageResult<Department> page(DepartmentQueryDTO queryDTO, Long current, Long size) {
        Page<Department> page = new Page<>(current, size);
        LambdaQueryWrapper<Department> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(queryDTO.getKeyword())) {
            wrapper.like(Department::getName, queryDTO.getKeyword());
        }

        if (queryDTO.getTenantId() != null) {
            wrapper.eq(Department::getTenantId, queryDTO.getTenantId());
        }

        if (queryDTO.getParentId() != null) {
            wrapper.eq(Department::getParentId, queryDTO.getParentId());
        }

        wrapper.orderByAsc(Department::getLevel);
        wrapper.orderByAsc(Department::getCreatedAt);

        Page<Department> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), result.getSize(), result.getCurrent());
    }

    @Override
    public List<Department> tree(Long tenantId) {
        List<Department> allDepts = list(new LambdaQueryWrapper<Department>()
            .eq(tenantId != null, Department::getTenantId, tenantId)
            .orderByAsc(Department::getLevel)
            .orderByAsc(Department::getCreatedAt));

        List<Department> tree = buildTree(allDepts, null);
        fillExtraInfo(tree);
        return tree;
    }

    private void fillExtraInfo(List<Department> departments) {
        for (Department dept : departments) {
            if (dept.getManagerId() != null) {
                dept.setManagerName(baseMapper.getManagerName(dept.getManagerId()));
            }
            dept.setMemberCount(baseMapper.getMemberCount(dept.getId()));

            if (dept.getChildren() != null && !dept.getChildren().isEmpty()) {
                fillExtraInfo(dept.getChildren());
            }
        }
    }

    private List<Department> buildTree(List<Department> allDepts, Long parentId) {
        List<Department> tree = new ArrayList<>();
        for (Department dept : allDepts) {
            boolean isChild = (parentId == null && dept.getParentId() == null) ||
                (parentId != null && parentId.equals(dept.getParentId()));

            if (isChild) {
                List<Department> children = buildTree(allDepts, dept.getId());
                if (!children.isEmpty()) {
                    dept.setChildren(children);
                }
                tree.add(dept);
            }
        }
        return tree;
    }

    @Override
    public Department create(DepartmentCreateDTO createDTO) {
        Department dept = new Department();
        dept.setName(createDTO.getName());
        dept.setTenantId(createDTO.getTenantId() != null ? createDTO.getTenantId() : DEFAULT_TENANT_ID);
        dept.setParentId(createDTO.getParentId());
        dept.setManagerId(createDTO.getManagerId());
        dept.setDescription(createDTO.getDescription());

        if (createDTO.getParentId() != null) {
            Department parent = getById(createDTO.getParentId());
            if (parent == null) {
                throw new BusinessException(404, "父部门不存在");
            }
            dept.setLevel(parent.getLevel() + 1);
            dept.setPath(parent.getPath() + "/" + createDTO.getName());
        } else {
            dept.setLevel(1);
            dept.setPath("/" + createDTO.getName());
        }

        save(dept);
        log.info("Department created successfully, deptId: {}, name: {}", dept.getId(), dept.getName());
        return dept;
    }

    @Override
    public Department update(Long id, DepartmentUpdateDTO updateDTO) {
        Department dept = getById(id);
        if (dept == null) {
            throw new BusinessException(404, "部门不存在");
        }

        if (StringUtils.hasText(updateDTO.getName())) {
            dept.setName(updateDTO.getName());
        }

        if (updateDTO.getParentId() != null) {
            if (id.equals(updateDTO.getParentId())) {
                throw new BusinessException(400, "不能将自己设置为父部门");
            }
            dept.setParentId(updateDTO.getParentId());

            if (updateDTO.getParentId() != null) {
                Department parent = getById(updateDTO.getParentId());
                if (parent == null) {
                    throw new BusinessException(404, "父部门不存在");
                }
                dept.setLevel(parent.getLevel() + 1);
                dept.setPath(parent.getPath() + "/" + dept.getName());
            } else {
                dept.setLevel(1);
                dept.setPath("/" + dept.getName());
            }
        }

        if (updateDTO.getManagerId() != null) {
            dept.setManagerId(updateDTO.getManagerId());
        }

        if (updateDTO.getDescription() != null) {
            dept.setDescription(updateDTO.getDescription());
        }

        updateById(dept);
        log.info("Department updated successfully, deptId: {}", id);
        return dept;
    }

    @Override
    public List<Department> getChildren(Long parentId) {
        return list(new LambdaQueryWrapper<Department>()
            .eq(Department::getParentId, parentId)
            .orderByAsc(Department::getLevel));
    }

    @Override
    public List<Department> getByTenantId(Long tenantId) {
        return list(new LambdaQueryWrapper<Department>()
            .eq(Department::getTenantId, tenantId)
            .orderByAsc(Department::getLevel));
    }

    @Override
    public void fillDepartmentInfo(Department dept) {
        if (dept != null) {
            if (dept.getManagerId() != null) {
                dept.setManagerName(baseMapper.getManagerName(dept.getManagerId()));
            }
            dept.setMemberCount(baseMapper.getMemberCount(dept.getId()));
        }
    }
}
