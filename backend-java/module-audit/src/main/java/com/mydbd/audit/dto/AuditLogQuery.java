package com.mydbd.audit.dto;

import java.time.LocalDateTime;

/**
 * 审计日志分页查询条件
 */
public record AuditLogQuery(
        LocalDateTime startTime,
        LocalDateTime endTime,
        String userName,
        String module,
        String action,
        Integer status,
        String keyword,
        long page,
        long size
) {
}
