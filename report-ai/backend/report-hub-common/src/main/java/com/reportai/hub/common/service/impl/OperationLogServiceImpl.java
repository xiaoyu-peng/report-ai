package com.reportai.hub.common.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.reportai.hub.common.entity.OperationLog;
import com.reportai.hub.common.mapper.OperationLogMapper;
import com.reportai.hub.common.service.OperationLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class OperationLogServiceImpl extends ServiceImpl<OperationLogMapper, OperationLog> implements OperationLogService {

    @Override
    public void log(Long userId, String username, String action, String actionName,
                    String resourceType, String resourceId, String resourceName,
                    String details, String ipAddress, String userAgent, String status, Integer durationMs) {
        OperationLog operationLog = new OperationLog();
        operationLog.setUserId(userId);
        operationLog.setUsername(username);
        operationLog.setAction(action);
        operationLog.setActionName(actionName);
        operationLog.setResourceType(resourceType);
        operationLog.setResourceId(resourceId);
        operationLog.setResourceName(resourceName);
        operationLog.setDetails(details);
        operationLog.setIpAddress(ipAddress);
        operationLog.setUserAgent(userAgent);
        operationLog.setStatus(status);
        operationLog.setDurationMs(durationMs);
        this.save(operationLog);
        log.debug("Operation log saved, action: {}, resource: {}", action, resourceType);
    }
}
