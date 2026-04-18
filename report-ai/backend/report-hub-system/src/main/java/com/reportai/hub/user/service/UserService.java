package com.reportai.hub.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.reportai.hub.common.PageResult;
import com.reportai.hub.common.entity.User;
import com.reportai.hub.user.dto.UserCreateDTO;
import com.reportai.hub.user.dto.UserQueryDTO;
import com.reportai.hub.user.dto.UserUpdateDTO;

public interface UserService extends IService<User> {
    
    PageResult<User> page(UserQueryDTO queryDTO, Long current, Long size);
    
    User create(UserCreateDTO createDTO);
    
    User update(Long id, UserUpdateDTO updateDTO);
    
    void resetPassword(Long id, String newPassword);
    
    void updateStatus(Long id, String status);
    
    User getByUsername(String username);
}
