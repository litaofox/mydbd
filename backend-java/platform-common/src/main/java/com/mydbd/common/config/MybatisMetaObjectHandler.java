package com.mydbd.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.mydbd.common.security.UserContext;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 公共审计字段自动填充：
 * 新增时写 creator/create_date/updater/update_date；更新时写 updater/update_date。
 * 操作人取当前登录用户（UserContext），无上下文（如初始化任务）时回落为 system。
 */
@Component
public class MybatisMetaObjectHandler implements MetaObjectHandler {

    private static final String DEFAULT_USER = "system";

    @Override
    public void insertFill(MetaObject metaObject) {
        String user = currentUser();
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, "creator", String.class, user);
        strictInsertFill(metaObject, "createDate", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updater", String.class, user);
        strictInsertFill(metaObject, "updateDate", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updater", String.class, currentUser());
        strictUpdateFill(metaObject, "updateDate", LocalDateTime.class, LocalDateTime.now());
    }

    private String currentUser() {
        String username = UserContext.username();
        return StringUtils.hasText(username) ? username : DEFAULT_USER;
    }
}
