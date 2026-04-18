package com.reportai.hub.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.reportai.hub.common.PageResult;
import com.reportai.hub.common.entity.User;
import com.reportai.hub.common.exception.BusinessException;
import com.reportai.hub.user.dto.UserCreateDTO;
import com.reportai.hub.user.dto.UserQueryDTO;
import com.reportai.hub.user.dto.UserUpdateDTO;
import com.reportai.hub.user.mapper.UserMapper;
import com.reportai.hub.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
    
    private final PasswordEncoder passwordEncoder;
    
    @Override
    public PageResult<User> page(UserQueryDTO queryDTO, Long current, Long size) {
        Page<User> page = new Page<>(current, size);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        
        if (StringUtils.hasText(queryDTO.getKeyword())) {
            wrapper.and(w -> w
                .like(User::getUsername, queryDTO.getKeyword())
                .or()
                .like(User::getEmail, queryDTO.getKeyword())
                .or()
                .like(User::getPhone, queryDTO.getKeyword())
            );
        }
        
        if (StringUtils.hasText(queryDTO.getStatus())) {
            wrapper.eq(User::getStatus, queryDTO.getStatus());
        }
        
        if (queryDTO.getDeptId() != null) {
            wrapper.eq(User::getDeptId, queryDTO.getDeptId());
        }
        
        if (queryDTO.getRoleId() != null) {
            wrapper.eq(User::getRoleId, queryDTO.getRoleId());
        }
        
        if (queryDTO.getTenantId() != null) {
            wrapper.eq(User::getTenantId, queryDTO.getTenantId());
        }
        
        wrapper.orderByDesc(User::getCreatedAt);
        
        Page<User> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), result.getSize(), result.getCurrent());
    }
    
    @Override
    public User create(UserCreateDTO createDTO) {
        User existUser = getByUsername(createDTO.getUsername());
        if (existUser != null) {
            throw new BusinessException(400, "用户名已存在");
        }
        
        User user = new User();
        user.setUsername(createDTO.getUsername());
        user.setEmail(createDTO.getEmail());
        user.setPasswordHash(passwordEncoder.encode(createDTO.getPassword()));
        user.setTenantId(createDTO.getTenantId() != null ? createDTO.getTenantId() : 1L);
        user.setDeptId(createDTO.getDeptId());
        user.setRoleId(createDTO.getRoleId());
        user.setPhone(createDTO.getPhone());
        user.setAvatar(createDTO.getAvatar());
        user.setStatus("active");
        user.setMfaEnabled(false);
        
        save(user);
        return user;
    }
    
    @Override
    public User update(Long id, UserUpdateDTO updateDTO) {
        User user = getById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        
        if (StringUtils.hasText(updateDTO.getEmail())) {
            user.setEmail(updateDTO.getEmail());
        }
        if (updateDTO.getDeptId() != null) {
            user.setDeptId(updateDTO.getDeptId());
        }
        if (updateDTO.getRoleId() != null) {
            user.setRoleId(updateDTO.getRoleId());
        }
        if (StringUtils.hasText(updateDTO.getPhone())) {
            user.setPhone(updateDTO.getPhone());
        }
        if (StringUtils.hasText(updateDTO.getAvatar())) {
            user.setAvatar(updateDTO.getAvatar());
        }
        
        updateById(user);
        return user;
    }
    
    @Override
    public void resetPassword(Long id, String newPassword) {
        User user = getById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        updateById(user);
    }
    
    @Override
    public void updateStatus(Long id, String status) {
        User user = getById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        
        user.setStatus(status);
        updateById(user);
    }
    
    @Override
    public User getByUsername(String username) {
        return getOne(new LambdaQueryWrapper<User>()
            .eq(User::getUsername, username));
    }
}
