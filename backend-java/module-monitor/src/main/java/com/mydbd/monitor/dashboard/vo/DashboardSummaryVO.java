package com.mydbd.monitor.dashboard.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * F15 监控总览大屏聚合摘要（MOD-MON-002 §4.1）。
 * Long id 一律 ToStringSerializer（项目约定）；比率类字段可为 null（分母 0 → 前端显示"—"）。
 */
@Data
public class DashboardSummaryVO {

    private Online online;
    private Mileage mileage;
    private WorkOrder workOrder;
    private List<FleetStat> fleetStats;
    private List<RegionStat> regionStats;
    private List<HeatCell> riskHeat;
    private LocalDateTime serverTime;

    /** D-01/D-02 在线口径 */
    @Data
    public static class Online {
        private long vehicleTotal;
        private long onlineCount;
        /** 0~1，vehicleTotal=0 时 null */
        private Double onlineRate;
        /** 生效的在线窗口分钟数（sys_config 缺省回落 5） */
        private int windowMinutes;
    }

    /** D-03 今日里程（里程表差值近似合计，km） */
    @Data
    public static class Mileage {
        private double todayMileage;
    }

    /** D-06/D-07 工单积压与闭环率 */
    @Data
    public static class WorkOrder {
        private long pending;
        private long processing;
        private long overdue;
        private long closed7d;
        private long created7d;
        /** 0~1，created7d=0 时 null */
        private Double closeRate;
    }

    /** D-08 车队分布 TOP 8 */
    @Data
    public static class FleetStat {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long deptId;
        private String deptName;
        private long total;
        private long online;
    }

    /** D-09 区域分布 TOP 8（注册地 city_code 口径） */
    @Data
    public static class RegionStat {
        private String cityCode;
        private String cityName;
        private long vehicleCount;
        private long riskCount;
    }

    /** D-10 风险热力网格（0.02°×0.02°，≤500 格） */
    @Data
    public static class HeatCell {
        private double lng;
        private double lat;
        private long count;
        /** 格内最高风险等级 1低/2中/3高 */
        private int maxLevel;
    }
}
