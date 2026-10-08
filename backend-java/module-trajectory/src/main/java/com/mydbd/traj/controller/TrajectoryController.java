package com.mydbd.traj.controller;

import com.mydbd.common.api.PageData;
import com.mydbd.common.api.Result;
import com.mydbd.traj.entity.EventRow;
import com.mydbd.traj.entity.GpsPoint;
import com.mydbd.traj.entity.StopSeg;
import com.mydbd.traj.entity.TrackPointRow;
import com.mydbd.traj.entity.Vehicle;
import com.mydbd.traj.entity.VehicleOption;
import com.mydbd.traj.service.TrajectoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 轨迹接口：车辆、最新位置、历史轨迹
 */
@RestController
@RequestMapping("/api/traj")
@RequiredArgsConstructor
public class TrajectoryController {

    private final TrajectoryService trajectoryService;

    /** 在途车辆（有轨迹的终端去重） */
    @GetMapping("/vehicles")
    public Result<List<VehicleOption>> vehicles() {
        return Result.ok(trajectoryService.listActiveVehicles());
    }

    /** 车辆台账 */
    @GetMapping("/vehicles/registered")
    public Result<List<Vehicle>> registeredVehicles() {
        return Result.ok(trajectoryService.listRegisteredVehicles());
    }

    /** 各终端最新轨迹点（实时监控） */
    @GetMapping("/latest")
    public Result<List<GpsPoint>> latest() {
        return Result.ok(trajectoryService.listLatestPoints());
    }

    /** 历史轨迹查询（回放） */
    @GetMapping("/track")
    public Result<List<GpsPoint>> track(
            @RequestParam(required = false) String identityCode,
            @RequestParam(required = false) String plateNo,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime end) {
        return Result.ok(trajectoryService.queryTrack(identityCode, plateNo, start, end));
    }

    /** 轨迹结果分页（底部"轨迹"标签） */
    @GetMapping("/track/page")
    public Result<PageData<TrackPointRow>> trackPage(
            @RequestParam(required = false) String identityCode,
            @RequestParam(required = false) String plateNo,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime end,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "50") long size) {
        return Result.ok(trajectoryService.pageTrack(identityCode, plateNo, start, end, page, size));
    }

    /** 事件结果分页（底部"事件"标签） */
    @GetMapping("/events/page")
    public Result<PageData<EventRow>> eventsPage(
            @RequestParam(required = false) String identityCode,
            @RequestParam(required = false) String plateNo,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime end,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "50") long size) {
        return Result.ok(trajectoryService.pageEvents(identityCode, plateNo, start, end, page, size));
    }

    /** 停车结果分页（底部"停车"标签） */
    @GetMapping("/stops/page")
    public Result<PageData<StopSeg>> stopsPage(
            @RequestParam(required = false) String identityCode,
            @RequestParam(required = false) String plateNo,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime end,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "50") long size) {
        return Result.ok(trajectoryService.pageStops(identityCode, plateNo, start, end, page, size));
    }
}
