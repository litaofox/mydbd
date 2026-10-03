package com.mydbd.monitor.panel.service;

import com.mydbd.common.api.ErrorCode;
import com.mydbd.common.exception.BizException;
import com.mydbd.common.security.UserContext;
import com.mydbd.common.security.UserInfo;
import com.mydbd.monitor.panel.mapper.VehiclePanelMapper;
import com.mydbd.monitor.panel.vo.VehiclePanelVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * F16 车辆详情聚合面板服务（MOD-MON-003 §3/§4）。
 * 编排聚合查询 + deptScope 数据范围校验 + 当日轨迹等间隔抽稀（≤500 点，首末必留）。
 */
@Service
@RequiredArgsConstructor
public class VehiclePanelService {

    private static final int MAX_TRACK_POINTS = 500;
    private static final long ONLINE_WINDOW_MINUTES = 5;

    private final VehiclePanelMapper panelMapper;

    public VehiclePanelVO panel(long vehicleId) {
        VehiclePanelVO.VehicleBrief vehicle = panelMapper.selectVehicle(vehicleId);
        if (vehicle == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "车辆不存在");
        }
        checkDeptScope(vehicle);

        VehiclePanelVO vo = new VehiclePanelVO();
        vo.setVehicle(vehicle);

        VehiclePanelVO.TerminalBrief terminal = panelMapper.selectTerminal(vehicleId);
        vo.setTerminal(terminal);
        vo.setDrivers(panelMapper.selectDrivers(vehicleId));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();

        if (terminal != null) {
            VehiclePanelVO.LatestPoint latest = panelMapper.selectLatestPoint(terminal.getIdentityCode());
            if (latest != null) {
                latest.setOnline(latest.getGpsTime() != null
                        && latest.getGpsTime().isAfter(now.minusMinutes(ONLINE_WINDOW_MINUTES)));
            }
            vo.setLatestPoint(latest);
            vo.setTodayTrack(buildTodayTrack(terminal.getIdentityCode(), dayStart, now));
        } else {
            vo.setLatestPoint(null);
            vo.setTodayTrack(emptyTrack(dayStart));
        }

        String plateNo = vehicle.getVehicleNo();
        vo.setLatestAlarms(panelMapper.selectLatestAlarms(plateNo));
        vo.setTodayAlarmCount(panelMapper.countTodayAlarms(plateNo, dayStart, now));
        vo.setTodayRiskCount(panelMapper.countTodayRisks(plateNo, dayStart, now));
        return vo;
    }

    /**
     * §4.1 数据范围：deptScope null/超管 = 全可见；空集 = 40301；
     * 否则车辆 deptId 为 NULL 或 ∉ scope → 40301。
     */
    private void checkDeptScope(VehiclePanelVO.VehicleBrief vehicle) {
        UserInfo user = UserContext.get();
        if (user == null || user.allData()) {
            return;
        }
        Set<Long> scope = user.deptScope();
        if (scope == null || scope.isEmpty()) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权查看该车辆");
        }
        if (vehicle.getDeptId() == null || !scope.contains(vehicle.getDeptId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权查看该车辆");
        }
    }

    /** §3.2 当日轨迹：Java 端等间隔抽稀至 ≤500 点，首点、末点必留 */
    private VehiclePanelVO.TodayTrack buildTodayTrack(String identityCode,
                                                      LocalDateTime dayStart,
                                                      LocalDateTime now) {
        List<VehiclePanelVO.TrackPoint> raw = panelMapper.selectTodayPoints(identityCode, dayStart, now);
        VehiclePanelVO.TodayTrack track = new VehiclePanelVO.TodayTrack();
        track.setDate(LocalDate.now().toString());
        track.setMaxPoints(MAX_TRACK_POINTS);
        track.setTotalPoints(raw.size());
        if (raw.size() <= MAX_TRACK_POINTS) {
            track.setSampled(false);
            track.setPoints(raw);
            return track;
        }
        track.setSampled(true);
        List<VehiclePanelVO.TrackPoint> sampled = new ArrayList<>(MAX_TRACK_POINTS);
        double step = (double) (raw.size() - 1) / (MAX_TRACK_POINTS - 1);
        for (int i = 0; i < MAX_TRACK_POINTS; i++) {
            int idx = (int) Math.round(i * step);
            idx = Math.min(idx, raw.size() - 1);
            VehiclePanelVO.TrackPoint p = raw.get(idx);
            if (sampled.isEmpty() || sampled.get(sampled.size() - 1) != p) {
                sampled.add(p);
            }
        }
        // 末点必留
        if (!sampled.get(sampled.size() - 1).equals(raw.get(raw.size() - 1))) {
            sampled.set(sampled.size() - 1, raw.get(raw.size() - 1));
        }
        track.setPoints(sampled);
        return track;
    }

    private VehiclePanelVO.TodayTrack emptyTrack(LocalDateTime dayStart) {
        VehiclePanelVO.TodayTrack track = new VehiclePanelVO.TodayTrack();
        track.setDate(LocalDate.now().toString());
        track.setTotalPoints(0);
        track.setSampled(false);
        track.setMaxPoints(MAX_TRACK_POINTS);
        track.setPoints(List.of());
        return track;
    }
}
