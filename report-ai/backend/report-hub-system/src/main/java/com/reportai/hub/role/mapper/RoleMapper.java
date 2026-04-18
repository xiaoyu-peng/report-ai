package com.reportai.hub.role.mapper;

import com.reportai.hub.common.mapper.CustomBaseMapper;
import com.reportai.hub.common.entity.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RoleMapper extends CustomBaseMapper<Role> {
    
    @Select("SELECT * FROM sys_role WHERE code = #{code} AND tenant_id = #{tenantId} AND deleted = 0")
    Role selectByCode(@Param("code") String code, @Param("tenantId") Long tenantId);
    
    @Select("SELECT * FROM sys_role WHERE tenant_id = #{tenantId} AND deleted = 0 ORDER BY created_at")
    List<Role> selectByTenantId(@Param("tenantId") Long tenantId);
}
