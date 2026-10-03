package com.mydbd.system.controller;

import com.mydbd.common.api.PageData;
import com.mydbd.common.api.Result;
import com.mydbd.common.audit.AuditLog;
import com.mydbd.common.security.RequiresPerm;
import com.mydbd.system.entity.SysConfig;
import com.mydbd.system.service.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 系统参数接口
 */
@RestController
@RequestMapping("/api/system/config")
@RequiredArgsConstructor
@RequiresPerm("system:config:view")
public class ConfigController {

    private final ConfigService configService;

    @GetMapping
    public Result<PageData<SysConfig>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword) {
        return Result.ok(configService.page(page, size, keyword));
    }

    @PostMapping
    @RequiresPerm("system:config:edit")
    @AuditLog(module = "SYSTEM", action = "CREATE", objectType = "CONFIG")
    public Result<Void> add(@RequestBody SysConfig config) {
        configService.add(config);
        return Result.ok();
    }

    @PutMapping("/{id}")
    @RequiresPerm("system:config:edit")
    @AuditLog(module = "SYSTEM", action = "UPDATE", objectType = "CONFIG", objectId = "#id")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysConfig config) {
        config.setId(id);
        configService.update(config);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPerm("system:config:edit")
    @AuditLog(module = "SYSTEM", action = "DELETE", objectType = "CONFIG", objectId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        configService.delete(id);
        return Result.ok();
    }

    /** 业务读取参数值（登录即可） */
    @GetMapping("/value/{key}")
    public Result<String> getValue(@PathVariable String key,
                                   @RequestParam(required = false) String defaultValue) {
        return Result.ok(configService.getValue(key, defaultValue));
    }
}
