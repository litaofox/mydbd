package com.mydbd.traj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mydbd.traj.entity.GpsPoint;
import com.mydbd.traj.entity.VehicleOption;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface GpsPointMapper extends BaseMapper<GpsPoint> {

    /**
     * 在途车辆（有轨迹点的终端，按设备去重）
     * 性能：终端表驱动 + LATERAL 逐台取最新点（索引探测 500 次），
     * 避免 DISTINCT 全量扫描轨迹表（890 万行约 19s → 亚秒级）。
     */
    @Select("""
            SELECT p."identityCode", p."plateNo"
              FROM traj.traj_terminal t
              CROSS JOIN LATERAL (
                    SELECT g.identity_code AS "identityCode", g.plate_no AS "plateNo"
                      FROM traj.traj_gps_point g
                     WHERE g.identity_code = t.identity_code
                     ORDER BY g.gps_time DESC
                     LIMIT 1
              ) p
             WHERE t.valid_mark = 1
             ORDER BY p."identityCode"
            """)
    List<VehicleOption> listActiveVehicles();

    /**
     * 每个终端的最新轨迹点
     * 性能：同上，终端表驱动 + LATERAL（DISTINCT ON 全量扫索引约 53s → 0.5s）。
     */
    @Select("""
            SELECT p.*
              FROM traj.traj_terminal t
              CROSS JOIN LATERAL (
                    SELECT g.id, g.identity_code AS "identityCode", g.plate_no AS "plateNo",
                           g.gps_time AS "gpsTime", g.lng, g.lat, g.speed, g.direction,
                           g.altitude, g.alarm_flag AS "alarmFlag", g.mileage
                      FROM traj.traj_gps_point g
                     WHERE g.identity_code = t.identity_code
                     ORDER BY g.gps_time DESC
                     LIMIT 1
              ) p
             WHERE t.valid_mark = 1
            """)
    List<GpsPoint> listLatestPoints();
}
