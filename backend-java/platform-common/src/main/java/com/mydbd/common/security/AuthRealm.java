package com.mydbd.common.security;

/**
 * 认证主体装载 SPI：由业务鉴权模块（module-iam）实现，
 * platform-common 不反向依赖具体模块。
 */
public interface AuthRealm {

    /**
     * 按用户ID装载完整登录主体（含权限集与数据范围）。
     *
     * @return 用户不存在、已停用/删除时返回 {@code null}（调用方按 401 处理）
     */
    UserInfo loadByUserId(Long userId);
}
