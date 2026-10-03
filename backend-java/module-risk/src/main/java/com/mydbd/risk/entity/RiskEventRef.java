package com.mydbd.risk.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * F20 工单域内对 mon.risk_event 的只读/回写引用（与 module-monitor 的 RiskEvent 隔离，
 * 仅用工单详情快照与闭环回写，避免模块间代码依赖）。
 */
@Data
@TableName("mon.risk_event")
public class RiskEventRef {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventCode;

    private String eventSource;

    private Long ruleId;

    private Long fenceId;

    private String title;

    private String plateNo;

    private String identityCode;

    private LocalDateTime eventTime;

    private BigDecimal lng;

    private BigDecimal lat;

    private Integer speed;

    private Integer riskLevel;

    private BigDecimal confidence;

    private String mediaUrl;

    private Integer handleStatus;

    private String handleRemark;

    @TableField("create_date")
    private LocalDateTime createDate;
}
