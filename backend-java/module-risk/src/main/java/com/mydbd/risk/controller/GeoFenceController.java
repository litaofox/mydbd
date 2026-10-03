package com.mydbd.risk.controller;

import com.mydbd.common.api.PageData;
import com.mydbd.common.api.Result;
import com.mydbd.common.audit.AuditLog;
import com.mydbd.common.security.RequiresPerm;
import com.mydbd.risk.dto.RiskRequests.FenceSaveRequest;
import com.mydbd.risk.dto.RiskRequests.StatusRequest;
import com.mydbd.risk.entity.RiskGeoFence;
import com.mydbd.risk.service.GeoFenceService;
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
 * F18 电子围栏配置接口
 */
@RestController
@RequestMapping("/api/risk/fences")
@RequiredArgsConstructor
@RequiresPerm("risk:fence:view")
public class GeoFenceController {

    private final GeoFenceService fenceService;

    @GetMapping
    public Result<PageData<RiskGeoFence>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String fenceName,
            @RequestParam(required = false) String fenceType,
            @RequestParam(required = false) Integer status) {
        return Result.ok(fenceService.page(page, size, fenceName, fenceType, status));
    }

    @GetMapping("/all")
    public Result<List<RiskGeoFence>> all() {
        return Result.ok(fenceService.listAll());
    }

    @GetMapping("/{id}")
    public Result<RiskGeoFence> detail(@PathVariable Long id) {
        return Result.ok(fenceService.detail(id));
    }

    @PostMapping
    @RequiresPerm("risk:fence:edit")
    @AuditLog(module = "RISK", action = "CREATE", actionName = "新建电子围栏", objectType = "GEO_FENCE")
    public Result<String> create(@Valid @RequestBody FenceSaveRequest request) {
        return Result.ok(String.valueOf(fenceService.create(request)));
    }

    @PutMapping("/{id}")
    @RequiresPerm("risk:fence:edit")
    @AuditLog(module = "RISK", action = "UPDATE", actionName = "编辑电子围栏",
            objectType = "GEO_FENCE", objectId = "#id")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody FenceSaveRequest request) {
        fenceService.update(id, request);
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    @RequiresPerm("risk:fence:edit")
    @AuditLog(module = "RISK", action = "UPDATE", actionName = "启停电子围栏",
            objectType = "GEO_FENCE", objectId = "#id")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        fenceService.updateStatus(id, request.status());
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPerm("risk:fence:edit")
    @AuditLog(module = "RISK", action = "DELETE", actionName = "删除电子围栏",
            objectType = "GEO_FENCE", objectId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        fenceService.delete(id);
        return Result.ok();
    }
}
