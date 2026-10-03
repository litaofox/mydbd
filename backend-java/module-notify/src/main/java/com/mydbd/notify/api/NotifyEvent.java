package com.mydbd.notify.api;

/**
 * F19 通知事件载荷。
 *
 * @param eventType ORDER_CREATE / ORDER_ASSIGN / ORDER_ESCALATE / ORDER_CLOSE / SYSTEM
 * @param bizType   业务对象类型（WORK_ORDER 等）
 * @param bizId     业务对象 id
 * @param title     通知标题
 * @param content   通知正文
 * @param level     1 低 / 2 中 / 3 高（决定弹窗与外部渠道策略）
 * @param orderNo   关联单号（可空，用于展示与跳转）
 * @param plateNo   车牌（可空）
 */
public record NotifyEvent(String eventType, String bizType, Long bizId, String title,
                          String content, int level, String orderNo, String plateNo) {

    public static final String ORDER_CREATE = "ORDER_CREATE";
    public static final String ORDER_ASSIGN = "ORDER_ASSIGN";
    public static final String ORDER_ESCALATE = "ORDER_ESCALATE";
    public static final String ORDER_CLOSE = "ORDER_CLOSE";
    public static final String SYSTEM = "SYSTEM";

    public static final String BIZ_WORK_ORDER = "WORK_ORDER";
}
