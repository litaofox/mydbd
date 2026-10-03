package com.mydbd.common.audit;

import java.time.LocalDateTime;

/**
 * 审计事件：采集端（common）→ 落库端（module-audit）的不可变载体，
 * 经 Spring ApplicationEvent 解耦，common 不依赖任何业务模块。
 */
public record AuditEvent(
        String traceId,
        String userName,
        String module,
        String action,
        String actionName,
        String objectType,
        String objectId,
        String requestMethod,
        String requestUri,
        String queryString,
        String requestBody,
        Integer status,
        Integer resultCode,
        String errorMsg,
        Integer costMs,
        String clientIp,
        String userAgent,
        LocalDateTime createTime
) {
}
