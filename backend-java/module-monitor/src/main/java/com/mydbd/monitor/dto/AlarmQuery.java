package com.mydbd.monitor.dto;

import lombok.Data;

/**
 * F17 报警分页入参（MOD-MON-004 §4.1）。
 * beginTime/endTime 为 ISO LocalDateTime 字符串，解析与校验在 AlarmService 完成。
 */
@Data
public class AlarmQuery {

    private long page = 1;

    private long size = 20;

    /** 车牌模糊 */
    private String plateNo;

    /** 报警类型精确 */
    private Integer typeId;

    /** 0/1/2；null=全部 */
    private Integer handleStatus;

    private String beginTime;

    private String endTime;
}
