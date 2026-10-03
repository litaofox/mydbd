package com.mydbd.audit.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 审计区间概览
 */
@Data
@AllArgsConstructor
public class AuditStatsVO {

    /** 区间操作总量 */
    private long total;
    /** 成功数 */
    private long successCount;
    /** 成功率（百分比，保留 1 位小数） */
    private double successRate;
    /** 登录失败次数 */
    private long loginFailCount;
    /** 今日操作数 */
    private long todayCount;
    /** 动作分布 */
    private List<NameCount> actionDist;
    /** 高频操作人 Top10 */
    private List<NameCount> topUsers;
}
