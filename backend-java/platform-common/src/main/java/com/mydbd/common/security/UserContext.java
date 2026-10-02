package com.mydbd.common.security;

/**
 * 基于 ThreadLocal 的当前用户上下文
 */
public final class UserContext {

    private static final ThreadLocal<UserInfo> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(UserInfo user) {
        HOLDER.set(user);
    }

    public static UserInfo get() {
        return HOLDER.get();
    }

    public static String username() {
        UserInfo info = HOLDER.get();
        return info == null ? null : info.username();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
