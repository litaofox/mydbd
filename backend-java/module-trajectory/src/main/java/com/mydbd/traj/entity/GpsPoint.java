package com.mydbd.traj.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 轨迹点（对应 traj.traj_gps_point；PostGIS location 列不在此映射）
 */
@Data
@TableName("traj.traj_gps_point")
public class GpsPoint {

    @TableId(type = IdType.AUTO)
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
}
