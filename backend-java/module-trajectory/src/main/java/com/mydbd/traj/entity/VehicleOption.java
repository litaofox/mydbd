package com.mydbd.traj.entity;

import lombok.Data;

/**
 * 车辆下拉选项（由轨迹点去重得到）
 */
@Data
public class VehicleOption {

    private String identityCode;
    private String plateNo;
}
