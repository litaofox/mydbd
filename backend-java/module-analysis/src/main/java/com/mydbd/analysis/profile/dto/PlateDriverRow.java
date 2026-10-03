package com.mydbd.analysis.profile.dto;

import lombok.Data;

/** 车牌→当前绑定主班司机映射行（排行 dim=driver / 司机画像归集） */
@Data
public class PlateDriverRow {
    private String plateNo;
    private Long driverId;
    private String driverName;
    private Long deptId;
}
