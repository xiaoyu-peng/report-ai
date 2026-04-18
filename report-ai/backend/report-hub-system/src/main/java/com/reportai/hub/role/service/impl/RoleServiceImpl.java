package com.reportai.hub.role.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reportai.hub.common.PageResult;
import com.reportai.hub.common.entity.Role;
import com.reportai.hub.common.exception.BusinessException;
import com.reportai.hub.role.dto.RoleCreateDTO;
import com.reportai.hub.role.dto.RoleQueryDTO;
import com.reportai.hub.role.dto.RoleUpdateDTO;
import com.reportai.hub.role.mapper.RoleMapper;
import com.reportai.hub.role.service.RoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 角色服务实现类
 *
 * @author skill-hub
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleServiceImpl extends ServiceImpl<RoleMapper, Role> implements RoleService {

    private final ObjectMapper objectMapper;

    @Override
    public PageResult<Role> page(RoleQueryDTO queryDTO, Long current, Long size) {
        Page<Role> page = new Page<>(current, size);
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(queryDTO.getKeyword())) {
            wrapper.and(w -> w
                .like(Role::getName, queryDTO.getKeyword())
                .or()
                .like(Role::getCode, queryDTO.getKeyword())
            );
        }

        if (queryDTO.getTenantId() != null) {
            wrapper.eq(Role::getTenantId, queryDTO.getTenantId());
        }

        if (queryDTO.getIsSystem() != null) {
            wrapper.eq(Role::getIsSystem, queryDTO.getIsSystem());
        }

        wrapper.orderByDesc(Role::getCreatedAt);

        Page<Role> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), result.getSize(), result.getCurrent());
    }

    @Override
    public Role create(RoleCreateDTO createDTO) {
        Role existRole = getByCode(createDTO.getCode());
        if (existRole != null) {
            throw new BusinessException(400, "角色编码已存在");
        }

        Role role = new Role();
        role.setName(createDTO.getName());
        role.setCode(createDTO.getCode());
        role.setTenantId(createDTO.getTenantId() != null ? createDTO.getTenantId() : 1L);
        role.setPermissions(createDTO.getPermissions());
        role.setIsSystem(createDTO.getIsSystem() != null ? createDTO.getIsSystem() : false);
        role.setDescription(createDTO.getDescription());

        save(role);
        log.info("Role created successfully, roleId: {}, code: {}", role.getId(), role.getCode());
        return role;
    }

    @Override
    public Role update(Long id, RoleUpdateDTO updateDTO) {
        Role role = getById(id);
        if (role == null) {
            throw new BusinessException(404, "角色不存在");
        }

        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw new BusinessException(400, "系统角色不允许修改");
        }

        if (StringUtils.hasText(updateDTO.getName())) {
            role.setName(updateDTO.getName());
        }

        if (updateDTO.getPermissions() != null) {
            role.setPermissions(updateDTO.getPermissions());
        }

        if (updateDTO.getDescription() != null) {
            role.setDescription(updateDTO.getDescription());
        }

        updateById(role);
        log.info("Role updated successfully, roleId: {}", id);
        return role;
    }

    @Override
    public void assignPermissions(Long id, List<String> permissions) {
        Role role = getById(id);
        if (role == null) {
            throw new BusinessException(404, "角色不存在");
        }

        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw new BusinessException(400, "系统角色不允许修改权限");
        }

        try {
            role.setPermissions(objectMapper.writeValueAsString(permissions));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize permissions", e);
            throw new BusinessException(500, "权限序列化失败");
        }

        updateById(role);
        log.info("Role permissions assigned, roleId: {}", id);
    }

    @Override
    public List<Role> getByTenantId(Long tenantId) {
        return list(new LambdaQueryWrapper<Role>()
            .eq(Role::getTenantId, tenantId)
            .orderByDesc(Role::getCreatedAt));
    }

    @Override
    public Role getByCode(String code) {
        return getOne(new LambdaQueryWrapper<Role>()
            .eq(Role::getCode, code));
    }
}
