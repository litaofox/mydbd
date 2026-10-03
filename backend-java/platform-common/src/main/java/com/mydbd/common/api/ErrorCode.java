package com.mydbd.common.api;

/**
 * 平台统一错误码
 */
public enum ErrorCode {

    BAD_REQUEST(40001, "请求参数错误"),
    UNAUTHORIZED(40101, "未认证或登录已失效"),
    LOCKED(42301, "账号已锁定"),
    FORBIDDEN(40301, "无访问权限"),
    NOT_FOUND(40401, "资源不存在"),
    CONFLICT(40901, "资源状态冲突"),
    INTERNAL_ERROR(50000, "服务器内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }
}
