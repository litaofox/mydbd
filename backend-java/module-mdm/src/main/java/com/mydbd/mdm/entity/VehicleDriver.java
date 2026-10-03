package com.mydbd.mdm.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mydbd.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 车辆-驾驶员绑定关系（对应 traj.traj_vehicle_driver）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("traj.traj_vehicle_driver")
public class VehicleDriver extends BaseEntity {

    private Long vehicleId;

    private Long driverId;

    /** 1=主班 2=副班 */
    private Integer driverType;

    private LocalDateTime bindTime;

    private LocalDateTime unbindTime;

    /** 1=有效 0=已解绑 */
    private Integer status;

    private String remark;

    /** 1=有效 0=失效 */
    private Integer validMark;

    /** 关联展示字段（非持久化） */
    @TableField(exist = false)
    private String vehicleNo;

    @TableField(exist = false)
    private String driverName;

    @TableField(exist = false)
    private String contactPhone;

    @TableField(exist = false)
    private String licenceCategory;
}
