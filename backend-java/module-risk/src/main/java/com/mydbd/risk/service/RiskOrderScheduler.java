package com.mydbd.risk.service;

import com.mydbd.notify.api.NotifyEvent;
import com.mydbd.notify.service.NotifyService;
import com.mydbd.risk.entity.RiskWorkOrder;
import com.mydbd.risk.mapper.RiskWorkOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * F20 工单超时扫描：每分钟物化 overdue / escalated 标记并补系统升级日志。
 * F19 新建工单通知：以 id 游标扫描 Python 同事务补建的新工单，向在线坐席派发建单通知。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RiskOrderScheduler {

    private static final int CREATE_BATCH = 50;

    private final RiskOrderService orderService;
    private final RiskWorkOrderMapper orderMapper;
    private final NotifyService notifyService;

    /** 重启后从当前最大 id 开始，不回放历史工单 */
    private volatile long lastNotifiedOrderId = -1L;

    @Scheduled(fixedDelay = 60_000L, initialDelay = 20_000L)
    public void scan() {
        try {
            orderService.runTimeoutScan();
        } catch (Exception e) {
            log.warn("F20 工单超时扫描失败：{}", e.getMessage());
        }
        try {
            scanNewOrders();
        } catch (Exception e) {
            log.warn("F19 新建工单通知扫描失败：{}", e.getMessage());
        }
    }

    private void scanNewOrders() {
        if (lastNotifiedOrderId < 0) {
            lastNotifiedOrderId = orderMapper.selectMaxId();
            return;
        }
        List<RiskWorkOrder> fresh = orderMapper.selectCreatedAfter(lastNotifiedOrderId, CREATE_BATCH);
        if (fresh.isEmpty()) {
            return;
        }
        List<Long> operators = notifyService.receiversByMenu(NotifyService.MENU_OPERATOR);
        for (RiskWorkOrder order : fresh) {
            int lv = order.getRiskLevel() == null ? 2 : order.getRiskLevel();
            String title = "新风险工单：" + (order.getEventTitle() != null ? order.getEventTitle()
                    : order.getEventCode()) + "｜" + order.getPlateNo();
            String content = "工单 " + order.getOrderNo() + "（" + order.getPlateNo()
                    + "，" + levelName(lv) + "风险）已生成，请在时限内认领处置。";
            notifyService.dispatchAsync(new NotifyEvent(NotifyEvent.ORDER_CREATE,
                    NotifyEvent.BIZ_WORK_ORDER, order.getId(), title, content, lv,
                    order.getOrderNo(), order.getPlateNo()), operators);
            lastNotifiedOrderId = Math.max(lastNotifiedOrderId, order.getId());
        }
        log.info("F19 新建工单通知派发：{} 个", fresh.size());
    }

    private static String levelName(int lv) {
        return lv >= 3 ? "高" : lv == 2 ? "中" : "低";
    }
}
