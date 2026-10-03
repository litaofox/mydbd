package com.mydbd.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mydbd.audit.dto.AuditLogQuery;
import com.mydbd.audit.entity.SysAuditLog;
import com.mydbd.audit.mapper.SysAuditLogMapper;
import com.mydbd.audit.vo.AuditLogVO;
import com.mydbd.audit.vo.AuditStatsVO;
import com.mydbd.common.api.ErrorCode;
import com.mydbd.common.api.PageData;
import com.mydbd.common.audit.AuditAction;
import com.mydbd.common.audit.AuditEvent;
import com.mydbd.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

/**
 * 审计日志落库（含行级指纹）、检索、统计
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    /** 默认查询近 7 天 */
    private static final long DEFAULT_RANGE_DAYS = 7L;
    /** 单次查询最大跨度 90 天 */
    private static final long MAX_RANGE_DAYS = 90L;

    private static final DateTimeFormatter HASH_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-ddHH:mm:ss.SSS");

    private final SysAuditLogMapper auditLogMapper;

    /**
     * 落库一条审计事件（由 Listener 异步调用）
     */
    public void saveEvent(AuditEvent event) {
        SysAuditLog entity = new SysAuditLog();
        entity.setTraceId(event.traceId());
        entity.setUserName(event.userName());
        entity.setModule(event.module());
        entity.setAction(event.action());
        entity.setActionName(event.actionName());
        entity.setObjectType(event.objectType());
        entity.setObjectId(event.objectId());
        entity.setRequestMethod(event.requestMethod());
        entity.setRequestUri(event.requestUri());
        entity.setQueryString(event.queryString());
        entity.setRequestBody(event.requestBody());
        entity.setStatus(event.status());
        entity.setResultCode(event.resultCode());
        entity.setErrorMsg(event.errorMsg());
        entity.setCostMs(event.costMs());
        entity.setClientIp(event.clientIp());
        entity.setUserAgent(event.userAgent());
        entity.setCreateTime(event.createTime());
        entity.setContentHash(computeHash(entity));
        auditLogMapper.insert(entity);
    }

    public PageData<AuditLogVO> page(AuditLogQuery query) {
        LocalDateTime[] range = resolveRange(query.startTime(), query.endTime());
        Page<SysAuditLog> page = new Page<>(query.page(), query.size());
        LambdaQueryWrapper<SysAuditLog> wrapper = new LambdaQueryWrapper<SysAuditLog>()
                .between(SysAuditLog::getCreateTime, range[0], range[1])
                .eq(StringUtils.hasText(query.userName()), SysAuditLog::getUserName, query.userName())
                .eq(StringUtils.hasText(query.module()), SysAuditLog::getModule, query.module())
                .eq(StringUtils.hasText(query.action()), SysAuditLog::getAction, query.action())
                .eq(query.status() != null, SysAuditLog::getStatus, query.status())
                .and(StringUtils.hasText(query.keyword()), k -> k
                        .like(SysAuditLog::getRequestUri, query.keyword())
                        .or().like(SysAuditLog::getObjectId, query.keyword())
                        .or().like(SysAuditLog::getErrorMsg, query.keyword()))
                .orderByDesc(SysAuditLog::getCreateTime)
                .orderByDesc(SysAuditLog::getId);
        Page<SysAuditLog> result = auditLogMapper.selectPage(page, wrapper);
        return new PageData<>(result.getTotal(), result.getCurrent(), result.getSize(),
                result.getRecords().stream().map(AuditLogVO::from).toList());
    }

    public SysAuditLog detail(Long id) {
        SysAuditLog entity = auditLogMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "审计日志不存在");
        }
        return entity;
    }

    public AuditStatsVO stats(LocalDateTime startTime, LocalDateTime endTime) {
        LocalDateTime[] range = resolveRange(startTime, endTime);
        long total = auditLogMapper.selectCount(new LambdaQueryWrapper<SysAuditLog>()
                .between(SysAuditLog::getCreateTime, range[0], range[1]));
        long success = auditLogMapper.selectCount(new LambdaQueryWrapper<SysAuditLog>()
                .between(SysAuditLog::getCreateTime, range[0], range[1])
                .eq(SysAuditLog::getStatus, 1));
        long loginFail = auditLogMapper.selectCount(new LambdaQueryWrapper<SysAuditLog>()
                .between(SysAuditLog::getCreateTime, range[0], range[1])
                .eq(SysAuditLog::getAction, AuditAction.LOGIN_FAIL));
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime now = LocalDateTime.now();
        long todayCount = auditLogMapper.selectCount(new LambdaQueryWrapper<SysAuditLog>()
                .between(SysAuditLog::getCreateTime, todayStart, now));

        double successRate = total == 0 ? 100.0
                : Math.round(success * 1000.0 / total) / 10.0;
        return new AuditStatsVO(total, success, successRate, loginFail, todayCount,
                auditLogMapper.actionDist(range[0], range[1]),
                auditLogMapper.topUsers(range[0], range[1]));
    }

    /**
     * 规范化时间范围：默认近 7 天；校验起止顺序与 90 天上限
     */
    private LocalDateTime[] resolveRange(LocalDateTime start, LocalDateTime end) {
        LocalDateTime endTime = end == null ? LocalDateTime.now() : end;
        LocalDateTime startTime = start == null ? endTime.minusDays(DEFAULT_RANGE_DAYS) : start;
        if (startTime.isAfter(endTime)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "开始时间不能晚于结束时间");
        }
        if (Duration.between(startTime, endTime).compareTo(Duration.ofDays(MAX_RANGE_DAYS)) > 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "单次查询时间跨度不能超过 90 天");
        }
        return new LocalDateTime[]{startTime, endTime};
    }

    /**
     * 行级内容指纹（MOD-AUDIT-001 §5.3）：
     * traceId|userName|module|action|objectType|objectId|method|uri|status|resultCode|createTime
     */
    private String computeHash(SysAuditLog e) {
        String canonical = String.join("|",
                nullToEmpty(e.getTraceId()),
                nullToEmpty(e.getUserName()),
                nullToEmpty(e.getModule()),
                nullToEmpty(e.getAction()),
                nullToEmpty(e.getObjectType()),
                nullToEmpty(e.getObjectId()),
                nullToEmpty(e.getRequestMethod()),
                nullToEmpty(e.getRequestUri()),
                String.valueOf(e.getStatus()),
                e.getResultCode() == null ? "" : String.valueOf(e.getResultCode()),
                e.getCreateTime().format(HASH_TIME));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            // SHA-256 为 JDK 内置算法，理论上不会缺失
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
