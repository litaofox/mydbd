package com.mydbd.traj.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mydbd.traj.entity.EventRow;
import com.mydbd.traj.entity.GpsPoint;
import com.mydbd.traj.entity.TrackPointRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 回放结果列表查询：轨迹分页、事件分页、停车聚合用窗口点。
 * 全部查询带数据权限车牌集合（plates：null=不限；空集由服务层提前返回）。
 */
public interface TrackQueryMapper {

    /**
     * 轨迹点分页（时间正序），LATERAL 关联车辆当前绑定主司机姓名。
     * 走索引 traj.traj_gps_point (identity_code, gps_time) / (plate_no, gps_time)。
     */
    @Select("""
            <script>
            SELECT g.id, g.identity_code AS "identityCode", g.plate_no AS "plateNo",
                   g.gps_time AS "gpsTime", g.lng, g.lat, g.speed, g.direction,
                   g.altitude, g.alarm_flag AS "alarmFlag", g.mileage,
                   g.receive_time AS "receiveTime",
                   d.driver_name AS "driverName"
              FROM traj.traj_gps_point g
              LEFT JOIN LATERAL (
                    SELECT dr.driver_name
                      FROM traj.traj_vehicle v
                      JOIN traj.traj_vehicle_driver vd
                        ON vd.vehicle_id = v.id AND vd.valid_mark = 1 AND vd.status = 1
                      JOIN traj.traj_driver dr
                        ON dr.id = vd.driver_id AND dr.valid_mark = 1
                     WHERE v.vehicle_no = g.plate_no AND v.valid_mark = 1
                     ORDER BY vd.bind_time DESC NULLS LAST
                     LIMIT 1
              ) d ON TRUE
             WHERE 1 = 1
            <if test="identityCode != null and identityCode != ''">
              AND g.identity_code = #{identityCode}
            </if>
            <if test="plateNo != null and plateNo != ''">
              AND g.plate_no = #{plateNo}
            </if>
            <if test="start != null">
              AND g.gps_time &gt;= #{start}
            </if>
            <if test="end != null">
              AND g.gps_time &lt;= #{end}
            </if>
            <if test="plates != null">
              AND g.plate_no IN
              <foreach collection="plates" item="x" open="(" separator="," close=")">#{x}</foreach>
            </if>
             ORDER BY g.gps_time ASC
            </script>
            """)
    IPage<TrackPointRow> pageTrack(IPage<TrackPointRow> page,
                                   @Param("identityCode") String identityCode,
                                   @Param("plateNo") String plateNo,
                                   @Param("start") LocalDateTime start,
                                   @Param("end") LocalDateTime end,
                                   @Param("plates") Collection<String> plates);

    /**
     * 事件分页（来源 mon.risk_event，同库跨 schema 只读；时间正序）。
     */
    @Select("""
            <script>
            SELECT e.id, e.identity_code AS "identityCode", e.plate_no AS "plateNo",
                   e.event_code AS "eventCode", e.event_source AS "eventSource",
                   e.event_time AS "eventTime", e.speed,
                   e.risk_level AS "riskLevel", e.lng, e.lat
              FROM mon.risk_event e
             WHERE 1 = 1
            <if test="identityCode != null and identityCode != ''">
              AND e.identity_code = #{identityCode}
            </if>
            <if test="plateNo != null and plateNo != ''">
              AND e.plate_no = #{plateNo}
            </if>
            <if test="start != null">
              AND e.event_time &gt;= #{start}
            </if>
            <if test="end != null">
              AND e.event_time &lt;= #{end}
            </if>
            <if test="plates != null">
              AND e.plate_no IN
              <foreach collection="plates" item="x" open="(" separator="," close=")">#{x}</foreach>
            </if>
             ORDER BY e.event_time ASC
            </script>
            """)
    IPage<EventRow> pageEvents(IPage<EventRow> page,
                               @Param("identityCode") String identityCode,
                               @Param("plateNo") String plateNo,
                               @Param("start") LocalDateTime start,
                               @Param("end") LocalDateTime end,
                               @Param("plates") Collection<String> plates);

    /**
     * 单车时间窗内的有序点（仅停车聚合所需字段；时间正序）。
     */
    @Select("""
            <script>
            SELECT g.id, g.identity_code AS "identityCode", g.plate_no AS "plateNo",
                   g.gps_time AS "gpsTime", g.lng, g.lat, g.speed
              FROM traj.traj_gps_point g
             WHERE 1 = 1
            <if test="identityCode != null and identityCode != ''">
              AND g.identity_code = #{identityCode}
            </if>
            <if test="plateNo != null and plateNo != ''">
              AND g.plate_no = #{plateNo}
            </if>
            <if test="start != null">
              AND g.gps_time &gt;= #{start}
            </if>
            <if test="end != null">
              AND g.gps_time &lt;= #{end}
            </if>
            <if test="plates != null">
              AND g.plate_no IN
              <foreach collection="plates" item="x" open="(" separator="," close=")">#{x}</foreach>
            </if>
             ORDER BY g.gps_time ASC
            </script>
            """)
    List<GpsPoint> listWindowPoints(@Param("identityCode") String identityCode,
                                    @Param("plateNo") String plateNo,
                                    @Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end,
                                    @Param("plates") Collection<String> plates);
}
