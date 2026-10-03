package com.mydbd.audit.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.mydbd.audit.entity.SysAuditLog;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志列表行（不含请求体全文，仅前 200 字符预览）
 */
@Data
public class AuditLogVO {

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
    private Integer status;
    private Integer resultCode;
    private String errorMsg;
    private Integer costMs;
    private String clientIp;
    private LocalDateTime createTime;
    private String bodyPreview;

    public static AuditLogVO from(SysAuditLog e) {
        AuditLogVO vo = new AuditLogVO();
        vo.setId(e.getId());
        vo.setTraceId(e.getTraceId());
        vo.setUserName(e.getUserName());
        vo.setModule(e.getModule());
        vo.setAction(e.getAction());
        vo.setActionName(e.getActionName());
        vo.setObjectType(e.getObjectType());
        vo.setObjectId(e.getObjectId());
        vo.setRequestMethod(e.getRequestMethod());
        vo.setRequestUri(e.getRequestUri());
        vo.setStatus(e.getStatus());
        vo.setResultCode(e.getResultCode());
        vo.setErrorMsg(e.getErrorMsg());
        vo.setCostMs(e.getCostMs());
        vo.setClientIp(e.getClientIp());
        vo.setCreateTime(e.getCreateTime());
        String body = e.getRequestBody();
        if (body != null) {
            vo.setBodyPreview(body.length() <= 200 ? body : body.substring(0, 200));
        }
        return vo;
    }
}
