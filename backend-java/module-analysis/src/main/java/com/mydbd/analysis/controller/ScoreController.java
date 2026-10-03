package com.mydbd.analysis.controller;

import com.mydbd.analysis.constant.ScoreConstants;
import com.mydbd.analysis.dto.RecalcRequest;
import com.mydbd.analysis.mapper.ScoreCalcMapper;
import com.mydbd.analysis.service.ScoreCalcService;
import com.mydbd.analysis.service.ScoreQueryService;
import com.mydbd.analysis.vo.ScoreVO;
import com.mydbd.analysis.vo.SummaryVO;
import com.mydbd.analysis.vo.TrendVO;
import com.mydbd.common.api.PageData;
import com.mydbd.common.api.Result;
import com.mydbd.common.audit.AuditLog;
import com.mydbd.common.exception.BizException;
import com.mydbd.common.security.RequiresPerm;
import com.mydbd.common.security.UserContext;
import com.mydbd.common.security.UserInfo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * F22 驾驶行为评分接口（MOD-ANA-001 §6）。
 */
@Slf4j
@RestController
@RequestMapping("/api/analysis/score")
@RequiredArgsConstructor
@RequiresPerm("analysis:score:view")
public class ScoreController {

    private static final int ERR_PARAM = 46001;
    private static final int ERR_RUNNING = 46002;

    private final ScoreQueryService queryService;
    private final ScoreCalcService calcService;
    private final ScoreCalcMapper calcMapper;

    /** 重算单线程执行器（互斥由 ScoreCalcService.AtomicBoolean 保证） */
    private final ExecutorService recalcExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "score-recalc");
        t.setDaemon(true);
        return t;
    });

    @GetMapping("/page")
    public Result<PageData<ScoreVO>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String driverId,
            @RequestParam(required = false) String plateNo,
            @RequestParam(required = false) String level) {
        return Result.ok(queryService.page(page, size, startDate, endDate, driverId, plateNo, level));
    }

    @GetMapping("/trend")
    public Result<List<TrendVO>> trend(
            @RequestParam String driverId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return Result.ok(queryService.trend(driverId, startDate, endDate));
    }

    @GetMapping("/summary")
    public Result<SummaryVO> summary(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return Result.ok(queryService.summary(startDate, endDate));
    }

    /** 手动重算（异步 + 互斥，§5.3）：46001 参数非法 / 46002 任务进行中 / 40301 角色不足 */
    @PostMapping("/recalc")
    @AuditLog(module = "ANALYSIS", action = "UPDATE", actionName = "评分重算", objectType = "DRIVER_SCORE")
    public Result<Map<String, Object>> recalc(@Valid @RequestBody RecalcRequest req) {
        LocalDate start = parseDate(req.getStart(), "start");
        LocalDate end = parseDate(req.getEnd(), "end");
        if (start.isAfter(end)) {
            throw new BizException(ERR_PARAM, "start 不能晚于 end");
        }
        long span = end.toEpochDay() - start.toEpochDay() + 1;
        if (span > ScoreConstants.MAX_RECALC_DAYS) {
            throw new BizException(ERR_PARAM, "重算跨度不能超过 " + ScoreConstants.MAX_RECALC_DAYS + " 天");
        }
        if (end.isAfter(LocalDate.now())) {
            throw new BizException(ERR_PARAM, "end 不能晚于今天");
        }
        Long driverId = null;
        if (StringUtils.hasText(req.getDriverId())) {
            try {
                driverId = Long.parseLong(req.getDriverId().trim());
            } catch (NumberFormatException e) {
                throw new BizException(ERR_PARAM, "driverId 格式非法: " + req.getDriverId());
            }
        }

        checkRecalcRole();

        if (!calcService.tryAcquire()) {
            throw new BizException(ERR_RUNNING, "评分计算任务进行中，请稍后再试");
        }
        final LocalDate s = start;
        final LocalDate e = end;
        final Long d = driverId;
        try {
            recalcExecutor.submit(() -> {
                try {
                    int days = calcService.recalc(s, e, d);
                    log.info("F22 手动重算完成：{}~{} driver={} days={}", s, e, d, days);
                } catch (Exception ex) {
                    log.error("F22 手动重算失败：{}~{} driver={}", s, e, d, ex);
                } finally {
                    calcService.release();
                }
            });
        } catch (RuntimeException ex) {
            calcService.release();
            throw ex;
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accepted", true);
        data.put("start", s.toString());
        data.put("end", e.toString());
        return Result.ok(data);
    }

    /** superAdmin 或持有 SUPER_ADMIN/SAFE_ADMIN 角色（MOD §5.3） */
    private void checkRecalcRole() {
        UserInfo u = UserContext.get();
        if (u == null) {
            throw new BizException(com.mydbd.common.api.ErrorCode.UNAUTHORIZED);
        }
        if (u.superAdmin()) {
            return;
        }
        List<String> codes = calcMapper.selectRoleCodes(u.userId());
        if (codes.contains("SUPER_ADMIN") || codes.contains("SAFE_ADMIN")) {
            return;
        }
        throw new BizException(com.mydbd.common.api.ErrorCode.FORBIDDEN, "仅超级管理员/安全管理员可触发评分重算");
    }

    private LocalDate parseDate(String v, String field) {
        try {
            return LocalDate.parse(v.trim());
        } catch (DateTimeParseException e) {
            throw new BizException(ERR_PARAM, field + " 需为 yyyy-MM-dd: " + v);
        }
    }
}
