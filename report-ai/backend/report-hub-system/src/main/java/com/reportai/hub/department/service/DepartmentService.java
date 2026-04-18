package com.reportai.hub.department.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.reportai.hub.common.PageResult;
import com.reportai.hub.common.entity.Department;
import com.reportai.hub.department.dto.DepartmentCreateDTO;
import com.reportai.hub.department.dto.DepartmentQueryDTO;
import com.reportai.hub.department.dto.DepartmentUpdateDTO;

import java.util.List;

public interface DepartmentService extends IService<Department> {
    
    PageResult<Department> page(DepartmentQueryDTO queryDTO, Long current, Long size);
    
    List<Department> tree(Long tenantId);
    
    Department create(DepartmentCreateDTO createDTO);
    
    Department update(Long id, DepartmentUpdateDTO updateDTO);
    
    List<Department> getChildren(Long parentId);
    
    List<Department> getByTenantId(Long tenantId);
    
    void fillDepartmentInfo(Department dept);
}
