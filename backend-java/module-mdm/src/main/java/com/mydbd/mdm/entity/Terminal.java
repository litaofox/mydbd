package com.mydbd.mdm.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mydbd.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

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

    /** vps 网关车辆主键 truckId（离线事件锚点，可编辑） */
    private Long gatewayTruckId;

    /** 在线状态：0 离线 1 在线（网关心跳/鉴权/离线事件维护，只读透出） */
    private Integer onlineStatus;

    /** 最近心跳时间（网关维护，只读透出） */
    private LocalDateTime lastHeartbeatTime;

    /** 最近上线时间（网关维护，只读透出） */
    private LocalDateTime lastOnlineTime;

    /** 最近离线时间（网关维护，只读透出） */
    private LocalDateTime lastOfflineTime;

    private String remark;

    /** 1=有效 0=失效 */
    private Integer validMark;

    /** 当前绑定车牌号（非持久化） */
    @TableField(exist = false)
    private String boundVehicleNo;
}
