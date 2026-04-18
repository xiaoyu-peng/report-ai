package com.reportai.hub.common.aspect;

import com.reportai.hub.common.context.UserContext;
import com.reportai.hub.common.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final OperationLogService operationLogService;

    @Around("execution(* com.reportai.hub..controller.*Controller.*(..))")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        String action = joinPoint.getSignature().getDeclaringTypeName() + "." + joinPoint.getSignature().getName();
        String actionName = joinPoint.getSignature().getName();
        String status = "SUCCESS";
        String errorMessage = null;
        Object result = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable e) {
            status = "FAIL";
            errorMessage = e.getMessage();
            throw e;
        } finally {
            try {
                long duration = System.currentTimeMillis() - start;
                Long userId = UserContext.getUserId();
                String username = UserContext.getUsername();
                String ipAddress = getClientIp();
                String userAgent = getUserAgent();
                operationLogService.log(
                    userId, username, action, actionName,
                    null, null, null,
                    errorMessage, ipAddress, userAgent, status, (int) duration
                );
            } catch (Exception ex) {
                log.warn("记录操作日志失败: {}", ex.getMessage());
            }
        }
    }

    private String getClientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;
            HttpServletRequest request = attrs.getRequest();
            String ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("X-Real-IP");
            }
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getRemoteAddr();
            }
            return ip;
        } catch (Exception e) {
            return null;
        }
    }

    private String getUserAgent() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;
            return attrs.getRequest().getHeader("User-Agent");
        } catch (Exception e) {
            return null;
        }
    }
}
