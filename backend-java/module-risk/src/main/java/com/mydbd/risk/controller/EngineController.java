package com.mydbd.risk.controller;

import com.mydbd.common.api.Result;
import com.mydbd.common.security.RequiresPerm;
import com.mydbd.risk.service.EngineStatsService;
import com.mydbd.risk.vo.EngineSummaryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * F18 引擎运行概览
 */
@RestController
@RequestMapping("/api/risk/engine")
@RequiredArgsConstructor
@RequiresPerm("risk:rule:view")
public class EngineController {

    private final EngineStatsService statsService;

    @GetMapping("/summary")
    public Result<EngineSummaryVO> summary() {
        return Result.ok(statsService.summary());
    }
}
