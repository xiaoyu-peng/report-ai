package com.reportai.hub.common.mapper;

import com.reportai.hub.common.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OperationLogMapper extends CustomBaseMapper<OperationLog> {
    
    @Select("SELECT * FROM operation_logs WHERE user_id = #{userId} ORDER BY created_at DESC LIMIT #{limit}")
    List<OperationLog> selectRecentByUserId(@Param("userId") String userId, @Param("limit") int limit);
    
    @Select("SELECT * FROM operation_logs WHERE tenant_id = #{tenantId} ORDER BY created_at DESC LIMIT #{limit}")
    List<OperationLog> selectRecentByTenantId(@Param("tenantId") String tenantId, @Param("limit") int limit);
}
