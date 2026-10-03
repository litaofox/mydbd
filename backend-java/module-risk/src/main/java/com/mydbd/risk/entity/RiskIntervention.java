package com.mydbd.risk.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * F21 坐席干预记录（mon.risk_intervention）
 */
@Data
@TableName("mon.risk_intervention")
public class RiskIntervention {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;
    private Long eventId;
    private String plateNo;
    private String identityCode;

    /** PHONE_REMIND / EDUCATION / STOP / PENALTY / OTHER */
    private String actionType;

    /** SUCCESS / FAILED / NO_ANSWER / PENDING */
    private String actionResult;

    private Long operatorId;
    private String operatorName;

    /** MANUAL / SYSTEM */
    private String source;

    private String remark;

    @TableField("create_date")
    private LocalDateTime createDate;
}
