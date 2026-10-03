package com.mydbd.risk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mydbd.common.security.UserContext;
import com.mydbd.common.security.UserInfo;
import com.mydbd.risk.entity.RiskIntervention;
import com.mydbd.risk.entity.RiskWorkOrder;
import com.mydbd.risk.mapper.RiskInterventionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
/**
 * F21 坐席干预记录服务
 */
@Service
@RequiredArgsConstructor
public class InterventionService {

    /** 闭环结果 → 干预动作类型映射；FALSE_ALARM 不生成干预 */
    private static final Map<String, String> CLOSE_TO_ACTION = Map.of(
            "PHONE_REMIND", "PHONE_REMIND",
            "EDUCATION", "EDUCATION",
            "REPORT_PENALTY", "PENALTY",
            "TRAFFIC_VIOLATION", "PENALTY"
    );

    private final RiskInterventionMapper interventionMapper;

    public List<RiskIntervention> listByOrderId(Long orderId) {
        return interventionMapper.selectList(new LambdaQueryWrapper<RiskIntervention>()
                .eq(RiskIntervention::getOrderId, orderId)
                .orderByAsc(RiskIntervention::getCreateDate));
    }

    public void add(RiskIntervention intervention) {
        UserInfo user = UserContext.get();
        intervention.setOperatorId(user.userId());
        intervention.setOperatorName(user.realName());
        intervention.setSource("MANUAL");
        if (intervention.getCreateDate() == null) {
            intervention.setCreateDate(LocalDateTime.now());
        }
        if (intervention.getActionResult() == null) {
            intervention.setActionResult("SUCCESS");
        }
        interventionMapper.insert(intervention);
    }

    /** 统计指定时间范围内创建的工单中，存在干预记录的工单数 */
    public long countIntervenedOrders(LocalDateTime start, LocalDateTime end) {
        return interventionMapper.countIntervenedOrders(start, end);
    }

    /** 工单闭环时自动补记一条系统干预（非误报） */
    public void autoFromClose(RiskWorkOrder order) {
        String actionType = CLOSE_TO_ACTION.get(order.getCloseResult());
        if (actionType == null) return; // FALSE_ALARM 或未知不生成
        RiskIntervention iv = new RiskIntervention();
        iv.setOrderId(order.getId());
        iv.setEventId(order.getEventId());
        iv.setPlateNo(order.getPlateNo());
        iv.setIdentityCode(order.getIdentityCode());
        iv.setActionType(actionType);
        iv.setActionResult("SUCCESS");
        iv.setOperatorId(order.getAssigneeId());
        iv.setOperatorName(order.getAssigneeName());
        iv.setSource("SYSTEM");
        iv.setRemark(order.getCloseRemark());
        iv.setCreateDate(LocalDateTime.now());
        interventionMapper.insert(iv);
    }
}
