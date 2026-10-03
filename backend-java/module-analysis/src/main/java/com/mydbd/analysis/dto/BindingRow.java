package com.mydbd.analysis.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 当日生效绑定行（identity → 车辆 → 主班司机）
 */
@Data
public class BindingRow {

    private String identityCode;
    private String plateNo;
    private Long deptId;
    private Long driverId;
    private LocalDateTime bindTime;
}
