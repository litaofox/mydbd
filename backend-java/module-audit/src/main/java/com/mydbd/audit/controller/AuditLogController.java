package com.mydbd.audit.controller;

import com.mydbd.audit.dto.AuditLogQuery;
import com.mydbd.audit.entity.SysAuditLog;
import com.mydbd.audit.service.AuditLogService;
import com.mydbd.audit.vo.AuditLogVO;
import com.mydbd.audit.vo.AuditStatsVO;
import com.mydbd.common.api.PageData;
import com.mydbd.common.api.Result;
import com.mydbd.common.security.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 操作审计查询接口（只读，无写入/修改/删除）
 */
@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@RequiresPerm("audit:view")
public class AuditLogController {

    private static final String TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private final AuditLogService auditLogService;

    @GetMapping("/logs")
    public Result<PageData<AuditLogVO>> page(
            @RequestParam(required = false) @DateTimeFormat(pattern = TIME_PATTERN) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = TIME_PATTERN) LocalDateTime endTime,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        AuditLogQuery query = new AuditLogQuery(startTime, endTime, userName, module, action,
                status, keyword, page, size);
        return Result.ok(auditLogService.page(query));
    }

    @GetMapping("/logs/{id}")
    public Result<SysAuditLog> detail(@PathVariable Long id) {
        return Result.ok(auditLogService.detail(id));
    }

    /**
     * 区间概览：与检索共用时间规则（默认近 7 天、跨度上限 90 天）
     */
    @GetMapping("/stats")
    public Result<AuditStatsVO> stats(
            @RequestParam(required = false) @DateTimeFormat(pattern = TIME_PATTERN) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = TIME_PATTERN) LocalDateTime endTime) {
        return Result.ok(auditLogService.stats(startTime, endTime));
    }
}
