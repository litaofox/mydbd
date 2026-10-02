package com.mydbd.traj.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mydbd.traj.entity.GpsPoint;
import com.mydbd.traj.entity.Vehicle;
import com.mydbd.traj.entity.VehicleOption;
import com.mydbd.traj.mapper.GpsPointMapper;
import com.mydbd.traj.mapper.VehicleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 轨迹查询与回放服务
 */
@Service
@RequiredArgsConstructor
public class TrajectoryService {

    private final GpsPointMapper gpsPointMapper;
    private final VehicleMapper vehicleMapper;

    /**
     * 在途车辆选项（实时监控/回放页面下拉）
     */
    public List<VehicleOption> listActiveVehicles() {
        return gpsPointMapper.listActiveVehicles();
    }

    /**
     * 车辆台账
     */
    public List<Vehicle> listRegisteredVehicles() {
        return vehicleMapper.selectList(new LambdaQueryWrapper<Vehicle>()
                .eq(Vehicle::getValidMark, 1)
                .orderByAsc(Vehicle::getId));
    }

    /**
     * 各终端最新位置（实时监控数据源）
     */
    public List<GpsPoint> listLatestPoints() {
        return gpsPointMapper.listLatestPoints();
    }

    /**
     * 按设备/车牌 + 时间范围查询轨迹（回放数据源），按时间正序
     */
    public List<GpsPoint> queryTrack(String identityCode, String plateNo,
                                     LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<GpsPoint> wrapper = new LambdaQueryWrapper<GpsPoint>()
                .eq(StringUtils.hasText(identityCode), GpsPoint::getIdentityCode, identityCode)
                .eq(StringUtils.hasText(plateNo), GpsPoint::getPlateNo, plateNo)
                .ge(start != null, GpsPoint::getGpsTime, start)
                .le(end != null, GpsPoint::getGpsTime, end)
                .orderByAsc(GpsPoint::getGpsTime);
        return gpsPointMapper.selectList(wrapper);
    }
}
