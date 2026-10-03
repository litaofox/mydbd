package com.mydbd.risk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mydbd.risk.entity.RiskGeoFence;
import com.mydbd.risk.entity.RiskRule;
import com.mydbd.risk.mapper.RiskGeoFenceMapper;
import com.mydbd.risk.mapper.RiskRuleMapper;
import com.mydbd.risk.mapper.RiskStatsMapper;
import com.mydbd.risk.vo.EngineSummaryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * F18 引擎运行概览统计
 */
@Service
@RequiredArgsConstructor
public class EngineStatsService {

    private final RiskRuleMapper ruleMapper;
    private final RiskGeoFenceMapper fenceMapper;
    private final RiskStatsMapper statsMapper;

    public EngineSummaryVO summary() {
        long ruleTotal = ruleMapper.selectCount(new LambdaQueryWrapper<RiskRule>()
                .eq(RiskRule::getValidMark, 1));
        long ruleEnabled = ruleMapper.selectCount(new LambdaQueryWrapper<RiskRule>()
                .eq(RiskRule::getValidMark, 1).eq(RiskRule::getStatus, 1));
        long fenceTotal = fenceMapper.selectCount(new LambdaQueryWrapper<RiskGeoFence>()
                .eq(RiskGeoFence::getValidMark, 1));
        long fenceEnabled = fenceMapper.selectCount(new LambdaQueryWrapper<RiskGeoFence>()
                .eq(RiskGeoFence::getValidMark, 1).eq(RiskGeoFence::getStatus, 1));
        return new EngineSummaryVO(
                ruleTotal, ruleEnabled, fenceTotal, fenceEnabled,
                statsMapper.countTodayEvents(),
                statsMapper.countTodayByLevel(),
                statsMapper.todayTopRules());
    }
}
