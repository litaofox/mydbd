package com.mydbd.monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 终端报警信息（对应 traj.traj_warn_info）
 * F17 报警中心：补齐全列映射；该类同时被 F14 WS 广播，扩列为向后兼容超集。
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

    private LocalDateTime endWarnTime;

    private String startLng;

    private String startLat;

    private String endLng;

    private String endLat;

    private Integer startSpeed;

    private Integer endSpeed;

    private Integer typeId;

    /** 0=停止 1=持续 */
    private Integer warnContinueMark;

    private Long ruleId;

    /** 状态机：0 待处理 / 1 已确认 / 2 已解除（可空，读取侧 COALESCE 归一） */
    private Integer handleStatus;

    /** 解除结果码：00 属实 / 01 误报 / 02 未知 */
    private String handleResultCode;

    private String handleResultMsg;

    private String handler;

    private String creator;

    private LocalDateTime createDate;

    private String updater;

    private LocalDateTime updateDate;
}
