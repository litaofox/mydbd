package com.mydbd.risk.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * F18 风控规则/围栏请求体
 */
public class RiskRequests {

    public record RuleSaveRequest(
            @NotBlank(message = "规则编码不能为空")
            @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,39}$", message = "规则编码需为 3~40 位大写字母/数字/下划线")
            String ruleCode,
            @NotBlank(message = "规则名称不能为空")
            String ruleName,
            @NotBlank(message = "规则类型不能为空")
            String ruleType,
            @NotNull(message = "风险等级不能为空")
            @Min(value = 1, message = "风险等级非法")
            @Max(value = 3, message = "风险等级非法")
            Integer riskLevel,
            Map<String, Object> params,
            @NotNull(message = "冷却时间不能为空")
            @Min(value = 0, message = "冷却时间非法")
            @Max(value = 86400, message = "冷却时间最长 86400 秒")
            Integer cooldownSec,
            Integer status,
            String remark) {
    }

    public record StatusRequest(
            @NotNull(message = "状态不能为空")
            @Min(value = 0, message = "状态非法")
            @Max(value = 1, message = "状态非法")
            Integer status) {
    }

    public record FenceSaveRequest(
            @NotBlank(message = "围栏名称不能为空")
            String fenceName,
            @NotBlank(message = "围栏形状不能为空")
            String fenceType,
            BigDecimal centerLng,
            BigDecimal centerLat,
            Integer radiusM,
            /** 多边形顶点 [[lng,lat],...]，首尾可不闭合（后端闭合） */
            List<List<BigDecimal>> points,
            @NotNull(message = "触发方向不能为空")
            @Min(1) @Max(3)
            Integer triggerDir,
            @NotNull @Min(1) @Max(3)
            Integer riskLevel,
            @NotNull @Min(0) @Max(86400)
            Integer cooldownSec,
            Integer status,
            String remark) {
    }
}
