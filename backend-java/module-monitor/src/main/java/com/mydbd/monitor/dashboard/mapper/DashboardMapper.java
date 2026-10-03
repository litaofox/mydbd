package com.mydbd.monitor.dashboard.mapper;

import com.mydbd.monitor.dashboard.vo.DashboardSummaryVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * F15 监控总览大屏聚合 SQL（MOD-MON-002 §4.1.1）：
 * 全 @Select 注解 SQL，同库跨 schema 只读直查 traj.* / mon.*，零跨模块 import。
 * 在线窗口分钟数一律经 make_interval(mins => #{n}) 参数化。
 */
public interface DashboardMapper {

    /** 在线窗口参数（traj.sys_config，键缺失/非法由 Service 回落缺省 5） */
    @Select("SELECT config_value FROM traj.sys_config WHERE config_key = #{key}")
    String selectConfigValue(@Param("key") String key);

    /** D-01/D-02：有效车辆总数 + 窗口内有定位的车辆数（vehicle→terminal→gps 链路） */
    @Select("""
            SELECT (SELECT count(*) FROM traj.traj_vehicle WHERE valid_mark = 1) AS "vehicleTotal",
                   count(*) FILTER (WHERE t.last_gps >= now() - make_interval(mins => #{windowMinutes})) AS "onlineCount"
              FROM (SELECT v.id, max(p.gps_time) AS last_gps
                      FROM traj.traj_vehicle v
                      JOIN traj.traj_vehicle_terminal vt ON vt.vehicle_id = v.id AND vt.status = 1 AND vt.valid_mark = 1
                      JOIN traj.traj_terminal tm ON tm.id = vt.terminal_id AND tm.valid_mark = 1
                      JOIN traj.traj_gps_point p ON p.identity_code = tm.identity_code
                     WHERE v.valid_mark = 1
                     GROUP BY v.id) t
            """)
    DashboardSummaryVO.Online selectOnline(@Param("windowMinutes") int windowMinutes);

    /** D-03：今日里程 = Σ每终端（当日 max(mileage) − min(mileage)），仅计非空 mileage */
    @Select("""
            SELECT COALESCE(sum(x.mx - x.mn), 0)
              FROM (SELECT p.identity_code, max(p.mileage) AS mx, min(p.mileage) AS mn
                      FROM traj.traj_gps_point p
                     WHERE p.gps_time >= #{dayStart} AND p.mileage IS NOT NULL
                     GROUP BY p.identity_code) x
            """)
    double selectTodayMileage(@Param("dayStart") LocalDateTime dayStart);

    /** D-06/D-07：工单积压计数 + 近 7 日闭环/新建数（FILTER 紧跟聚合函数） */
    @Select("""
            SELECT count(*) FILTER (WHERE status = 'PENDING')                AS "pending",
                   count(*) FILTER (WHERE status = 'PROCESSING')             AS "processing",
                   count(*) FILTER (WHERE overdue = 1 AND status <> 'CLOSED') AS "overdue",
                   count(*) FILTER (WHERE status = 'CLOSED' AND close_time >= now() - interval '7 days') AS "closed7d",
                   count(*) FILTER (WHERE create_date >= now() - interval '7 days')                      AS "created7d"
              FROM mon.risk_work_order
             WHERE valid_mark = 1
            """)
    DashboardSummaryVO.WorkOrder selectWorkOrder();

    /**
     * D-08：车队（部门）分布 TOP 8，按在线数降序。
     * 偏差说明：MOD 原文限定 dept_type=2，现库挂车部门均为 dept_type=1，
     * 严格过滤将恒空；按"全部有效部门"分组（实施记录已注明）。
     */
    @Select("""
            SELECT d.id AS "deptId", d.dept_name AS "deptName",
                   count(*) AS "total",
                   count(*) FILTER (WHERE o.last_gps >= now() - make_interval(mins => #{windowMinutes})) AS "online"
              FROM traj.traj_dept d
              JOIN traj.traj_vehicle v ON v.dept_id = d.id AND v.valid_mark = 1
              LEFT JOIN traj.traj_vehicle_terminal vt ON vt.vehicle_id = v.id AND vt.status = 1 AND vt.valid_mark = 1
              LEFT JOIN traj.traj_terminal tm ON tm.id = vt.terminal_id AND tm.valid_mark = 1
              LEFT JOIN (SELECT identity_code, max(gps_time) AS last_gps
                           FROM traj.traj_gps_point GROUP BY identity_code) o ON o.identity_code = tm.identity_code
             WHERE d.valid_mark = 1
             GROUP BY d.id, d.dept_name
             ORDER BY "online" DESC, "total" DESC
             LIMIT 8
            """)
    List<DashboardSummaryVO.FleetStat> selectFleetStats(@Param("windowMinutes") int windowMinutes);

    /** D-09a：按注册 city_code 的车辆数（空串=未知） */
    @Select("""
            SELECT COALESCE(NULLIF(v.city_code, ''), '') AS "cityCode", count(*) AS "vehicleCount"
              FROM traj.traj_vehicle v
             WHERE v.valid_mark = 1
             GROUP BY 1
            """)
    List<Map<String, Object>> selectRegionVehicleCounts();

    /** D-09b：按车辆注册 city_code 的当日风险事件数（risk_event.plate_no → traj_vehicle.vehicle_no） */
    @Select("""
            SELECT COALESCE(NULLIF(v.city_code, ''), '') AS "cityCode", count(*) AS "riskCount"
              FROM mon.risk_event e
              JOIN traj.traj_vehicle v ON v.vehicle_no = e.plate_no AND v.valid_mark = 1
             WHERE e.event_time >= #{dayStart}
             GROUP BY 1
            """)
    List<Map<String, Object>> selectRegionRiskCounts(@Param("dayStart") LocalDateTime dayStart);

    /** D-10：近 24h 风险事件 0.02°×0.02° 网格聚合，count 降序封顶 500 格 */
    @Select("""
            SELECT round(e.lng / 0.02) * 0.02 AS "lng",
                   round(e.lat / 0.02) * 0.02 AS "lat",
                   count(*) AS "count",
                   max(e.risk_level) AS "maxLevel"
              FROM mon.risk_event e
             WHERE e.event_time >= now() - interval '24 hours'
               AND e.lng IS NOT NULL AND e.lat IS NOT NULL
             GROUP BY 1, 2
             ORDER BY count(*) DESC
             LIMIT 500
            """)
    List<DashboardSummaryVO.HeatCell> selectRiskHeat();
}
