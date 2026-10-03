package com.mydbd.analysis.mapper;

import com.mydbd.analysis.dto.BindingRow;
import com.mydbd.analysis.dto.EventAggRow;
import com.mydbd.analysis.dto.GpsPointRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * F22 评分计算输入（跨 schema 只读：risk_event / gps / 绑定）。
 * 关联键统一用 plate_no（vehicle_no）：risk_event.identity_code 多为空、
 * gps 与终端表 identity 体系不一致，车牌是唯一稳定业务键（MOD-ANA-001 §3.5 修订）。
 */
@Mapper
public interface ScoreCalcMapper {

    /**
     * 当日生效主班司机绑定（时间窗口径，重算历史日按当时绑定生效）。
     * 同一车牌命中多行（跨日换班）时取 bind_time 最大行——由 Service 端去重。
     */
    @Select("""
            SELECT v.vehicle_no     AS plateNo,
                   t.identity_code  AS identityCode,
                   v.dept_id        AS deptId,
                   vd.driver_id     AS driverId,
                   COALESCE(vd.bind_time, vt.bind_time, '-infinity'::timestamp) AS bindTime
            FROM traj.traj_vehicle_terminal vt
            JOIN traj.traj_vehicle v         ON v.id = vt.vehicle_id AND v.valid_mark = 1
            JOIN traj.traj_terminal t        ON t.id = vt.terminal_id AND t.valid_mark = 1
            JOIN traj.traj_vehicle_driver vd ON vd.vehicle_id = v.id AND vd.driver_type = 1 AND vd.valid_mark = 1
            JOIN traj.traj_driver d          ON d.id = vd.driver_id AND d.valid_mark = 1
            WHERE vt.valid_mark = 1
              AND (vt.bind_time   IS NULL OR vt.bind_time   < #{dayEnd})
              AND (vt.unbind_time IS NULL OR vt.unbind_time >= #{dayStart})
              AND (vd.bind_time   IS NULL OR vd.bind_time   < #{dayEnd})
              AND (vd.unbind_time IS NULL OR vd.unbind_time >= #{dayStart})
            ORDER BY v.vehicle_no, bindTime DESC
            """)
    List<BindingRow> selectBindings(@Param("dayStart") LocalDateTime dayStart,
                                    @Param("dayEnd") LocalDateTime dayEnd);

    /** 事件聚合（白名单 + 剔除误报工单，按车牌×码） */
    @Select("""
            SELECT e.plate_no        AS plateNo,
                   e.event_code      AS eventCode,
                   MAX(e.risk_level) AS lv,
                   COUNT(*)          AS cnt
            FROM mon.risk_event e
            WHERE e.event_time >= #{dayStart} AND e.event_time < #{dayEnd}
              AND e.event_code = ANY(string_to_array(#{eventCodes}, ','))
              AND NOT EXISTS (
                SELECT 1 FROM mon.risk_work_order w
                WHERE w.event_id = e.id AND w.close_result = 'FALSE_ALARM'
              )
            GROUP BY 1, 2
            """)
    List<EventAggRow> selectEventAgg(@Param("dayStart") LocalDateTime dayStart,
                                     @Param("dayEnd") LocalDateTime dayEnd,
                                     @Param("eventCodes") String eventCodes);

    /** 用户启用角色码（重算权限校验，MOD-ANA-001 §5.3） */
    @Select("""
            SELECT r.role_code
            FROM traj.sys_user_role ur
            JOIN traj.sys_role r ON r.id = ur.role_id AND r.valid_mark = 1 AND r.status = 1
            WHERE ur.user_id = #{userId}
            """)
    List<String> selectRoleCodes(@Param("userId") Long userId);

    /** 单车当日 GPS 点（走 idx_traj_gps_point_plate_time） */
    @Select("""
            SELECT gps_time AS gpsTime, speed AS speed
            FROM traj.traj_gps_point
            WHERE plate_no = #{plateNo}
              AND gps_time >= #{dayStart} AND gps_time < #{dayEnd}
            ORDER BY gps_time ASC
            """)
    List<GpsPointRow> selectGpsPoints(@Param("plateNo") String plateNo,
                                      @Param("dayStart") LocalDateTime dayStart,
                                      @Param("dayEnd") LocalDateTime dayEnd);
}
