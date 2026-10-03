package com.mydbd.mdm.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mydbd.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 车辆-终端绑定关系（对应 traj.traj_vehicle_terminal）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("traj.traj_vehicle_terminal")
public class VehicleTerminal extends BaseEntity {

    private Long vehicleId;

    private Long terminalId;

    /** 1=正式安装 2=临时换装 */
    private Integer bindType;

    private LocalDateTime bindTime;

    private LocalDateTime installTime;

    private String installer;

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
    private String terminalIdentity;

    @TableField(exist = false)
    private String tlModel;

    @TableField(exist = false)
    private String simAccount;
}
