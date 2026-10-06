package com.mydbd.monitor.panel.mapper;

import com.mydbd.monitor.panel.vo.VehiclePanelVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * F16 车辆详情聚合面板（MOD-MON-003 §3.4）：
 * 全 @Select 注解 SQL，同库跨 schema 只读直查 traj.* / mon.*，
 * 不 import module-mdm / module-trajectory 类（模块零依赖约定）。
 */
public interface VehiclePanelMapper {

    /** 车辆档案 + 组织名（valid_mark=1；不存在返回 null，同时完成存在性校验） */
    @Select("""
            SELECT v.id, v.dept_id AS "deptId", d.dept_name AS "deptName",
                   v.vehicle_no AS "vehicleNo", v.vehicle_plate_color AS "vehiclePlateColor",
                   v.vehicle_type AS "vehicleType", v.vehicle_brand AS "vehicleBrand",
                   v.vin, v.operation_type AS "operationType",
                   v.owner_name AS "ownerName", v.owner_phone AS "ownerPhone",
                   v.road_license_no AS "roadLicenseNo", v.remark
              FROM traj.traj_vehicle v
              LEFT JOIN traj.traj_dept d ON d.id = v.dept_id
             WHERE v.id = #{vehicleId} AND v.valid_mark = 1
            """)
    VehiclePanelVO.VehicleBrief selectVehicle(@Param("vehicleId") long vehicleId);

    /** 当前有效绑定终端（status=1 AND valid_mark=1；无绑定返回 null），含网关在案在线状态 */
    @Select("""
            SELECT t.id, t.identity_code AS "identityCode", t.tl_model AS "tlModel",
                   t.sim_account AS "simAccount", t.protocol_type AS "protocolType",
                   t.equipment_type AS "equipmentType", t.video_channel AS "videoChannel",
                   t.status, t.online_status AS "onlineStatus", b.bind_time AS "bindTime"
              FROM traj.traj_vehicle_terminal b
              JOIN traj.traj_terminal t ON t.id = b.terminal_id AND t.valid_mark = 1
             WHERE b.vehicle_id = #{vehicleId} AND b.status = 1 AND b.valid_mark = 1
             ORDER BY b.bind_time DESC
             LIMIT 1
            """)
    VehiclePanelVO.TerminalBrief selectTerminal(@Param("vehicleId") long vehicleId);

    /** 在班司机（主班 driver_type=1 / 副班=2，按 driver_type、bind_time 排序） */
    @Select("""
            SELECT dr.id, dr.driver_name AS "driverName", dr.sex,
                   dr.contact_phone AS "contactPhone", dr.licence_category AS "licenceCategory",
                   bd.driver_type AS "driverType", dr.status, bd.bind_time AS "bindTime"
              FROM traj.traj_vehicle_driver bd
              JOIN traj.traj_driver dr ON dr.id = bd.driver_id AND dr.valid_mark = 1
             WHERE bd.vehicle_id = #{vehicleId} AND bd.status = 1 AND bd.valid_mark = 1
             ORDER BY bd.driver_type, bd.bind_time
            """)
    List<VehiclePanelVO.DriverBrief> selectDrivers(@Param("vehicleId") long vehicleId);

    /** 最新定位点（按终端识别码，命中 idx_traj_gps_point_identity_time） */
    @Select("""
            SELECT identity_code AS "identityCode", plate_no AS "plateNo",
                   lng, lat, speed, direction, gps_time AS "gpsTime", alarm_flag AS "alarmFlag"
              FROM traj.traj_gps_point
             WHERE identity_code = #{identityCode}
             ORDER BY gps_time DESC
             LIMIT 1
            """)
    VehiclePanelVO.LatestPoint selectLatestPoint(@Param("identityCode") String identityCode);

    /** 当日轨迹原始点（升序，Java 端抽稀；单车当日 ≤8640 点小范围扫描） */
    @Select("""
            SELECT gps_time AS "gpsTime", lng, lat, speed
              FROM traj.traj_gps_point
             WHERE identity_code = #{identityCode}
               AND gps_time >= #{dayStart} AND gps_time <= #{endTime}
             ORDER BY gps_time ASC
            """)
    List<VehiclePanelVO.TrackPoint> selectTodayPoints(@Param("identityCode") String identityCode,
                                                      @Param("dayStart") LocalDateTime dayStart,
                                                      @Param("endTime") LocalDateTime endTime);

    /** 最新 5 条终端报警（按车牌；字段形状对齐 F17 契约，MOD-MON-003 §3.2） */
    @Select("""
            SELECT w.id, w.type_id AS "typeId",
                   COALESCE(t.name, '类型 ' || w.type_id) AS "typeName",
                   w.start_warn_time AS "startWarnTime",
                   COALESCE(w.handle_status, 0) AS "handleStatus",
                   w.start_lng AS "startLng", w.start_lat AS "startLat"
              FROM traj.traj_warn_info w
              LEFT JOIN traj.base_warn_type t ON t.id = w.type_id
             WHERE w.plate_no = #{plateNo}
             ORDER BY w.start_warn_time DESC NULLS LAST, w.id DESC
             LIMIT 5
            """)
    List<VehiclePanelVO.PanelAlarm> selectLatestAlarms(@Param("plateNo") String plateNo);

    /** 当日终端报警计数（按车牌） */
    @Select("""
            SELECT count(*) FROM traj.traj_warn_info
             WHERE plate_no = #{plateNo} AND start_warn_time >= #{dayStart} AND start_warn_time <= #{endTime}
            """)
    long countTodayAlarms(@Param("plateNo") String plateNo,
                          @Param("dayStart") LocalDateTime dayStart,
                          @Param("endTime") LocalDateTime endTime);

    /** 当日风险事件计数（按车牌） */
    @Select("""
            SELECT count(*) FROM mon.risk_event
             WHERE plate_no = #{plateNo} AND event_time >= #{dayStart} AND event_time <= #{endTime}
            """)
    long countTodayRisks(@Param("plateNo") String plateNo,
                         @Param("dayStart") LocalDateTime dayStart,
                         @Param("endTime") LocalDateTime endTime);
}
