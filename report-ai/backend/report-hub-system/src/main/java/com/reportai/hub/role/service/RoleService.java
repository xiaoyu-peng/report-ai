package com.reportai.hub.role.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.reportai.hub.common.PageResult;
import com.reportai.hub.common.entity.Role;
import com.reportai.hub.role.dto.RoleCreateDTO;
import com.reportai.hub.role.dto.RoleQueryDTO;
import com.reportai.hub.role.dto.RoleUpdateDTO;

import java.util.List;

public interface RoleService extends IService<Role> {
    
    PageResult<Role> page(RoleQueryDTO queryDTO, Long current, Long size);
    
    Role create(RoleCreateDTO createDTO);
    
    Role update(Long id, RoleUpdateDTO updateDTO);
    
    void assignPermissions(Long id, List<String> permissions);
    
    List<Role> getByTenantId(Long tenantId);
    
    Role getByCode(String code);
}
