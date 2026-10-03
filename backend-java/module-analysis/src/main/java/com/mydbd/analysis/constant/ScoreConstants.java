package com.mydbd.analysis.constant;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * F22 评分模型常量（MOD-ANA-001 §3）。
 * 等级边界（90/75/60/40）与 14-driving-score.sql 字典 remark 三处同值，改动需同步。
 */
public final class ScoreConstants {

    private ScoreConstants() {
    }

    /** 参与评分的事件白名单（F18 内置规则产出码） */
    public static final List<String> SCORE_EVENT_CODES = List.of(
            "SPEED_GENERAL", "SPEED_SEVERE", "FATIGUE_DRIVE",
            "DSM_FATIGUE", "DSM_DISTRACTION", "ADAS_FCW", "ADAS_LDW",
            "COMBO_FATIGUE_SPEED");

    /** 风险等级 → 单次扣分 */
    public static final Map<Integer, BigDecimal> LEVEL_DEDUCT = Map.of(
            3, new BigDecimal("8"),
            2, new BigDecimal("4"),
            1, new BigDecimal("2"));

    /** 急加减速单次扣分与单日封顶 */
    public static final BigDecimal HARD_DEDUCT = new BigDecimal("0.5");
    public static final BigDecimal HARD_CAP = new BigDecimal("5");

    /** 加速度阈值 m/s² */
    public static final BigDecimal ACCEL_THRESHOLD = new BigDecimal("2.5");
    /** 相邻点最大时间差（秒），超出视为段断开 */
    public static final int MAX_DT_SECONDS = 30;
    /** 低速漂移过滤：max(v1,v2) 最小 km/h */
    public static final int MIN_SPEED_KMH = 10;

    /** 样本门槛 */
    public static final int MIN_SAMPLE_POINTS = 200;
    public static final int MIN_DRIVING_MINUTES = 30;

    /** 重算最大跨度（天） */
    public static final int MAX_RECALC_DAYS = 92;

    /** 等级边界（MOD §3.4） */
    public static String levelOf(BigDecimal score) {
        if (score.compareTo(new BigDecimal("90")) >= 0) return "A";
        if (score.compareTo(new BigDecimal("75")) >= 0) return "B";
        if (score.compareTo(new BigDecimal("60")) >= 0) return "C";
        if (score.compareTo(new BigDecimal("40")) >= 0) return "D";
        return "E";
    }
}
