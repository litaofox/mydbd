package com.mydbd.monitor.controller;

import com.mydbd.common.api.PageData;
import com.mydbd.common.api.Result;
import com.mydbd.common.audit.AuditLog;
import com.mydbd.common.security.RequiresPerm;
import com.mydbd.monitor.dto.AlarmQuery;
import com.mydbd.monitor.dto.ResolveRequest;
import com.mydbd.monitor.service.AlarmService;
import com.mydbd.monitor.vo.AlarmVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * F17 终端报警中心接口（MOD-MON-004 §4）。
 * 类级 alarm:view 覆盖全部 GET；confirm/resolve 方法级 alarm:handle + 审计留痕。
 */
@RestController
@RequestMapping("/api/alarm")
@RequiredArgsConstructor
@RequiresPerm("alarm:view")
public class AlarmController {

    private final AlarmService alarmService;

    /** 分页列表（车牌模糊/类型/状态/时间段） */
    @GetMapping("/page")
    public Result<PageData<AlarmVO>> page(AlarmQuery query) {
        return Result.ok(alarmService.page(query));
    }

    /** 待处理滚动（大屏/面板契约，§4.2） */
    @GetMapping("/latest")
    public Result<List<AlarmVO>> latest(@RequestParam(required = false) Integer limit) {
        return Result.ok(alarmService.latest(limit));
    }

    /** 统计（§4.3）：start/end 缺省=今日 */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats(@RequestParam(required = false) String start,
                                             @RequestParam(required = false) String end) {
        return Result.ok(alarmService.stats(start, end));
    }

    /** 报警类型下拉（§4.5） */
    @GetMapping("/types")
    public Result<List<Map<String, Object>>> types() {
        return Result.ok(alarmService.types());
    }

    /** 详情（§4.5）：附带 relatedRisks */
    @GetMapping("/{id}")
    public Result<AlarmVO> detail(@PathVariable Long id) {
        return Result.ok(alarmService.detail(id));
    }

    /** 确认 0→1（§4.4） */
    @PostMapping("/{id}/confirm")
    @RequiresPerm("alarm:handle")
    @AuditLog(module = "MONITOR", action = "HANDLE", actionName = "报警确认",
            objectType = "ALARM", objectId = "#id")
    public Result<AlarmVO> confirm(@PathVariable Long id) {
        return Result.ok(alarmService.confirm(id));
    }

    /** 解除 0/1→2（§4.4） */
    @PostMapping("/{id}/resolve")
    @RequiresPerm("alarm:handle")
    @AuditLog(module = "MONITOR", action = "HANDLE", actionName = "报警解除",
            objectType = "ALARM", objectId = "#id")
    public Result<AlarmVO> resolve(@PathVariable Long id, @RequestBody ResolveRequest request) {
        return Result.ok(alarmService.resolve(id, request));
    }
}
