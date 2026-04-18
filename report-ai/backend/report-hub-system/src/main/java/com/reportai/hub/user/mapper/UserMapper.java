package com.reportai.hub.user.mapper;

import com.reportai.hub.common.mapper.CustomBaseMapper;
import com.reportai.hub.common.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserMapper extends CustomBaseMapper<User> {
    
    @Select("SELECT * FROM sys_user WHERE username = #{username} AND deleted = 0")
    User selectByUsername(@Param("username") String username);
    
    @Select("SELECT * FROM sys_user WHERE email = #{email} AND deleted = 0")
    User selectByEmail(@Param("email") String email);
    
    @Select("SELECT * FROM sys_user WHERE tenant_id = #{tenantId} AND deleted = 0")
    List<User> selectByTenantId(@Param("tenantId") Long tenantId);
}
