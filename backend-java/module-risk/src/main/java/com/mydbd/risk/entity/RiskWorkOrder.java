package com.mydbd.risk.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * F20 处置工单（mon.risk_work_order，与 risk_event 1:1）
 */
@Data
@TableName("mon.risk_work_order")
public class RiskWorkOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long eventId;

    private String eventTitle;

    private String eventCode;

    private String eventSource;

    private String plateNo;

    private String identityCode;

    /** 1低 2中 3高 */
    private Integer riskLevel;

    private LocalDateTime eventTime;

    /** PENDING / PROCESSING / CLOSED */
    private String status;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long assigneeId;

    private String assigneeName;

    private LocalDateTime assignTime;

    private LocalDateTime claimTime;

    private LocalDateTime closeTime;

    /** PHONE_REMIND/EDUCATION/SUSPEND/FALSE_ALARM/OTHER */
    private String closeResult;

    private String closeRemark;

    private LocalDateTime deadline;

    private Integer slaLimitMin;

    private Integer graceMin;

    private Integer overdue;

    private Integer escalated;

    private LocalDateTime escalateTime;

    private Integer reopenCount;

    private Integer validMark;

    @TableField(fill = FieldFill.INSERT)
    private String creator;

    @TableField(value = "create_date", fill = FieldFill.INSERT)
    private LocalDateTime createDate;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updater;

    @TableField(value = "update_date", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateDate;
}
