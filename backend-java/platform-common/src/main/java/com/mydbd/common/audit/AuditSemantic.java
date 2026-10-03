package com.mydbd.common.audit;

/**
 * AOP 切面解析出的审计语义，经 request attribute 传递给 AuditFilter。
 */
public record AuditSemantic(String objectId) {
}
