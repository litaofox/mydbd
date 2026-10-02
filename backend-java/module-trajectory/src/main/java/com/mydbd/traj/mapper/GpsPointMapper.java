package com.mydbd.traj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mydbd.traj.entity.GpsPoint;
import com.mydbd.traj.entity.VehicleOption;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface GpsPointMapper extends BaseMapper<GpsPoint> {

    /**
     * 在途车辆（有轨迹点的终端，按设备去重）
     */
    @Select("""
            SELECT DISTINCT identity_code AS identityCode, plate_no AS plateNo
            FROM traj.traj_gps_point
            ORDER BY identityCode
            """)
    List<VehicleOption> listActiveVehicles();

    /**
     * 每个终端的最新轨迹点（PostgreSQL DISTINCT ON）
     */
    @Select("""
            SELECT DISTINCT ON (identity_code)
                   id, identity_code AS identityCode, plate_no AS plateNo, gps_time AS gpsTime,
                   lng, lat, speed, direction, altitude, alarm_flag AS alarmFlag, mileage
            FROM traj.traj_gps_point
            ORDER BY identity_code, gps_time DESC
            """)
    List<GpsPoint> listLatestPoints();
}
