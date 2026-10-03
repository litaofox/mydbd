package com.mydbd.analysis.profile.constant;

import java.util.Map;

/**
 * F23 风险趋势与画像常量（MOD-ANA-002）。
 */
public final class ProfileConstants {

    private ProfileConstants() {
    }

    /** 时间范围非法（跨度>366 天或 start>=end） */
    public static final int ERR_RANGE = 46101;
    /** 维度/粒度/radius 非法 */
    public static final int ERR_DIM = 46102;

    public static final long MAX_SPAN_DAYS = 366;

    /** 趋势维度白名单 */
    public static final java.util.Set<String> TREND_DIMS = java.util.Set.of("global", "dept", "driver", "vehicle");
    /** 排行维度白名单 */
    public static final java.util.Set<String> RANK_DIMS = java.util.Set.of("driver", "vehicle");
    /** 粒度白名单 */
    public static final java.util.Set<String> GRANULARITIES = java.util.Set.of("day", "week", "month");
    /** 画像对象类型白名单 */
    public static final java.util.Set<String> CARD_TYPES = java.util.Set.of("vehicle", "driver", "dept");
    /** 黑点网格半径白名单（米） */
    public static final java.util.Set<Integer> RADIUS_WHITELIST = java.util.Set.of(100, 200, 500);

    /**
     * 事件码中文名兜底（字典 risk_event_code 仅 6 项，库内还有规则码/厂商码/国标数字码）。
     * 优先级：事件 title 众数 > 字典 > 本表 > 原码。
     */
    public static final Map<String, String> EVENT_NAME_FALLBACK = Map.ofEntries(
            Map.entry("SPEED_GENERAL", "一般超速"),
            Map.entry("SPEED_SEVERE", "严重超速"),
            Map.entry("FATIGUE_DRIVE", "疲劳驾驶"),
            Map.entry("DSM_FATIGUE", "终端信号·疲劳"),
            Map.entry("DSM_DISTRACTION", "终端信号·分心"),
            Map.entry("ADAS_FCW", "终端信号·前向碰撞风险"),
            Map.entry("ADAS_LDW", "终端信号·车道偏离"),
            Map.entry("COMBO_FATIGUE_SPEED", "疲劳叠加超速"),
            Map.entry("GEO_ENTER", "进入围栏"),
            Map.entry("GEO_EXIT", "离开围栏"),
            Map.entry("V_LDW", "车道偏离"),
            Map.entry("V_HMW", "车距过近"),
            Map.entry("V_FCW", "前向碰撞"),
            Map.entry("V_PCW", "未系安全带"),
            Map.entry("BEIDOU_SPEED", "北斗超速"),
            Map.entry("1", "紧急报警"),
            Map.entry("2", "超速报警"),
            Map.entry("3", "疲劳驾驶"),
            Map.entry("4", "危险预警"),
            Map.entry("5", "GNSS模块故障"),
            Map.entry("6", "通信模块故障"),
            Map.entry("7", "超速报警(预警)"));

    /** 黑点近似口径说明（接口与页面共用文案） */
    public static final String HOTSPOT_NOTE = "网格聚类近似口径，非真实路段；F10 路网匹配后升级";
}
