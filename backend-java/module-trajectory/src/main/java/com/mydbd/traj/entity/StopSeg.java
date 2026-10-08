package com.mydbd.traj.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 停车结果列表行：由连续零速轨迹点聚合出的停车段。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StopSeg {

    private String plateNo;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** 停车时长（秒） */
    private Long durationSec;

    private BigDecimal lng;

    private BigDecimal lat;
}
