package com.mydbd.mdm.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mydbd.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 车辆档案（对应 traj.traj_vehicle，MDM 全字段视图）
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

    /** 1=货运 2=客运 3=危化品 9=其他 */
    private Integer operationType;

    private String vehicleIndustry;

    private String roadLicenseNo;

    private String provinceCode;

    private String cityCode;

    private String countyCode;

    private String vehicleColor;

    private String vehicleBrand;

    private String ownerName;

    private String ownerPhone;

    private String remark;

    /** 1=有效 0=失效 */
    private Integer validMark;

    /** 所属组织名称（非持久化） */
    @TableField(exist = false)
    private String deptName;

    /** 当前绑定终端编号（非持久化） */
    @TableField(exist = false)
    private String terminalIdentity;

    /** 当前主班司机姓名（非持久化） */
    @TableField(exist = false)
    private String mainDriverName;
}
