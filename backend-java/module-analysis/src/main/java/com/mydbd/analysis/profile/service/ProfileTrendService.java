package com.mydbd.analysis.profile.service;

import com.mydbd.analysis.profile.dto.CodeBucketRow;
import com.mydbd.analysis.profile.dto.CodeCntRow;
import com.mydbd.analysis.profile.dto.PlateDriverRow;
import com.mydbd.analysis.profile.dto.RankRow;
import com.mydbd.analysis.profile.dto.TrendBucketRow;
import com.mydbd.analysis.profile.mapper.ProfileEventMapper;
import com.mydbd.analysis.profile.mapper.ProfileMasterMapper;
import com.mydbd.analysis.profile.mapper.ProfileScoreMapper;
import com.mydbd.analysis.profile.vo.RankingVO;
import com.mydbd.analysis.profile.vo.TrendVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * F23 趋势分析 + 对比排行（MOD-ANA-002 §3.1/§3.3）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileTrendService {

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final ProfileEventMapper eventMapper;
    private final ProfileMasterMapper masterMapper;
    private final ProfileScoreMapper scoreMapper;
    private final ProfileSupport support;

    public TrendVO trend(String dim, String id, LocalDateTime start, LocalDateTime end, String gran) {
        List<Long> scope = support.scopeOrNull();
        if (scope != null && scope.isEmpty()) {
            return emptyTrend(dim, id, gran, start, end);
        }
        List<String> plates = support.resolvePlates(dim, id, scope);
        if (plates != null && plates.isEmpty()) {
            return emptyTrend(dim, id, gran, start, end);
        }

        List<TrendBucketRow> rows = eventMapper.trendBuckets(gran, start, end, plates, scope);
        List<String> bucketKeys = bucketKeys(start, end, gran);
        Map<String, TrendBucketRow> byKey = new HashMap<>();
        for (TrendBucketRow r : rows) {
            byKey.put(bucketKey(r.getBucket(), gran), r);
        }

        TrendVO vo = new TrendVO();
        vo.setDim(dim);
        vo.setId("global".equals(dim) ? null : id);
        vo.setGranularity(gran);
        vo.setBuckets(bucketKeys);
        List<Long> total = new ArrayList<>();
        TrendVO.LevelSeries lv = new TrendVO.LevelSeries();
        List<Long> high = new ArrayList<>();
        List<Long> mid = new ArrayList<>();
        List<Long> low = new ArrayList<>();
        for (String key : bucketKeys) {
            TrendBucketRow r = byKey.get(key);
            total.add(r == null ? 0L : r.getTotal());
            high.add(r == null ? 0L : r.getHighCnt());
            mid.add(r == null ? 0L : r.getMidCnt());
            low.add(r == null ? 0L : r.getLowCnt());
        }
        vo.setTotal(total);
        lv.setHigh(high);
        lv.setMid(mid);
        lv.setLow(low);
        vo.setByLevel(lv);

        // Top5 事件码序列
        List<CodeCntRow> topRows = eventMapper.topCodes(start, end, plates, scope);
        List<TrendVO.Series> series = new ArrayList<>();
        if (!topRows.isEmpty()) {
            String codesCsv = String.join(",", topRows.stream().map(CodeCntRow::getEventCode).toList());
            List<CodeBucketRow> cbr = eventMapper.codeBuckets(gran, start, end, codesCsv, plates, scope);
            Map<String, Map<String, Long>> grid = new LinkedHashMap<>();
            for (CodeBucketRow r : cbr) {
                grid.computeIfAbsent(bucketKey(r.getBucket(), gran), k -> new HashMap<>())
                        .merge(r.getEventCode(), r.getCnt(), Long::sum);
            }
            Map<String, String> dict = support.riskEventDict();
            for (CodeCntRow t : topRows) {
                List<Long> s = new ArrayList<>();
                for (String key : bucketKeys) {
                    Map<String, Long> m = grid.get(key);
                    s.add(m == null ? 0L : m.getOrDefault(t.getEventCode(), 0L));
                }
                series.add(new TrendVO.Series(t.getEventCode(), support.eventName(t.getEventCode(), dict), s));
            }
        }
        vo.setTopCodes(series);
        return vo;
    }

    public RankingVO ranking(String dim, LocalDateTime start, LocalDateTime end, int limit) {
        List<Long> scope = support.scopeOrNull();
        RankingVO vo = new RankingVO();
        vo.setDim(dim);
        vo.setItems(List.of());
        if (scope != null && scope.isEmpty()) {
            return vo;
        }
        // 车牌榜多取一些，司机榜归集后可能减少
        List<RankRow> plateRows = eventMapper.rankingPlates(start, end, scope, "driver".equals(dim) ? 500 : limit);
        Map<String, PlateDriverRow> pdMap = new HashMap<>();
        for (PlateDriverRow p : masterMapper.allPlateDrivers()) {
            pdMap.put(p.getPlateNo(), p);
        }

        List<RankingVO.Item> items;
        if ("vehicle".equals(dim)) {
            items = new ArrayList<>();
            for (RankRow r : plateRows) {
                RankingVO.Item it = new RankingVO.Item();
                it.setId(r.getPlateNo());
                it.setName(r.getPlateNo());
                it.setSub(r.getIdentityCode());
                it.setEventCnt(r.getEventCnt());
                it.setHighCnt(r.getHighCnt());
                it.setWeightedScore(r.getWeightedScore());
                items.add(it);
            }
            items = items.subList(0, Math.min(items.size(), limit));
        } else {
            // dim=driver：plate→driver 当前绑定映射，未绑定司机的车不进榜
            Map<Long, RankingVO.Item> merged = new LinkedHashMap<>();
            for (RankRow r : plateRows) {
                PlateDriverRow pd = pdMap.get(r.getPlateNo());
                if (pd == null || pd.getDriverId() == null) {
                    continue;
                }
                RankingVO.Item it = merged.computeIfAbsent(pd.getDriverId(), k -> {
                    RankingVO.Item n = new RankingVO.Item();
                    n.setId(String.valueOf(k));
                    n.setName(pd.getDriverName());
                    n.setSub(r.getPlateNo());
                    return n;
                });
                it.setEventCnt(it.getEventCnt() + r.getEventCnt());
                it.setHighCnt(it.getHighCnt() + r.getHighCnt());
                it.setWeightedScore(it.getWeightedScore() + r.getWeightedScore());
            }
            items = new ArrayList<>(merged.values());
            items.sort((a, b) -> {
                int c = Long.compare(b.getWeightedScore(), a.getWeightedScore());
                return c != 0 ? c : Long.compare(b.getEventCnt(), a.getEventCnt());
            });
            items = items.subList(0, Math.min(items.size(), limit));
        }

        // 评分列（driver 榜按 driverIds；vehicle 榜按当前绑定司机）——F22 未就绪降级 null
        LocalDate sd = start.toLocalDate();
        LocalDate ed = end.toLocalDate().minusDays(1);
        if (ed.isBefore(sd)) {
            ed = sd;
        }
        Map<Long, Map<String, Object>> scoreByDriver = new HashMap<>();
        Map<String, Long> itemDriverId = new HashMap<>();
        try {
            List<Long> ids = new ArrayList<>();
            for (RankingVO.Item it : items) {
                Long driverId;
                if ("driver".equals(dim)) {
                    driverId = Long.valueOf(it.getId());
                } else {
                    PlateDriverRow pd = pdMap.get(it.getId());
                    driverId = pd == null ? null : pd.getDriverId();
                }
                if (driverId != null) {
                    itemDriverId.put(it.getId(), driverId);
                    ids.add(driverId);
                }
            }
            if (!ids.isEmpty()) {
                for (Map<String, Object> row : scoreMapper.latestByDriverIds(ids, sd, ed)) {
                    scoreByDriver.put(((Number) row.get("driverId")).longValue(), row);
                }
            }
        } catch (Exception e) {
            log.warn("F23 排行评分列降级（driver_score 不可用）: {}", e.getMessage());
        }
        int rank = 1;
        for (RankingVO.Item it : items) {
            it.setRank(rank++);
            Long driverId = itemDriverId.get(it.getId());
            Map<String, Object> sc = driverId == null ? null : scoreByDriver.get(driverId);
            if (sc != null) {
                it.setScore((BigDecimal) sc.get("score"));
                it.setLevel((String) sc.get("level"));
            }
        }
        vo.setItems(items);
        return vo;
    }

    private TrendVO emptyTrend(String dim, String id, String gran, LocalDateTime start, LocalDateTime end) {
        TrendVO vo = new TrendVO();
        vo.setDim(dim);
        vo.setId("global".equals(dim) ? null : id);
        vo.setGranularity(gran);
        List<String> keys = bucketKeys(start, end, gran);
        vo.setBuckets(keys);
        vo.setTotal(zeros(keys.size()));
        TrendVO.LevelSeries lv = new TrendVO.LevelSeries();
        lv.setHigh(zeros(keys.size()));
        lv.setMid(zeros(keys.size()));
        lv.setLow(zeros(keys.size()));
        vo.setByLevel(lv);
        vo.setTopCodes(List.of());
        return vo;
    }

    private List<Long> zeros(int n) {
        List<Long> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            l.add(0L);
        }
        return l;
    }

    /** 完整桶序列（空桶补零对齐用） */
    private List<String> bucketKeys(LocalDateTime start, LocalDateTime end, String gran) {
        List<String> keys = new ArrayList<>();
        LocalDate d = start.toLocalDate();
        LocalDate last = end.toLocalDate();
        // 左闭右开：end 恰为 00:00 时不含当日
        if (end.toLocalTime().equals(java.time.LocalTime.MIDNIGHT)) {
            last = last.minusDays(1);
        }
        switch (gran) {
            case "month": {
                LocalDate cur = d.withDayOfMonth(1);
                LocalDate stop = last.withDayOfMonth(1);
                while (!cur.isAfter(stop)) {
                    keys.add(cur.format(MONTH_FMT));
                    cur = cur.plusMonths(1);
                }
                break;
            }
            case "week": {
                LocalDate cur = d.minusDays(d.getDayOfWeek().getValue() - 1L);
                LocalDate stop = last.minusDays(last.getDayOfWeek().getValue() - 1L);
                while (!cur.isAfter(stop)) {
                    keys.add(cur.format(DAY_FMT));
                    cur = cur.plusWeeks(1);
                }
                break;
            }
            default: {
                LocalDate cur = d;
                while (!cur.isAfter(last)) {
                    keys.add(cur.format(DAY_FMT));
                    cur = cur.plusDays(1);
                }
            }
        }
        return keys;
    }

    /** SQL 桶时间 → 对齐 key */
    private String bucketKey(LocalDateTime bucket, String gran) {
        LocalDate d = bucket.toLocalDate();
        switch (gran) {
            case "month":
                return d.format(MONTH_FMT);
            case "week":
                return d.minusDays(d.getDayOfWeek().getValue() - 1L).format(DAY_FMT);
            default:
                return d.format(DAY_FMT);
        }
    }
}
