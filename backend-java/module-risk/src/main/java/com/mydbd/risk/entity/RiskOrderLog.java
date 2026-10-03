package com.mydbd.risk.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * F20 工单流转日志（mon.risk_order_log）
 */
@Data
@TableName("mon.risk_order_log")
public class RiskOrderLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    /** CREATE/ASSIGN/CLAIM/TRANSFER/CLOSE/REOPEN/ESCALATE */
    private String action;

    private String fromStatus;

    private String toStatus;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long fromUserId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long toUserId;

    private String fromUserName;

    private String toUserName;

    private String remark;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long operatorId;

    private String operatorName;

    @TableField("create_date")
    private LocalDateTime createDate;
}
