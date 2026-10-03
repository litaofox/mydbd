package com.mydbd.risk.vo;

import java.util.List;
import java.util.Map;

/**
 * F18 引擎运行概览（规则页顶部统计条）
 */
public record EngineSummaryVO(
        long ruleTotal,
        long ruleEnabled,
        long fenceTotal,
        long fenceEnabled,
        long todayEventTotal,
        List<Map<String, Object>> todayByLevel,
        List<Map<String, Object>> todayTopRules) {
}
