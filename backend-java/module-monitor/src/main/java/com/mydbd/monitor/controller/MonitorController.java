package com.mydbd.monitor.controller;

import com.mydbd.common.api.PageData;
import com.mydbd.common.api.Result;
import com.mydbd.common.audit.AuditLog;
import com.mydbd.common.security.RequiresPerm;
import com.mydbd.monitor.entity.RiskEvent;
import com.mydbd.monitor.entity.VideoAnalysis;
import com.mydbd.monitor.entity.WarnInfo;
import com.mydbd.monitor.service.MonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 实时监控与风险预警接口
 */
@RestController
@RequestMapping("/api/monitor")
@RequiredArgsConstructor
public class MonitorController {

    private final MonitorService monitorService;

    /** 监控总览指标 */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return Result.ok(monitorService.overview());
    }

    /** 风险事件分页列表 */
    @GetMapping("/risks")
    public Result<PageData<RiskEvent>> risks(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String eventSource,
            @RequestParam(required = false) Integer riskLevel,
            @RequestParam(required = false) Integer handleStatus,
            @RequestParam(required = false) Long ruleId,
            @RequestParam(required = false) String plateNo,
            @RequestParam(required = false) String cityCode) {
        return Result.ok(monitorService.pageRisks(page, size, eventSource, riskLevel, handleStatus, ruleId, plateNo, cityCode));
    }

    /** 处置风险事件（功能权限与工单处置同码：风险闭环的轻量入口） */
    @PostMapping("/risks/{id}/handle")
    @RequiresPerm("risk:order:handle")
    @AuditLog(module = "MONITOR", action = "HANDLE", actionName = "处置风险事件",
            objectType = "RISK_EVENT", objectId = "#id")
    public Result<Void> handle(@PathVariable Long id, @RequestBody HandleRequest request) {
        monitorService.handleRisk(id, request.remark());
        return Result.ok();
    }

    /** 风险类型分布（饼图） */
    @GetMapping("/risks/type-stats")
    public Result<List<Map<String, Object>>> typeStats() {
        return Result.ok(monitorService.riskTypeStats(null, null));
    }

    /** 终端报警列表 */
    @GetMapping("/warnings")
    public Result<List<WarnInfo>> warnings() {
        return Result.ok(monitorService.listWarnings());
    }

    /** 视频分析任务列表 */
    @GetMapping("/video-analyses")
    public Result<List<VideoAnalysis>> videoAnalyses() {
        return Result.ok(monitorService.listVideoAnalyses());
    }

    /** 处置请求体 */
    public record HandleRequest(String remark) {
    }
}
