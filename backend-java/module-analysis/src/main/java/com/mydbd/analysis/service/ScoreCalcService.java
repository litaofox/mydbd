package com.mydbd.analysis.service;

import com.mydbd.analysis.constant.ScoreConstants;
import com.mydbd.analysis.dto.BindingRow;
import com.mydbd.analysis.dto.EventAggRow;
import com.mydbd.analysis.dto.GpsPointRow;
import com.mydbd.analysis.entity.DriverScore;
import com.mydbd.analysis.mapper.DriverScoreMapper;
import com.mydbd.analysis.mapper.ScoreCalcMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * F22 评分计算核心（MOD-ANA-001 §3/§5.4）。
 * 定时与手动重算共用 recalc 入口，AtomicBoolean 互斥。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreCalcService {

    private final ScoreCalcMapper calcMapper;
    private final DriverScoreMapper scoreMapper;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public boolean isRunning() {
        return running.get();
    }

    /** 互斥锁：控制器提交任务前同步抢占，失败即 46002 */
    public boolean tryAcquire() {
        return running.compareAndSet(false, true);
    }

    public void release() {
        running.set(false);
    }

    /**
     * 区间重算（调用方已保证 start<=end、跨度合法、已持有互斥锁）。
     *
     * @param driverId 可选，仅重算该司机（按 driver 维度过滤产出）
     * @return 处理天数
     */
    public int recalc(LocalDate start, LocalDate end, Long driverId) {
        int days = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            calcDay(d, driverId);
            days++;
        }
        return days;
    }

    /** 单日计算（driverId 非空时仅处理该司机） */
    public void calcDay(LocalDate date, Long onlyDriverId) {
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

        // 1. 绑定：plate → 当日主班司机（多行取 bind_time 最大 = 首行）
        List<BindingRow> rows = calcMapper.selectBindings(dayStart, dayEnd);
        Map<String, BindingRow> bindings = new LinkedHashMap<>();
        for (BindingRow r : rows) {
            if (onlyDriverId != null && !onlyDriverId.equals(r.getDriverId())) {
                continue;
            }
            bindings.putIfAbsent(r.getPlateNo(), r); // SQL 已按 bindTime DESC，首行即最新
        }
        if (bindings.isEmpty()) {
            // 全日无任何生效绑定：清扫该日旧行（driverId 过滤时跳过）
            if (onlyDriverId == null) {
                scoreMapper.deleteDayExcept(date, List.of());
            }
            log.info("F22 评分 {}：无生效绑定，跳过", date);
            return;
        }

        // 2. 事件聚合（一次 SQL，按车牌×码）
        String codes = String.join(",", ScoreConstants.SCORE_EVENT_CODES);
        Map<String, List<EventAggRow>> eventsByPlate = new LinkedHashMap<>();
        for (EventAggRow e : calcMapper.selectEventAgg(dayStart, dayEnd, codes)) {
            eventsByPlate.computeIfAbsent(e.getPlateNo(), k -> new ArrayList<>()).add(e);
        }

        int produced = 0;
        int skipThreshold = 0;
        // 同司机多车合计（driver_id 维度 upsert）
        Map<Long, List<DriverScore>> perDriver = new LinkedHashMap<>();

        // 3. 逐车差分与扣分
        for (BindingRow b : bindings.values()) {
            List<GpsPointRow> points = calcMapper.selectGpsPoints(b.getPlateNo(), dayStart, dayEnd);
            HardEventStats hard = deriveHardEvents(points);

            DriverScore ds = new DriverScore();
            ds.setScoreDate(date);
            ds.setDriverId(b.getDriverId());
            ds.setIdentityCode(b.getIdentityCode());
            ds.setPlateNo(b.getPlateNo());
            ds.setDeptId(b.getDeptId());
            ds.setSamplePoints(points.size());
            ds.setFeatures(buildFeatures(eventsByPlate.getOrDefault(b.getPlateNo(), List.of()), hard));
            ds.setEventCount(hard.eventCount);

            if (points.size() < ScoreConstants.MIN_SAMPLE_POINTS
                    || hard.drivingMinutes < ScoreConstants.MIN_DRIVING_MINUTES) {
                // 门槛不足：不产出，并删除可能存在的旧行（重算一致性）
                scoreMapper.deleteDayDriver(date, b.getDriverId());
                skipThreshold++;
                continue;
            }

            BigDecimal deduct = (BigDecimal) ds.getFeatures().get("total_deduct");
            BigDecimal score = BigDecimal.valueOf(100).subtract(deduct).max(BigDecimal.ZERO)
                    .setScale(1, RoundingMode.HALF_UP);
            ds.setScore(score);
            ds.setLevel(ScoreConstants.levelOf(score));
            perDriver.computeIfAbsent(b.getDriverId(), k -> new ArrayList<>()).add(ds);
            produced++;
        }

        // 4. 同司机多车合并：特征与急加减速计数累加后统一扣分
        for (Map.Entry<Long, List<DriverScore>> en : perDriver.entrySet()) {
            List<DriverScore> list = en.getValue();
            if (list.size() == 1) {
                scoreMapper.upsert(list.get(0));
                continue;
            }
            DriverScore merged = mergeDriver(list, date);
            scoreMapper.upsert(merged);
        }

        // 5. 全日重算清扫：解绑/换车后该日不再归属的司机旧行删除（driverId 过滤时跳过，防误删他人）
        if (onlyDriverId == null) {
            scoreMapper.deleteDayExcept(date, new ArrayList<>(perDriver.keySet()));
        }

        log.info("F22 评分 {}：产出 {} 行，跳过（门槛不足 {}）", date, produced, skipThreshold);
    }

    // ============================== 急加减速派生 ==============================

    /**
     * GPS 速度差分 + 连续段合并（MOD-ANA-001 §3.3）。
     */
    static HardEventStats deriveHardEvents(List<GpsPointRow> points) {
        HardEventStats st = new HardEventStats();
        boolean inAccel = false, inBrake = false;
        for (int i = 1; i < points.size(); i++) {
            GpsPointRow p1 = points.get(i - 1);
            GpsPointRow p2 = points.get(i);
            if (p1.getSpeed() == null || p2.getSpeed() == null) {
                inAccel = false;
                inBrake = false;
                continue;
            }
            long dt = Duration.between(p1.getGpsTime(), p2.getGpsTime()).getSeconds();
            if (dt < 1 || dt > ScoreConstants.MAX_DT_SECONDS) {
                inAccel = false;
                inBrake = false;
                continue;
            }
            int v1 = p1.getSpeed(), v2 = p2.getSpeed();
            if (v1 > 0 && v2 > 0) {
                st.drivingSeconds += dt;
            }
            if (Math.max(v1, v2) < ScoreConstants.MIN_SPEED_KMH) {
                inAccel = false;
                inBrake = false;
                continue;
            }
            BigDecimal a = BigDecimal.valueOf(v2 - v1)
                    .divide(BigDecimal.valueOf(3.6), 6, RoundingMode.HALF_UP)
                    .divide(BigDecimal.valueOf(dt), 6, RoundingMode.HALF_UP);
            boolean accel = a.compareTo(ScoreConstants.ACCEL_THRESHOLD) >= 0;
            boolean brake = a.compareTo(ScoreConstants.ACCEL_THRESHOLD.negate()) <= 0;

            if (accel) {
                if (!inAccel) {
                    st.accel++;
                    inAccel = true;
                }
                inBrake = false;
            } else if (brake) {
                if (!inBrake) {
                    st.brake++;
                    inBrake = true;
                }
                inAccel = false;
            } else {
                inAccel = false;
                inBrake = false;
            }
        }
        st.drivingMinutes = (int) (st.drivingSeconds / 60);
        return st;
    }

    static class HardEventStats {
        int accel;
        int brake;
        long drivingSeconds;
        int drivingMinutes;
        int eventCount;
    }

    // ============================== 扣分与特征 ==============================

    /**
     * 构建 features（10 键全量 + driving_minutes + total_deduct），同时回填 eventCount。
     */
    private Map<String, Object> buildFeatures(List<EventAggRow> events, HardEventStats hard) {
        Map<String, Object> f = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        int eventCount = 0;

        // 事件按码计数与等级
        Map<String, EventAggRow> byCode = new LinkedHashMap<>();
        for (EventAggRow e : events) {
            byCode.put(e.getEventCode(), e);
        }
        for (String code : ScoreConstants.SCORE_EVENT_CODES) {
            EventAggRow e = byCode.get(code);
            int count = e == null ? 0 : e.getCnt();
            int lv = e == null || e.getLv() == null ? 2 : e.getLv();
            BigDecimal deduct = ScoreConstants.LEVEL_DEDUCT
                    .getOrDefault(lv, ScoreConstants.LEVEL_DEDUCT.get(2))
                    .multiply(BigDecimal.valueOf(count));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("count", count);
            item.put("deduct", deduct);
            f.put(code, item);
            total = total.add(deduct);
            eventCount += count;
        }

        // 急加减速（单日 5 分封顶）
        BigDecimal accelDeduct = ScoreConstants.HARD_DEDUCT
                .multiply(BigDecimal.valueOf(hard.accel)).min(ScoreConstants.HARD_CAP);
        BigDecimal brakeDeduct = ScoreConstants.HARD_DEDUCT
                .multiply(BigDecimal.valueOf(hard.brake)).min(ScoreConstants.HARD_CAP);
        f.put("HARD_ACCEL", Map.of("count", hard.accel, "deduct", accelDeduct));
        f.put("HARD_BRAKE", Map.of("count", hard.brake, "deduct", brakeDeduct));
        total = total.add(accelDeduct).add(brakeDeduct);

        f.put("driving_minutes", hard.drivingMinutes);
        f.put("total_deduct", total);
        hard.eventCount = eventCount;
        return f;
    }

    /**
     * 同司机多车合并：点/分钟/事件/急加减速计数累加，统一重算扣分与分数。
     */
    @SuppressWarnings("unchecked")
    private DriverScore mergeDriver(List<DriverScore> list, LocalDate date) {
        DriverScore first = list.get(0);
        DriverScore m = new DriverScore();
        m.setScoreDate(date);
        m.setDriverId(first.getDriverId());
        m.setIdentityCode(first.getIdentityCode());
        m.setPlateNo(first.getPlateNo());
        m.setDeptId(first.getDeptId());
        int sample = 0, events = 0, accel = 0, brake = 0, minutes = 0;
        Map<String, int[]> counts = new LinkedHashMap<>(); // code → [count]
        Map<String, BigDecimal> deducts = new LinkedHashMap<>();
        for (DriverScore ds : list) {
            sample += ds.getSamplePoints() == null ? 0 : ds.getSamplePoints();
            events += ds.getEventCount() == null ? 0 : ds.getEventCount();
            minutes += (Integer) ds.getFeatures().getOrDefault("driving_minutes", 0);
            for (String code : ScoreConstants.SCORE_EVENT_CODES) {
                Map<String, Object> item = (Map<String, Object>) ds.getFeatures().get(code);
                if (item == null) continue;
                counts.computeIfAbsent(code, k -> new int[1])[0] += ((Number) item.get("count")).intValue();
                deducts.merge(code, (BigDecimal) item.get("deduct"), BigDecimal::add);
            }
            Map<String, Object> ha = (Map<String, Object>) ds.getFeatures().get("HARD_ACCEL");
            Map<String, Object> hb = (Map<String, Object>) ds.getFeatures().get("HARD_BRAKE");
            if (ha != null) accel += ((Number) ha.get("count")).intValue();
            if (hb != null) brake += ((Number) hb.get("count")).intValue();
        }
        BigDecimal accelDeduct = ScoreConstants.HARD_DEDUCT
                .multiply(BigDecimal.valueOf(accel)).min(ScoreConstants.HARD_CAP);
        BigDecimal brakeDeduct = ScoreConstants.HARD_DEDUCT
                .multiply(BigDecimal.valueOf(brake)).min(ScoreConstants.HARD_CAP);

        Map<String, Object> f = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (String code : ScoreConstants.SCORE_EVENT_CODES) {
            int c = counts.getOrDefault(code, new int[1])[0];
            BigDecimal d = deducts.getOrDefault(code, BigDecimal.ZERO);
            f.put(code, Map.of("count", c, "deduct", d));
            total = total.add(d);
        }
        f.put("HARD_ACCEL", Map.of("count", accel, "deduct", accelDeduct));
        f.put("HARD_BRAKE", Map.of("count", brake, "deduct", brakeDeduct));
        f.put("driving_minutes", minutes);
        total = total.add(accelDeduct).add(brakeDeduct);
        f.put("total_deduct", total);

        BigDecimal score = BigDecimal.valueOf(100).subtract(total).max(BigDecimal.ZERO)
                .setScale(1, RoundingMode.HALF_UP);
        m.setFeatures(f);
        m.setSamplePoints(sample);
        m.setEventCount(events);
        m.setScore(score);
        m.setLevel(ScoreConstants.levelOf(score));
        return m;
    }
}
