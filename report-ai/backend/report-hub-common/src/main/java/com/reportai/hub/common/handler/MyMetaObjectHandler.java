package com.reportai.hub.common.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.reportai.hub.common.context.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {
    
    @Override
    public void insertFill(MetaObject metaObject) {
        log.debug("开始insert填充...");
        
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "deleted", Integer.class, 0);
        this.strictInsertFill(metaObject, "version", Integer.class, 1);
        
        Long userId = UserContext.getUserId();
        log.debug("insertFill - UserContext.getUserId(): {}", userId);
        if (userId != null) {
            this.strictInsertFill(metaObject, "createdBy", Long.class, userId);
        }
    }
    
    @Override
    public void updateFill(MetaObject metaObject) {
        log.debug("开始update填充...");
        
        this.setFieldValByName("updatedAt", LocalDateTime.now(), metaObject);
    }
}
