package com.mydbd.monitor.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * F17 终端报警出参视图（MOD-MON-004 §4）。
 * id 转 String（ToStringSerializer），前端全链路 string。
 */
@Data
public class AlarmVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String plateNo;

    private String identityCode;

    private Integer typeId;

    /** JOIN traj.base_warn_type 得到；类型缺失时兜底"类型 {id}" */
    private String typeName;

    /** 1=红 2=橙 3=灰（着色依据） */
    private Integer gradeLevel;

    private LocalDateTime startWarnTime;

    private LocalDateTime endWarnTime;

    private String startLng;

    private String startLat;

    private String endLng;

    private String endLat;

    private Integer startSpeed;

    private Integer endSpeed;

    /** 0=停止 1=持续 */
    private Integer warnContinueMark;

    /** 0 待处理 / 1 已确认 / 2 已解除（已 COALESCE 归一） */
    private Integer handleStatus;

    private String handleResultCode;

    private String handleResultMsg;

    private String handler;

    private LocalDateTime updateDate;

    /** 详情接口附带：同车牌 ±30min 的 mon.risk_event 前 5 条（§3.3） */
    private List<Map<String, Object>> relatedRisks;
}
