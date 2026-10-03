package com.mydbd.mdm.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mydbd.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 驾驶员档案（对应 traj.traj_driver）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("traj.traj_driver")
public class Driver extends BaseEntity {

    private String driverName;

    /** 1=男 2=女 */
    private Integer sex;

    private String idcard;

    private String contactPhone;

    /** 驾驶证号（业务唯一身份） */
    private String licenseCode;

    private String licenceCategory;

    private String driverImg;

    /** 1=在岗 2=离岗 3=停用 */
    private Integer status;

    private String remark;

    /** 1=有效 0=失效 */
    private Integer validMark;

    /** 当前绑定车牌号（非持久化） */
    @TableField(exist = false)
    private String boundVehicleNo;
}
