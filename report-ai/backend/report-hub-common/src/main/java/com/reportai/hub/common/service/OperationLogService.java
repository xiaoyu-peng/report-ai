package com.reportai.hub.common.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.reportai.hub.common.entity.OperationLog;

public interface OperationLogService extends IService<OperationLog> {
    
    void log(Long userId, String username, String action, String actionName,
             String resourceType, String resourceId, String resourceName,
             String details, String ipAddress, String userAgent, String status, Integer durationMs);
}
