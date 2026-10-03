package com.mydbd.monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 风险预警事件（对应 mon.risk_event）
 * 来源：ADAS（前向碰撞/车道偏离/车距过近/行人碰撞）、
 *       DSM（疲劳/分心/抽烟/接打电话）、北斗（超速/围栏）
 */
@Data
@TableName("mon.risk_event")
public class RiskEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventCode;

    private String eventSource;

    /** F18：命中规则 id（围栏事件为空） */
    private Long ruleId;

    /** F18：命中围栏 id（非围栏事件为空） */
    private Long fenceId;

    /** F18：事件中文名快照 */
    private String title;

    private String plateNo;

    private String identityCode;

    private LocalDateTime eventTime;

    private BigDecimal lng;

    private BigDecimal lat;

    private Integer speed;

    /** 风险等级：1=低 2=中 3=高 */
    private Integer riskLevel;

    private BigDecimal confidence;

    private String mediaUrl;

    /** 处置状态：0=未处置 1=已处置 */
    private Integer handleStatus;

    private String handleRemark;

    @TableField("create_date")
    private LocalDateTime createDate;
}
