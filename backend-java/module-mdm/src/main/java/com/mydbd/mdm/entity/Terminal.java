package com.mydbd.mdm.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mydbd.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 终端档案（对应 traj.traj_terminal）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("traj.traj_terminal")
public class Terminal extends BaseEntity {

    /** 终端唯一身份编号 */
    private String identityCode;

    private String tlMac;

    private String oemCode;

    private String tlModel;

    private String simAccount;

    /** JT808 / JT1078 / OTHER */
    private String protocolType;

    /** 1=一体机 2=分体机 4=视频智能终端 */
    private String equipmentType;

    private Integer videoChannel;

    /** 1=正常 2=维修停用 3=报废 */
    private Integer status;

    private String remark;

    /** 1=有效 0=失效 */
    private Integer validMark;

    /** 当前绑定车牌号（非持久化） */
    @TableField(exist = false)
    private String boundVehicleNo;
}
