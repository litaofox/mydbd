package com.mydbd.monitor.dashboard.controller;

import com.mydbd.common.api.Result;
import com.mydbd.common.security.RequiresPerm;
import com.mydbd.monitor.dashboard.service.DashboardService;
import com.mydbd.monitor.dashboard.vo.DashboardSummaryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * F15 监控总览大屏接口（MOD-MON-002 §4.1）。
 * 类级 monitor:dashboard:view（菜单 15 权限码，12-dashboard.sql）；只读聚合，无数据范围裁剪（与 F14 口径一致）。
 */
@RestController
@RequestMapping("/api/monitor/dashboard")
@RequiredArgsConstructor
@RequiresPerm("monitor:dashboard:view")
public class DashboardController {

    private final DashboardService dashboardService;

    /** 大屏聚合摘要：一次返回 在线/里程/工单/车队/区域/热力 六块 */
    @GetMapping("/summary")
    public Result<DashboardSummaryVO> summary() {
        return Result.ok(dashboardService.summary());
    }
}
