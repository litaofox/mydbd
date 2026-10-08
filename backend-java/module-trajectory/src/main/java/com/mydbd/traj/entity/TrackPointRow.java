package com.mydbd.traj.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 轨迹结果列表行：轨迹点全字段 + 当前绑定司机姓名。
 */
@Data
public class TrackPointRow {

    private Long id;

    private String identityCode;

    private String plateNo;

    private LocalDateTime gpsTime;

    private BigDecimal lng;

    private BigDecimal lat;

    private Integer speed;

    private Integer direction;

    private Integer altitude;

    private Integer alarmFlag;

    private BigDecimal mileage;

    private LocalDateTime receiveTime;

    /** 当前绑定主司机姓名（无绑定为 null） */
    private String driverName;
}
