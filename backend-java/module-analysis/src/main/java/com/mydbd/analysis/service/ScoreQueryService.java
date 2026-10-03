package com.mydbd.analysis.service;

import com.mydbd.analysis.mapper.DriverScoreMapper;
import com.mydbd.analysis.vo.ScoreVO;
import com.mydbd.analysis.vo.SummaryVO;
import com.mydbd.analysis.vo.TrendVO;
import com.mydbd.common.api.PageData;
import com.mydbd.common.security.UserContext;
import com.mydbd.common.security.UserInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * F22 评分查询（page/trend/summary），按登录人 deptScope 过滤。
 * deptScope 语义（IAM 约定）：null=全部；空集=不可见任何数据。
 */
@Service
@RequiredArgsConstructor
public class ScoreQueryService {

    private final DriverScoreMapper scoreMapper;

    public PageData<ScoreVO> page(long page, long size, String startDate, String endDate,
                                  String driverId, String plateNo, String level) {
        LocalDate[] range = resolveRange(startDate, endDate, 7);
        List<Long> scope = scopeOrNull();
        if (scope != null && scope.isEmpty()) {
            return new PageData<>(0, page, size, List.of());
        }
        long p = Math.max(1, page);
        long s = Math.min(Math.max(1, size), 100);
        Long drv = StringUtils.hasText(driverId) ? parseLong(driverId) : null;
        long total = scoreMapper.pageCount(range[0], range[1], drv,
                StringUtils.hasText(plateNo) ? plateNo.trim() : null,
                StringUtils.hasText(level) ? level.trim() : null, scope);
        List<ScoreVO> records = total == 0 ? List.of()
                : scoreMapper.pageQuery(range[0], range[1], drv,
                StringUtils.hasText(plateNo) ? plateNo.trim() : null,
                StringUtils.hasText(level) ? level.trim() : null,
                scope, s, (p - 1) * s);
        return new PageData<>(total, p, s, records);
    }

    public List<TrendVO> trend(String driverId, String startDate, String endDate) {
        Long drv = parseLong(driverId);
        LocalDate[] range = resolveRange(startDate, endDate, 30);
        long span = range[1].toEpochDay() - range[0].toEpochDay() + 1;
        if (span > 92) {
            range[0] = range[1].minusDays(91);
        }
        List<Long> scope = scopeOrNull();
        if (scope != null && scope.isEmpty()) {
            return List.of();
        }
        return scoreMapper.trend(drv, range[0], range[1], scope);
    }

    public SummaryVO summary(String startDate, String endDate) {
        LocalDate[] range = resolveRange(startDate, endDate, 7);
        List<Long> scope = scopeOrNull();
        SummaryVO vo = new SummaryVO();
        if (scope != null && scope.isEmpty()) {
            vo.setAvgScore(BigDecimal.ZERO);
            vo.setRecordCount(0);
            vo.setDriverCount(0);
            vo.setLevelDist(List.of());
            return vo;
        }
        SummaryVO base = scoreMapper.summaryBase(range[0], range[1], scope);
        vo.setAvgScore(base == null || base.getAvgScore() == null ? BigDecimal.ZERO : base.getAvgScore());
        vo.setRecordCount(base == null ? 0 : base.getRecordCount());
        vo.setDriverCount(base == null ? 0 : base.getDriverCount());
        vo.setLevelDist(scoreMapper.summaryLevelDist(range[0], range[1], scope));
        return vo;
    }

    /** 缺省最近 N 天 */
    private LocalDate[] resolveRange(String start, String end, int defaultDays) {
        LocalDate e = StringUtils.hasText(end) ? LocalDate.parse(end.trim()) : LocalDate.now();
        LocalDate s = StringUtils.hasText(start) ? LocalDate.parse(start.trim()) : e.minusDays(defaultDays - 1L);
        if (s.isAfter(e)) {
            LocalDate t = s;
            s = e;
            e = t;
        }
        return new LocalDate[]{s, e};
    }

    /** 返回 null=不限制；非 null（可能空集）=按集合过滤 */
    private List<Long> scopeOrNull() {
        UserInfo u = UserContext.get();
        if (u == null || u.allData()) {
            return null;
        }
        Set<Long> scope = u.deptScope();
        if (scope == null) {
            return null;
        }
        return new ArrayList<>(scope);
    }

    private Long parseLong(String v) {
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            throw new com.mydbd.common.exception.BizException(
                    com.mydbd.common.api.ErrorCode.BAD_REQUEST, "参数格式非法: " + v);
        }
    }
}
