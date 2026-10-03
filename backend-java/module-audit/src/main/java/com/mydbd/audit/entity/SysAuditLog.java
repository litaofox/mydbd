package com.mydbd.audit.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作审计日志（对应 traj.sys_audit_log，只追加表）。
 * 不继承 BaseEntity：无更新人/更新时间/软删除标记。
 */
@Data
@TableName("traj.sys_audit_log")
public class SysAuditLog {

    /** 雪花ID（19位超出JS安全整数，序列化为字符串避免前端精度丢失） */
    @TableId
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String traceId;

    private String userName;

    private String module;

    private String action;

    private String actionName;

    private String objectType;

    private String objectId;

    private String requestMethod;

    private String requestUri;

    private String queryString;

    private String requestBody;

    /** 1=成功 0=失败 */
    private Integer status;

    private Integer resultCode;

    private String errorMsg;

    private Integer costMs;

    private String clientIp;

    private String userAgent;

    private String contentHash;

    private LocalDateTime createTime;
}
