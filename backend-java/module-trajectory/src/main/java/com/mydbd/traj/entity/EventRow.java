package com.mydbd.traj.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 事件结果列表行（来源 mon.risk_event，跨 schema 只读映射）。
 */
@Data
public class EventRow {

    private Long id;

    private String identityCode;

    private String plateNo;

    private String eventCode;

    private String eventSource;

    private LocalDateTime eventTime;

    private Integer speed;

    /** 1低 2中 3高 */
    private Integer riskLevel;

    private BigDecimal lng;

    private BigDecimal lat;
}
