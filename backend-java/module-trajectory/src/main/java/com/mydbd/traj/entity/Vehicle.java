package com.mydbd.traj.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mydbd.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 车辆台账（对应 traj.traj_vehicle）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("traj.traj_vehicle")
public class Vehicle extends BaseEntity {

    private Long deptId;

    private String vehicleNo;

    private String vehiclePlateColor;

    private String vin;

    private String vehicleType;

    private Integer operationType;

    private String vehicleBrand;

    private String ownerName;

    private Integer validMark;
}
