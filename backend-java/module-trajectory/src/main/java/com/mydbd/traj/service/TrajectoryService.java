package com.mydbd.traj.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mydbd.common.api.PageData;
import com.mydbd.common.security.UserContext;
import com.mydbd.common.security.UserInfo;
import com.mydbd.traj.entity.EventRow;
import com.mydbd.traj.entity.GpsPoint;
import com.mydbd.traj.entity.StopSeg;
import com.mydbd.traj.entity.TrackPointRow;
import com.mydbd.traj.entity.Vehicle;
import com.mydbd.traj.entity.VehicleOption;
import com.mydbd.traj.mapper.GpsPointMapper;
import com.mydbd.traj.mapper.TrackQueryMapper;
import com.mydbd.traj.mapper.VehicleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final TrackQueryMapper trackQueryMapper;

    /** 停车段切分：相邻零速点间隔超过该值视为两次停车 */
    private static final Duration STOP_GAP = Duration.ofMinutes(5);
    /** 计入停车结果的最短持续时长 */
    private static final Duration MIN_STOP_DURATION = Duration.ofMinutes(3);

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

    /**
     * 轨迹结果分页（回放页底部"轨迹"标签）。
     */
    public PageData<TrackPointRow> pageTrack(String identityCode, String plateNo,
                                             LocalDateTime start, LocalDateTime end,
                                             long page, long size) {
        List<String> plates = currentPlates();
        if (plates != null && plates.isEmpty()) {
            return new PageData<>(0, page, size, List.of());
        }
        IPage<TrackPointRow> result = trackQueryMapper.pageTrack(
                new Page<>(page, size), identityCode, plateNo, start, end, plates);
        return new PageData<>(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    /**
     * 事件结果分页（回放页底部"事件"标签，来源 mon.risk_event）。
     */
    public PageData<EventRow> pageEvents(String identityCode, String plateNo,
                                         LocalDateTime start, LocalDateTime end,
                                         long page, long size) {
        List<String> plates = currentPlates();
        if (plates != null && plates.isEmpty()) {
            return new PageData<>(0, page, size, List.of());
        }
        IPage<EventRow> result = trackQueryMapper.pageEvents(
                new Page<>(page, size), identityCode, plateNo, start, end, plates);
        return new PageData<>(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    /**
     * 停车结果分页（回放页底部"停车"标签）：
     * 取窗口内有序点，将连续零速点聚合为停车段（间隔超 {@link #STOP_GAP} 切分），
     * 持续时长 ≥ {@link #MIN_STOP_DURATION} 才计入；服务层手工分页。
     */
    public PageData<StopSeg> pageStops(String identityCode, String plateNo,
                                       LocalDateTime start, LocalDateTime end,
                                       long page, long size) {
        List<String> plates = currentPlates();
        if (plates != null && plates.isEmpty()) {
            return new PageData<>(0, page, size, List.of());
        }
        List<GpsPoint> points = trackQueryMapper.listWindowPoints(identityCode, plateNo, start, end, plates);

        // 连续零速点分组（间隔超阈值切分；出现非零速点结束当前组）
        List<List<GpsPoint>> groups = new ArrayList<>();
        List<GpsPoint> current = null;
        LocalDateTime lastStopTime = null;
        for (GpsPoint p : points) {
            boolean isStop = p.getSpeed() != null && p.getSpeed() == 0;
            if (isStop) {
                boolean split = current == null
                        || (lastStopTime != null && Duration.between(lastStopTime, p.getGpsTime()).compareTo(STOP_GAP) > 0);
                if (split) {
                    current = new ArrayList<>();
                    groups.add(current);
                }
                current.add(p);
                lastStopTime = p.getGpsTime();
            } else {
                current = null;
                lastStopTime = null;
            }
        }

        // 最小时长过滤后映射为停车段
        List<StopSeg> all = new ArrayList<>();
        for (List<GpsPoint> g : groups) {
            LocalDateTime s = g.get(0).getGpsTime();
            LocalDateTime e = g.get(g.size() - 1).getGpsTime();
            long secs = Duration.between(s, e).getSeconds();
            if (secs < MIN_STOP_DURATION.getSeconds()) continue;
            all.add(new StopSeg(g.get(0).getPlateNo(), s, e, secs, g.get(0).getLng(), g.get(0).getLat()));
        }

        long total = all.size();
        int fromIndex = (int) Math.min(Integer.MAX_VALUE, Math.max(0, (page - 1) * size));
        List<StopSeg> records = fromIndex >= all.size()
                ? List.of()
                : all.subList(fromIndex, (int) Math.min(all.size(), fromIndex + size));
        return new PageData<>(total, page, size, records);
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
