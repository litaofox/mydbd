package com.mydbd.risk.vo;

import lombok.Data;

/**
 * F21 感知—预警—干预—闭环 漏斗
 */
@Data
public class OrderFunnelVO {

    /** 感知：风险事件数 */
    private long eventTotal;

    /** 预警：生成工单数 */
    private long orderTotal;

    /** 干预：有干预记录的工单数 */
    private long interventionTotal;

    /** 闭环：已闭环工单数 */
    private long closedTotal;

    /** 干预率 = interventionTotal / orderTotal（百分比，保留1位） */
    private double interventionRate;

    /** 闭环率 = closedTotal / orderTotal（百分比，保留1位） */
    private double closeRate;
}
