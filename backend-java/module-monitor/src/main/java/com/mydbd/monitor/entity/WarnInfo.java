package com.mydbd.monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 终端报警信息（对应 traj.traj_warn_info）
 */
@Data
@TableName("traj.traj_warn_info")
public class WarnInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sourceId;

    private String plateNo;

    private String identityCode;

    private LocalDateTime startWarnTime;

    private Integer typeId;

    private String startLng;

    private String startLat;

    private Integer startSpeed;

    private Integer handleStatus;
}
