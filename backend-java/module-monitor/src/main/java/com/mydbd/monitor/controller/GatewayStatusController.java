package com.mydbd.monitor.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.mydbd.common.api.Result;
import com.mydbd.common.security.RequiresPerm;
import com.mydbd.monitor.gateway.ProcessingClient;
import com.mydbd.monitor.mapper.GatewayStatusMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * vps 网关接入状态（GATEWAY-PLAN-001，权限码 system:gateway:view）。
 * 状态代理 processing /api/gateway/status 原样返回；未登记终端直查隔离区表。
 */
@RestController
@RequestMapping("/api/monitor/gateway")
@RequiredArgsConstructor
@RequiresPerm("system:gateway:view")
public class GatewayStatusController {

    private final ProcessingClient processingClient;
    private final GatewayStatusMapper gatewayStatusMapper;

    /** 网关消费运行状态（mode/enabled/running/stats/dlqCount/unknownCount 等，原样代理） */
    @GetMapping("/status")
    public Result<JsonNode> status() {
        return Result.ok(processingClient.getJson("/api/gateway/status"));
    }

    /** 未登记终端隔离区列表（traj.gateway_unknown_terminal，最近活跃优先） */
    @GetMapping("/unknown-terminals")
    public Result<List<Map<String, Object>>> unknownTerminals() {
        return Result.ok(gatewayStatusMapper.selectUnknownTerminals());
    }
}
