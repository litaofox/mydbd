package com.mydbd.risk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mydbd.common.api.ErrorCode;
import com.mydbd.common.api.PageData;
import com.mydbd.common.exception.BizException;
import com.mydbd.risk.dto.RiskRequests.RuleSaveRequest;
import com.mydbd.risk.entity.RiskRule;
import com.mydbd.risk.mapper.RiskRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * F18 风控规则配置服务
 */
@Service
@RequiredArgsConstructor
public class RiskRuleService {

    public static final Set<String> RULE_TYPES = Set.of("SPEED", "FATIGUE", "SIGNAL", "COMBO");
    /** SIGNAL 类允许的信号码（与终端 DSM/ADAS 通道一致） */
    public static final Set<String> SIGNAL_CODES =
            Set.of("DSM_FATIGUE", "DSM_DISTRACTION", "ADAS_FCW", "ADAS_LDW");

    private final RiskRuleMapper ruleMapper;

    public PageData<RiskRule> page(long page, long size, String ruleCode, String ruleName,
                                   String ruleType, Integer status) {
        LambdaQueryWrapper<RiskRule> qw = new LambdaQueryWrapper<RiskRule>()
                .eq(RiskRule::getValidMark, 1)
                .like(StringUtils.hasText(ruleCode), RiskRule::getRuleCode, ruleCode)
                .like(StringUtils.hasText(ruleName), RiskRule::getRuleName, ruleName)
                .eq(StringUtils.hasText(ruleType), RiskRule::getRuleType, ruleType)
                .eq(status != null, RiskRule::getStatus, status)
                .orderByAsc(RiskRule::getId);
        Page<RiskRule> result = ruleMapper.selectPage(new Page<>(page, clampSize(size)), qw);
        return new PageData<>(result.getTotal(), result.getCurrent(), result.getSize(),
                result.getRecords());
    }

    /** 全量有效规则（事件页筛选用，含停用项另行调用方不过滤） */
    public List<RiskRule> listAll() {
        return ruleMapper.selectList(new LambdaQueryWrapper<RiskRule>()
                .eq(RiskRule::getValidMark, 1)
                .orderByAsc(RiskRule::getId));
    }

    public RiskRule detail(Long id) {
        RiskRule rule = ruleMapper.selectById(id);
        if (rule == null || rule.getValidMark() == null || rule.getValidMark() != 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "规则不存在");
        }
        return rule;
    }

    public Long create(RuleSaveRequest req) {
        String type = req.ruleType() == null ? "" : req.ruleType().trim().toUpperCase();
        String code = req.ruleCode().trim();
        if (!RULE_TYPES.contains(type)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "规则类型非法");
        }
        if ("SIGNAL".equals(type) && !SIGNAL_CODES.contains(code)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "信号码规则编码必须是四类 DSM/ADAS 信号码之一");
        }
        validateParams(type, req.params());
        if (ruleMapper.selectCount(new LambdaQueryWrapper<RiskRule>()
                .eq(RiskRule::getValidMark, 1)
                .eq(RiskRule::getRuleCode, code)) > 0) {
            throw new BizException(ErrorCode.CONFLICT, "规则编码已存在：" + code);
        }
        RiskRule rule = new RiskRule();
        rule.setRuleCode(code);
        rule.setRuleName(req.ruleName().trim());
        rule.setRuleType(type);
        rule.setEventCode(code);
        rule.setRiskLevel(req.riskLevel());
        rule.setParams(req.params());
        rule.setCooldownSec(req.cooldownSec());
        rule.setStatus(req.status() == null ? 1 : req.status());
        rule.setBuiltIn(0);
        rule.setValidMark(1);
        rule.setRemark(req.remark());
        ruleMapper.insert(rule);
        return rule.getId();
    }

    public void update(Long id, RuleSaveRequest req) {
        RiskRule rule = detail(id);
        // 编码/类型/事件码创建后不可变（内置与自定义同此约束）
        validateParams(rule.getRuleType(), req.params());
        rule.setRuleName(req.ruleName().trim());
        rule.setRiskLevel(req.riskLevel());
        rule.setParams(req.params());
        rule.setCooldownSec(req.cooldownSec());
        if (req.status() != null) {
            rule.setStatus(req.status());
        }
        rule.setRemark(req.remark());
        ruleMapper.updateById(rule);
    }

    public void updateStatus(Long id, Integer status) {
        RiskRule rule = detail(id);
        rule.setStatus(status);
        ruleMapper.updateById(rule);
    }

    public void delete(Long id) {
        RiskRule rule = detail(id);
        if (rule.getBuiltIn() != null && rule.getBuiltIn() == 1) {
            throw new BizException(ErrorCode.CONFLICT, "内置规则不可删除");
        }
        rule.setValidMark(0);
        ruleMapper.updateById(rule);
    }

    // ---- 参数校验（按类型）----

    private void validateParams(String type, Map<String, Object> params) {
        if (params == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "规则参数不能为空");
        }
        switch (type) {
            case "SPEED" -> requireRange(params, "speedKmh", 1, 220, "速度阈值需在 1~220 km/h 之间");
            case "FATIGUE" -> {
                requireRange(params, "continuousMin", 1, 1440, "连续驾驶上限需在 1~1440 分钟之间");
                requireRange(params, "gapMin", 1, 120, "中断间隔需在 1~120 分钟之间");
            }
            case "COMBO" -> {
                requireRange(params, "windowMin", 1, 720, "组合窗口需在 1~720 分钟之间");
                requireRange(params, "speedKmh", 1, 220, "速度阈值需在 1~220 km/h 之间");
            }
            case "SIGNAL" -> { /* 无阈值参数 */ }
            default -> throw new BizException(ErrorCode.BAD_REQUEST, "规则类型非法");
        }
    }

    private void requireRange(Map<String, Object> params, String key,
                              double min, double max, String message) {
        double v = asDouble(params.get(key));
        if (Double.isNaN(v) || v < min || v > max) {
            throw new BizException(ErrorCode.BAD_REQUEST, message);
        }
    }

    private double asDouble(Object o) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (Exception e) {
            return Double.NaN;
        }
    }

    private long clampSize(long size) {
        if (size < 1) return 10;
        if (size > 200) return 200;
        return size;
    }
}
