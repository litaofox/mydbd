package com.mydbd.audit.listener;

import com.mydbd.audit.service.AuditLogService;
import com.mydbd.common.audit.AuditEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 审计事件异步落库监听：单线程 auditExecutor 顺序消费。
 * 落库失败仅记录 error，不重试、不影响业务（MOD-AUDIT-001 §5.5）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogListener {

    private final AuditLogService auditLogService;

    @Async("auditExecutor")
    @EventListener
    public void onAuditEvent(AuditEvent event) {
        try {
            auditLogService.saveEvent(event);
        } catch (Exception ex) {
            log.error("审计日志落库失败 uri={}, action={}", event.requestUri(), event.action(), ex);
        }
    }
}
