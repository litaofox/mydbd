package com.mydbd.traj.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mydbd.common.security.UserContext;
import com.mydbd.common.security.UserInfo;
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
import java.util.Set;

/**
 * 轨迹查询与回放服务。
 * 全部查询受当前用户部门数据范围约束（总体规则：数据权限强制）：
 * null=不限制；空集合=返回空数据（fail-closed）。
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
        List<String> plates = currentPlates();
        if (plates != null && plates.isEmpty()) {
            return List.of();
        }
        return gpsPointMapper.listActiveVehicles(plates);
    }

    /**
     * 车辆台账
     */
    public List<Vehicle> listRegisteredVehicles() {
        List<String> plates = currentPlates();
        if (plates != null && plates.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<Vehicle> wrapper = new LambdaQueryWrapper<Vehicle>()
                .eq(Vehicle::getValidMark, 1)
                .in(plates != null, Vehicle::getVehicleNo, plates == null ? List.of() : plates)
                .orderByAsc(Vehicle::getId);
        return vehicleMapper.selectList(wrapper);
    }

    /**
     * 各终端最新位置（实时监控 HTTP 数据源）：按当前用户范围过滤。
     */
    public List<GpsPoint> listLatestPoints() {
        List<String> plates = currentPlates();
        if (plates != null && plates.isEmpty()) {
            return List.of();
        }
        return gpsPointMapper.listLatestPoints(plates);
    }

    /**
     * 各终端最新位置全量（WebSocket 推送链路专用）：
     * 调度线程无 UserContext，全量查询后由发送环节按各会话范围过滤。
     */
    public List<GpsPoint> listLatestPointsForPush() {
        return gpsPointMapper.listLatestPoints(null);
    }

    /**
     * 按设备/车牌 + 时间范围查询轨迹（回放数据源），按时间正序。
     * 仅给设备号不给车牌时同样受范围约束（点位车牌必须在可见集合内）。
     */
    public List<GpsPoint> queryTrack(String identityCode, String plateNo,
                                     LocalDateTime start, LocalDateTime end) {
        List<String> plates = currentPlates();
        if (plates != null && plates.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<GpsPoint> wrapper = new LambdaQueryWrapper<GpsPoint>()
                .eq(StringUtils.hasText(identityCode), GpsPoint::getIdentityCode, identityCode)
                .eq(StringUtils.hasText(plateNo), GpsPoint::getPlateNo, plateNo)
                .in(plates != null, GpsPoint::getPlateNo, plates == null ? List.of() : plates)
                .ge(start != null, GpsPoint::getGpsTime, start)
                .le(end != null, GpsPoint::getGpsTime, end)
                .orderByAsc(GpsPoint::getGpsTime);
        return gpsPointMapper.selectList(wrapper);
    }

    // ===== 内部方法 =====

    /**
     * 当前用户可见车牌：
     * null=不限制（全部数据）；空列表=不可见任何数据；非空=可见车牌。
     */
    private List<String> currentPlates() {
        UserInfo user = UserContext.get();
        if (user == null || user.allData()) {
            return null;
        }
        Set<Long> scope = user.deptScope();
        if (scope == null || scope.isEmpty()) {
            return List.of();
        }
        return vehicleMapper.selectList(new LambdaQueryWrapper<Vehicle>()
                        .eq(Vehicle::getValidMark, 1)
                        .in(Vehicle::getDeptId, scope))
                .stream().map(Vehicle::getVehicleNo).distinct().toList();
    }
}
