package com.mydbd.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 功能权限校验注解（可标注于 Controller 类或方法；方法注解优先）。
 * 不满足抛 BizException(FORBIDDEN 40301)；未登录抛 UNAUTHORIZED 40101。
 * 超级管理员恒放行。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPerm {

    /** 权限码，如 mdm:vehicle:edit */
    String[] value();

    /** 多权限码逻辑，默认全部满足 */
    Logical logical() default Logical.AND;
}
