package com.mydbd.risk.controller;

import com.mydbd.common.api.PageData;
import com.mydbd.common.api.Result;
import com.mydbd.common.audit.AuditLog;
import com.mydbd.common.security.RequiresPerm;
import com.mydbd.risk.dto.OrderRequests.AssignRequest;
import com.mydbd.risk.dto.OrderRequests.CloseRequest;
import com.mydbd.risk.dto.OrderRequests.ReopenRequest;
import com.mydbd.risk.dto.OrderRequests.SlaItem;
import com.mydbd.risk.dto.OrderRequests.SlaUpdateRequest;
import com.mydbd.risk.dto.OrderRequests.TransferRequest;
import com.mydbd.risk.entity.RiskIntervention;
import com.mydbd.risk.entity.RiskOrderSla;
import com.mydbd.risk.entity.RiskWorkOrder;
import com.mydbd.risk.service.InterventionService;
import com.mydbd.risk.service.RiskOrderService;
import com.mydbd.risk.vo.AssignableUserVO;
import com.mydbd.risk.vo.OrderDetailVO;
import com.mydbd.risk.vo.OrderFunnelVO;
import com.mydbd.risk.vo.OrderStatsVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * F20 处置工单流转接口
 */
@RestController
@RequestMapping("/api/risk/orders")
@RequiredArgsConstructor
@RequiresPerm("risk:order:view")
public class RiskOrderController {

    private final RiskOrderService orderService;
    private final InterventionService interventionService;

    @GetMapping
    public Result<PageData<RiskWorkOrder>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer riskLevel,
            @RequestParam(required = false) String closeResult,
            @RequestParam(required = false) String timeFlag,
            @RequestParam(required = false) String assigneeId,
            @RequestParam(required = false) String beginTime,
            @RequestParam(required = false) String endTime) {
        return Result.ok(orderService.page(page, size, keyword, status, riskLevel,
                closeResult, timeFlag, assigneeId, beginTime, endTime));
    }

    @GetMapping("/{id}")
    public Result<OrderDetailVO> detail(@PathVariable Long id) {
        return Result.ok(orderService.detail(id));
    }

    @GetMapping("/stats")
    public Result<OrderStatsVO> stats() {
        return Result.ok(orderService.stats());
    }

    /** F21 感知—预警—干预—闭环 漏斗 */
    @GetMapping("/funnel")
    public Result<OrderFunnelVO> funnel(
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime start,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime end) {
        return Result.ok(orderService.funnel(start, end));
    }

    /** F21 工单干预记录列表 */
    @GetMapping("/{id}/interventions")
    public Result<List<RiskIntervention>> interventions(@PathVariable Long id) {
        return Result.ok(interventionService.listByOrderId(id));
    }

    /** F21 新增加干预记录 */
    @PostMapping("/interventions")
    @RequiresPerm("risk:order:handle")
    @AuditLog(module = "RISK", action = "HANDLE", actionName = "新增干预记录",
            objectType = "INTERVENTION", objectId = "#data.orderId")
    public Result<Void> addIntervention(@RequestBody RiskIntervention data) {
        interventionService.add(data);
        return Result.ok();
    }

    @GetMapping("/assignable-users")
    public Result<List<AssignableUserVO>> assignableUsers() {
        return Result.ok(orderService.assignableUsers());
    }

    /** 事件页"去处理"：无工单则补建，返回工单对象供前端打开详情 */
    @PostMapping("/ensure/{eventId}")
    @RequiresPerm("risk:order:handle")
    public Result<RiskWorkOrder> ensure(@PathVariable Long eventId) {
        return Result.ok(orderService.ensureOrder(eventId));
    }

    @PostMapping("/{id}/claim")
    @RequiresPerm("risk:order:handle")
    @AuditLog(module = "RISK", action = "UPDATE", actionName = "认领工单",
            objectType = "RISK_ORDER", objectId = "#id")
    public Result<Void> claim(@PathVariable Long id) {
        orderService.claim(id);
        return Result.ok();
    }

    @PostMapping("/{id}/assign")
    @RequiresPerm("risk:order:assign")
    @AuditLog(module = "RISK", action = "UPDATE", actionName = "分派工单",
            objectType = "RISK_ORDER", objectId = "#id")
    public Result<Void> assign(@PathVariable Long id, @Valid @RequestBody AssignRequest request) {
        orderService.assign(id, request.userId(), request.remark());
        return Result.ok();
    }

    @PostMapping("/{id}/transfer")
    @RequiresPerm("risk:order:handle")
    @AuditLog(module = "RISK", action = "UPDATE", actionName = "转派工单",
            objectType = "RISK_ORDER", objectId = "#id")
    public Result<Void> transfer(@PathVariable Long id, @Valid @RequestBody TransferRequest request) {
        orderService.transfer(id, request.userId(), request.remark());
        return Result.ok();
    }

    @PostMapping("/{id}/close")
    @RequiresPerm("risk:order:handle")
    @AuditLog(module = "RISK", action = "UPDATE", actionName = "闭环工单",
            objectType = "RISK_ORDER", objectId = "#id")
    public Result<Void> close(@PathVariable Long id, @Valid @RequestBody CloseRequest request) {
        orderService.close(id, request.result(), request.remark());
        return Result.ok();
    }

    @PostMapping("/{id}/reopen")
    @RequiresPerm("risk:order:assign")
    @AuditLog(module = "RISK", action = "UPDATE", actionName = "重开工单",
            objectType = "RISK_ORDER", objectId = "#id")
    public Result<Void> reopen(@PathVariable Long id, @Valid @RequestBody ReopenRequest request) {
        orderService.reopen(id, request.remark());
        return Result.ok();
    }

    // ========== SLA 配置 ==========
    @GetMapping("/sla")
    public Result<List<RiskOrderSla>> sla() {
        return Result.ok(orderService.listSla());
    }

    @PutMapping("/sla")
    @RequiresPerm("risk:order:assign")
    @AuditLog(module = "RISK", action = "UPDATE", actionName = "修改工单SLA配置",
            objectType = "RISK_ORDER_SLA")
    public Result<Void> updateSla(@Valid @RequestBody SlaUpdateRequest request) {
        orderService.updateSla(request.items());
        return Result.ok();
    }
}
