package com.reportai.hub.department.mapper;

import com.reportai.hub.common.mapper.CustomBaseMapper;
import com.reportai.hub.common.entity.Department;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DepartmentMapper extends CustomBaseMapper<Department> {
    
    @Select("SELECT u.username FROM sys_user u WHERE u.id = #{managerId} AND u.deleted = 0")
    String getManagerName(@Param("managerId") Long managerId);
    
    @Select("SELECT COUNT(*) FROM sys_user WHERE dept_id = #{deptId} AND deleted = 0")
    Integer getMemberCount(@Param("deptId") Long deptId);
    
    @Select("SELECT * FROM sys_department WHERE tenant_id = #{tenantId} AND parent_id = #{parentId} AND deleted = 0 ORDER BY created_at")
    List<Department> selectByParentId(@Param("tenantId") Long tenantId, @Param("parentId") Long parentId);
    
    @Select("SELECT * FROM sys_department WHERE tenant_id = #{tenantId} AND deleted = 0 ORDER BY path, level")
    List<Department> selectAllByTenantId(@Param("tenantId") Long tenantId);
}
