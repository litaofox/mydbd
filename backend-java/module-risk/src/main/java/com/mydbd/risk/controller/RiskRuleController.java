package com.mydbd.risk.controller;

import com.mydbd.common.api.PageData;
import com.mydbd.common.api.Result;
import com.mydbd.common.audit.AuditLog;
import com.mydbd.common.security.RequiresPerm;
import com.mydbd.risk.dto.RiskRequests.RuleSaveRequest;
import com.mydbd.risk.dto.RiskRequests.StatusRequest;
import com.mydbd.risk.entity.RiskRule;
import com.mydbd.risk.service.RiskRuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * F18 风控规则配置接口
 */
@RestController
@RequestMapping("/api/risk/rules")
@RequiredArgsConstructor
@RequiresPerm("risk:rule:view")
public class RiskRuleController {

    private final RiskRuleService ruleService;

    @GetMapping
    public Result<PageData<RiskRule>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String ruleCode,
            @RequestParam(required = false) String ruleName,
            @RequestParam(required = false) String ruleType,
            @RequestParam(required = false) Integer status) {
        return Result.ok(ruleService.page(page, size, ruleCode, ruleName, ruleType, status));
    }

    /** 全量有效规则（风险事件页筛选下拉；停用规则也需要用于历史筛选——此接口返回全部有效行） */
    @GetMapping("/all")
    public Result<List<RiskRule>> all() {
        return Result.ok(ruleService.listAll());
    }

    @GetMapping("/{id}")
    public Result<RiskRule> detail(@PathVariable Long id) {
        return Result.ok(ruleService.detail(id));
    }

    @PostMapping
    @RequiresPerm("risk:rule:edit")
    @AuditLog(module = "RISK", action = "CREATE", actionName = "新建风控规则", objectType = "RISK_RULE")
    public Result<String> create(@Valid @RequestBody RuleSaveRequest request) {
        return Result.ok(String.valueOf(ruleService.create(request)));
    }

    @PutMapping("/{id}")
    @RequiresPerm("risk:rule:edit")
    @AuditLog(module = "RISK", action = "UPDATE", actionName = "编辑风控规则",
            objectType = "RISK_RULE", objectId = "#id")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody RuleSaveRequest request) {
        ruleService.update(id, request);
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    @RequiresPerm("risk:rule:edit")
    @AuditLog(module = "RISK", action = "UPDATE", actionName = "启停风控规则",
            objectType = "RISK_RULE", objectId = "#id")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        ruleService.updateStatus(id, request.status());
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPerm("risk:rule:edit")
    @AuditLog(module = "RISK", action = "DELETE", actionName = "删除风控规则",
            objectType = "RISK_RULE", objectId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        ruleService.delete(id);
        return Result.ok();
    }
}
