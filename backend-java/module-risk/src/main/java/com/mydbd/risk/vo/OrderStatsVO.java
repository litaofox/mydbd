package com.mydbd.risk.vo;

import lombok.Data;

/**
 * F20 工单看板统计
 */
@Data
public class OrderStatsVO {

    private long pendingCount;

    private long processingCount;

    private long overdueCount;

    private long escalatedCount;

    private long closedTodayCount;

    /** 当日已闭环工单平均闭环时长（秒） */
    private long avgCloseSeconds;

    /** 平均闭环时长（分钟，前端展示用） */
    private long avgCloseMinutes;
}
