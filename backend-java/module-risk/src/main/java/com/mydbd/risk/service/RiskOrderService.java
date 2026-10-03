package com.mydbd.risk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mydbd.common.api.ErrorCode;
import com.mydbd.common.api.PageData;
import com.mydbd.common.exception.BizException;
import com.mydbd.common.security.UserContext;
import com.mydbd.common.security.UserInfo;
import com.mydbd.notify.api.NotifyEvent;
import com.mydbd.notify.service.NotifyService;
import com.mydbd.risk.dto.OrderRequests.SlaItem;
import com.mydbd.risk.entity.RiskEventRef;
import com.mydbd.risk.entity.RiskOrderLog;
import com.mydbd.risk.entity.RiskOrderSla;
import com.mydbd.risk.entity.RiskWorkOrder;
import com.mydbd.risk.mapper.RiskEventRefMapper;
import com.mydbd.risk.mapper.RiskOrderLogMapper;
import com.mydbd.risk.mapper.RiskOrderSlaMapper;
import com.mydbd.risk.mapper.RiskWorkOrderMapper;
import com.mydbd.risk.vo.AssignableUserVO;
import com.mydbd.risk.vo.OrderDetailVO;
import com.mydbd.risk.vo.OrderFunnelVO;
import com.mydbd.risk.vo.OrderStatsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * F20 处置工单状态机与流转服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskOrderService {

    public static final String PENDING = "PENDING";
    public static final String PROCESSING = "PROCESSING";
    public static final String CLOSED = "CLOSED";

    /** 闭环结果码 → 中文名 */
    private static final Map<String, String> RESULT_NAMES = Map.of(
            "PHONE_REMIND", "电话提醒",
            "EDUCATION", "安全教育",
            "REPORT_PENALTY", "通报处罚",
            "TRAFFIC_VIOLATION", "交通违法",
            "FALSE_ALARM", "误报排除"
    );
    private static final Set<String> VALID_RESULTS = RESULT_NAMES.keySet();
    private static final int DEFAULT_LIMIT_MIN = 60;
    private static final int DEFAULT_GRACE_MIN = 30;

    private final RiskWorkOrderMapper orderMapper;
    private final RiskOrderLogMapper logMapper;
    private final RiskOrderSlaMapper slaMapper;
    private final RiskEventRefMapper eventMapper;
    private final InterventionService interventionService;
    private final NotifyService notifyService;

    // ============================== 查询 ==============================

    public PageData<RiskWorkOrder> page(long page, long size, String keyword, String status,
                                        Integer riskLevel, String closeResult, String timeFlag,
                                        String assigneeId, String beginTime, String endTime) {
        LambdaQueryWrapper<RiskWorkOrder> qw = new LambdaQueryWrapper<>();
        qw.eq(RiskWorkOrder::getValidMark, 1);
        if (StringUtils.hasText(keyword)) {
            String k = keyword.trim();
            qw.and(w -> w.like(RiskWorkOrder::getOrderNo, k)
                    .or().like(RiskWorkOrder::getPlateNo, k)
                    .or().like(RiskWorkOrder::getEventTitle, k)
                    .or().like(RiskWorkOrder::getEventCode, k));
        }
        if (StringUtils.hasText(status)) {
            qw.eq(RiskWorkOrder::getStatus, status.trim());
        }
        if (riskLevel != null) {
            qw.eq(RiskWorkOrder::getRiskLevel, riskLevel);
        }
        if (StringUtils.hasText(closeResult)) {
            qw.eq(RiskWorkOrder::getCloseResult, closeResult.trim());
        }
        if (StringUtils.hasText(assigneeId)) {
            try {
                qw.eq(RiskWorkOrder::getAssigneeId, Long.parseLong(assigneeId.trim()));
            } catch (NumberFormatException e) {
                throw new BizException(ErrorCode.BAD_REQUEST, "负责人参数非法");
            }
        }
        if (StringUtils.hasText(beginTime)) {
            qw.ge(RiskWorkOrder::getEventTime, beginTime);
        }
        if (StringUtils.hasText(endTime)) {
            qw.le(RiskWorkOrder::getEventTime, endTime);
        }
        // 时限档位（实时口径，不等扫描）
        if (StringUtils.hasText(timeFlag)) {
            switch (timeFlag.trim()) {
                case "due" -> qw.apply("status <> 'CLOSED' AND deadline IS NOT NULL "
                        + "AND deadline > CURRENT_TIMESTAMP "
                        + "AND deadline <= CURRENT_TIMESTAMP + sla_limit_min * 0.2 * interval '1 minute'");
                case "overdue" -> qw.apply("status <> 'CLOSED' AND deadline IS NOT NULL "
                        + "AND deadline < CURRENT_TIMESTAMP");
                case "escalated" -> qw.apply("status <> 'CLOSED' AND deadline IS NOT NULL "
                        + "AND deadline + COALESCE(grace_min,0) * interval '1 minute' < CURRENT_TIMESTAMP");
                default -> { /* all 不过滤 */ }
            }
        }
        qw.last("ORDER BY escalated DESC, "
                + "(CASE status WHEN 'PENDING' THEN 0 WHEN 'PROCESSING' THEN 1 ELSE 2 END), "
                + "(deadline IS NOT NULL AND deadline < CURRENT_TIMESTAMP) DESC, "
                + "risk_level DESC, event_time ASC, id ASC");
        Page<RiskWorkOrder> result = orderMapper.selectPage(new Page<>(page, clampSize(size)), qw);
        return new PageData<>(result.getTotal(), result.getCurrent(), result.getSize(),
                result.getRecords());
    }

    public OrderDetailVO detail(Long id) {
        RiskWorkOrder order = getOrder(id);
        OrderDetailVO vo = new OrderDetailVO();
        vo.setOrder(order);
        vo.setEvent(eventMapper.selectById(order.getEventId()));
        vo.setLogs(logMapper.selectList(
                new LambdaQueryWrapper<RiskOrderLog>()
                        .eq(RiskOrderLog::getOrderId, id)
                        .orderByAsc(RiskOrderLog::getId)));
        return vo;
    }

    public OrderStatsVO stats() {
        Map<String, Object> row = orderMapper.selectStats();
        OrderStatsVO vo = new OrderStatsVO();
        if (row != null) {
            vo.setPendingCount(asLong(row.get("pendingcount")));
            vo.setProcessingCount(asLong(row.get("processingcount")));
            vo.setOverdueCount(asLong(row.get("overduecount")));
            vo.setEscalatedCount(asLong(row.get("escalatedcount")));
            vo.setClosedTodayCount(asLong(row.get("closedtodaycount")));
            long secs = asLong(row.get("avgcloseseconds"));
            vo.setAvgCloseSeconds(secs);
            vo.setAvgCloseMinutes(secs / 60);
        }
        return vo;
    }

    public List<AssignableUserVO> assignableUsers() {
        return orderMapper.selectAssignableUsers();
    }

    /** F21 感知—预警—干预—闭环 漏斗 */
    public OrderFunnelVO funnel(LocalDateTime start, LocalDateTime end) {
        OrderFunnelVO vo = new OrderFunnelVO();
        long eventTotal = eventMapper.selectCount(new LambdaQueryWrapper<RiskEventRef>()
                .ge(RiskEventRef::getCreateDate, start).lt(RiskEventRef::getCreateDate, end));
        long orderTotal = orderMapper.selectCount(new LambdaQueryWrapper<RiskWorkOrder>()
                .eq(RiskWorkOrder::getValidMark, 1)
                .ge(RiskWorkOrder::getCreateDate, start).lt(RiskWorkOrder::getCreateDate, end));
        long closedTotal = orderMapper.selectCount(new LambdaQueryWrapper<RiskWorkOrder>()
                .eq(RiskWorkOrder::getValidMark, 1).eq(RiskWorkOrder::getStatus, CLOSED)
                .ge(RiskWorkOrder::getCreateDate, start).lt(RiskWorkOrder::getCreateDate, end));
        long interventionTotal = interventionService.countIntervenedOrders(start, end);
        vo.setEventTotal(eventTotal);
        vo.setOrderTotal(orderTotal);
        vo.setInterventionTotal(interventionTotal);
        vo.setClosedTotal(closedTotal);
        vo.setInterventionRate(orderTotal == 0 ? 0 : Math.round(interventionTotal * 1000.0 / orderTotal) / 10.0);
        vo.setCloseRate(orderTotal == 0 ? 0 : Math.round(closedTotal * 1000.0 / orderTotal) / 10.0);
        return vo;
    }

    // ============================== 状态机 ==============================

    /** 事件页"去处理"：无工单则按 SLA 补建（并发由唯一约束兜底） */
    @Transactional
    public RiskWorkOrder ensureOrder(Long eventId) {
        RiskWorkOrder existing = orderMapper.selectOne(
                new LambdaQueryWrapper<RiskWorkOrder>().eq(RiskWorkOrder::getEventId, eventId));
        if (existing != null) {
            return existing;
        }
        RiskEventRef event = eventMapper.selectById(eventId);
        if (event == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "风险事件不存在");
        }
        RiskOrderSla sla = slaMapper.selectById(event.getRiskLevel() == null ? 2 : event.getRiskLevel());
        int limitMin = sla != null && sla.getLimitMin() != null ? sla.getLimitMin() : DEFAULT_LIMIT_MIN;
        int graceMin = sla != null && sla.getGraceMin() != null ? sla.getGraceMin() : DEFAULT_GRACE_MIN;
        LocalDateTime eventTime = event.getEventTime() != null ? event.getEventTime()
                : (event.getCreateDate() != null ? event.getCreateDate() : LocalDateTime.now());

        RiskWorkOrder order = new RiskWorkOrder();
        order.setEventId(eventId);
        order.setEventTitle(event.getTitle());
        order.setEventCode(event.getEventCode());
        order.setEventSource(event.getEventSource());
        order.setPlateNo(event.getPlateNo());
        order.setIdentityCode(event.getIdentityCode());
        order.setRiskLevel(event.getRiskLevel() == null ? 2 : event.getRiskLevel());
        order.setEventTime(eventTime);
        order.setStatus(PENDING);
        order.setDeadline(eventTime.plusMinutes(limitMin));
        order.setSlaLimitMin(limitMin);
        order.setGraceMin(graceMin);
        order.setValidMark(1);
        UserInfo user = UserContext.get();
        order.setCreator(user != null ? user.username() : "system");
        try {
            orderMapper.insertWithNo(order);
            writeLog(order, "CREATE", null, PENDING, null, null, null, null,
                    "风险事件补建工单", user);
        } catch (DuplicateKeyException e) {
            return orderMapper.selectOne(new LambdaQueryWrapper<RiskWorkOrder>()
                    .eq(RiskWorkOrder::getEventId, eventId));
        }
        return order;
    }

    @Transactional
    public void claim(Long id) {
        RiskWorkOrder order = getOrder(id);
        UserInfo user = currentUser();
        if (CLOSED.equals(order.getStatus())) {
            throw new BizException(ErrorCode.CONFLICT, "工单已闭环，不可认领");
        }
        if (order.getAssigneeId() != null && !order.getAssigneeId().equals(user.userId())) {
            throw new BizException(ErrorCode.CONFLICT, "工单已分派给他人，请走转派流程");
        }
        String old = order.getStatus();
        order.setStatus(PROCESSING);
        order.setAssigneeId(user.userId());
        order.setAssigneeName(user.realName());
        order.setAssignTime(LocalDateTime.now());
        order.setClaimTime(LocalDateTime.now());
        orderMapper.updateById(order);
        writeLog(order, "CLAIM", old, PROCESSING, null, null, null, user.realName(),
                "坐席认领", user);
    }

    @Transactional
    public void assign(Long id, Long userId, String remark) {
        RiskWorkOrder order = getOrder(id);
        UserInfo operator = currentUser();
        if (CLOSED.equals(order.getStatus())) {
            throw new BizException(ErrorCode.CONFLICT, "工单已闭环，不可分派");
        }
        AssignableUserVO target = findAssignable(userId);
        Long oldUserId = order.getAssigneeId();
        String oldName = order.getAssigneeName();
        String oldStatus = order.getStatus();
        order.setAssigneeId(userId);
        order.setAssigneeName(target.getName());
        order.setAssignTime(LocalDateTime.now());
        order.setStatus(PROCESSING);
        orderMapper.updateById(order);
        writeLog(order, "ASSIGN", oldStatus, PROCESSING, oldUserId, userId, oldName,
                target.getName(), StringUtils.hasText(remark) ? remark.trim() : "主管分派", operator);
        int lv = order.getRiskLevel() == null ? 2 : order.getRiskLevel();
        notifyOrder(NotifyEvent.ORDER_ASSIGN, order,
                "工单分派给你：" + orderTitle(order),
                "工单 " + order.getOrderNo() + "（" + order.getPlateNo() + "）已分派给你，请及时处置。",
                lv, List.of(userId));
    }

    @Transactional
    public void transfer(Long id, Long userId, String remark) {
        RiskWorkOrder order = getOrder(id);
        UserInfo operator = currentUser();
        if (CLOSED.equals(order.getStatus())) {
            throw new BizException(ErrorCode.CONFLICT, "工单已闭环，不可转派");
        }
        if (order.getAssigneeId() == null) {
            throw new BizException(ErrorCode.CONFLICT, "工单尚未认领，请直接认领");
        }
        AssignableUserVO target = findAssignable(userId);
        Long oldUserId = order.getAssigneeId();
        String oldName = order.getAssigneeName();
        order.setAssigneeId(userId);
        order.setAssigneeName(target.getName());
        order.setAssignTime(LocalDateTime.now());
        order.setStatus(PROCESSING);
        orderMapper.updateById(order);
        writeLog(order, "TRANSFER", order.getStatus(), PROCESSING, oldUserId, userId,
                oldName, target.getName(), remark, operator);
        int lv = order.getRiskLevel() == null ? 2 : order.getRiskLevel();
        notifyOrder(NotifyEvent.ORDER_ASSIGN, order,
                "工单转派给你：" + orderTitle(order),
                "工单 " + order.getOrderNo() + "（" + order.getPlateNo() + "）已转派给你：" + remark,
                lv, List.of(userId));
    }

    @Transactional
    public void close(Long id, String result, String remark) {
        RiskWorkOrder order = getOrder(id);
        UserInfo user = currentUser();
        if (CLOSED.equals(order.getStatus())) {
            throw new BizException(ErrorCode.CONFLICT, "工单已闭环，请勿重复操作");
        }
        if (!VALID_RESULTS.contains(result)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "处置结果非法");
        }
        // 仅负责人本人或持有分派/督办权限者可闭环
        boolean isOwner = order.getAssigneeId() != null
                && order.getAssigneeId().equals(user.userId());
        if (!isOwner && !user.hasPerm("risk:order:assign")) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅工单负责人或安全主管可闭环");
        }
        String old = order.getStatus();
        LocalDateTime now = LocalDateTime.now();
        order.setStatus(CLOSED);
        order.setCloseTime(now);
        order.setCloseResult(result);
        order.setCloseRemark(remark);
        if (order.getAssigneeId() == null) {
            order.setAssigneeId(user.userId());
            order.setAssigneeName(user.realName());
            order.setClaimTime(now);
        }
        orderMapper.updateById(order);

        String prefix = RESULT_NAMES.get(result) + "：";
        String handleRemark = prefix + remark;
        if (handleRemark.length() > 255) {
            handleRemark = handleRemark.substring(0, 255);
        }
        eventMapper.update(null, new LambdaUpdateWrapper<RiskEventRef>()
                .eq(RiskEventRef::getId, order.getEventId())
                .set(RiskEventRef::getHandleStatus, 1)
                .set(RiskEventRef::getHandleRemark, handleRemark));
        writeLog(order, "CLOSE", old, CLOSED, null, null, null, null,
                prefix + remark, user);
        // 闭环自动补记一条系统干预记录（非误报）
        interventionService.autoFromClose(order);
        // 分派人≠操作人时，通知负责人工单已闭环
        if (order.getAssigneeId() != null && !order.getAssigneeId().equals(user.userId())) {
            notifyOrder(NotifyEvent.ORDER_CLOSE, order,
                    "工单已闭环：" + orderTitle(order),
                    "工单 " + order.getOrderNo() + " 由 " + user.realName()
                            + " 闭环（" + RESULT_NAMES.get(result) + "）。",
                    1, List.of(order.getAssigneeId()));
        }
    }

    @Transactional
    public void reopen(Long id, String remark) {
        RiskWorkOrder order = getOrder(id);
        UserInfo user = currentUser();
        if (!CLOSED.equals(order.getStatus())) {
            throw new BizException(ErrorCode.CONFLICT, "仅已闭环工单可重新打开");
        }
        String targetStatus = order.getAssigneeId() != null ? PROCESSING : PENDING;
        order.setStatus(targetStatus);
        order.setCloseTime(null);
        order.setCloseResult(null);
        order.setCloseRemark(null);
        order.setReopenCount((order.getReopenCount() == null ? 0 : order.getReopenCount()) + 1);
        orderMapper.updateById(order);
        eventMapper.update(null, new LambdaUpdateWrapper<RiskEventRef>()
                .eq(RiskEventRef::getId, order.getEventId())
                .set(RiskEventRef::getHandleStatus, 0)
                .set(RiskEventRef::getHandleRemark, null));
        writeLog(order, "REOPEN", CLOSED, targetStatus, null, null, null, null,
                "重新打开：" + remark, user);
    }

    // ============================== SLA ==============================

    public List<RiskOrderSla> listSla() {
        return slaMapper.selectList(new LambdaQueryWrapper<RiskOrderSla>()
                .orderByAsc(RiskOrderSla::getRiskLevel));
    }

    @Transactional
    public void updateSla(List<SlaItem> items) {
        if (items == null || items.size() != 3
                || items.stream().map(SlaItem::riskLevel).collect(
                        java.util.stream.Collectors.toSet()).size() != 3) {
            throw new BizException(ErrorCode.BAD_REQUEST, "必须同时提交 低/中/高 三个等级的 SLA");
        }
        LocalDateTime now = LocalDateTime.now();
        for (SlaItem item : items) {
            RiskOrderSla sla = new RiskOrderSla();
            sla.setRiskLevel(item.riskLevel());
            sla.setLimitMin(item.limitMin());
            sla.setGraceMin(item.graceMin());
            sla.setUpdateDate(now);
            // 显式全字段更新（updateDate 由后端填）
            slaMapper.update(sla, new LambdaUpdateWrapper<RiskOrderSla>()
                    .eq(RiskOrderSla::getRiskLevel, item.riskLevel()));
        }
    }

    // ============================== 定时扫描 ==============================

    @Transactional
    public void runTimeoutScan() {
        orderMapper.markOverdue();
        orderMapper.markEscalated();
        // F19：先取本轮新升级工单（insertEscalateLogs 执行后这批即不再命中），再补日志
        List<RiskWorkOrder> escalated = orderMapper.selectNewlyEscalated();
        orderMapper.insertEscalateLogs();
        if (escalated.isEmpty()) {
            return;
        }
        List<Long> supervisors = notifyService.receiversByMenu(NotifyService.MENU_SUPERVISOR);
        for (RiskWorkOrder order : escalated) {
            List<Long> receivers = new java.util.ArrayList<>();
            if (order.getAssigneeId() != null) {
                receivers.add(order.getAssigneeId());
            }
            receivers.addAll(supervisors);
            notifyOrder(NotifyEvent.ORDER_ESCALATE, order,
                    "升级督办：" + orderTitle(order),
                    "工单 " + order.getOrderNo() + "（" + order.getPlateNo()
                            + "）已超过处置时限与宽限期，自动升级督办，请立即处理。",
                    3, receivers);
        }
        log.info("F19 升级督办通知派发：{} 个工单", escalated.size());
    }

    // ============================== 私有辅助 ==============================

    /** F19 通知派发（异步、吞异常，不影响主事务） */
    private void notifyOrder(String eventType, RiskWorkOrder order, String title,
                             String content, int level, List<Long> receivers) {
        if (receivers == null || receivers.isEmpty()) {
            return;
        }
        notifyService.dispatchAsync(new NotifyEvent(eventType, NotifyEvent.BIZ_WORK_ORDER,
                order.getId(), title, content, level, order.getOrderNo(), order.getPlateNo()),
                receivers);
    }

    private String orderTitle(RiskWorkOrder order) {
        String t = StringUtils.hasText(order.getEventTitle()) ? order.getEventTitle()
                : order.getEventCode();
        return (t == null ? "风险工单" : t) + "｜" + order.getPlateNo();
    }

    private RiskWorkOrder getOrder(Long id) {
        RiskWorkOrder order = orderMapper.selectById(id);
        if (order == null || order.getValidMark() == null || order.getValidMark() != 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "工单不存在");
        }
        return order;
    }

    private AssignableUserVO findAssignable(Long userId) {
        if (userId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "负责人不能为空");
        }
        return orderMapper.selectAssignableUsers().stream()
                .filter(u -> userId.toString().equals(u.getId().toString()))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.BAD_REQUEST,
                        "目标用户不存在、已停用或无工单处置权限"));
    }

    private UserInfo currentUser() {
        UserInfo user = UserContext.get();
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return user;
    }

    private void writeLog(RiskWorkOrder order, String action, String fromStatus, String toStatus,
                          Long fromUserId, Long toUserId, String fromName, String toName,
                          String remark, UserInfo operator) {
        RiskOrderLog log = new RiskOrderLog();
        log.setOrderId(order.getId());
        log.setAction(action);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setFromUserId(fromUserId);
        log.setToUserId(toUserId);
        log.setFromUserName(fromName);
        log.setToUserName(toName);
        log.setRemark(remark);
        if (operator != null) {
            log.setOperatorId(operator.userId());
            log.setOperatorName(operator.realName());
        }
        logMapper.insert(log);
    }

    private long clampSize(long size) {
        if (size < 1) {
            return 10;
        }
        return Math.min(size, 100);
    }

    private long asLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(value.toString());
    }
}
