package com.mydbd.monitor.panel.controller;

import com.mydbd.common.api.ErrorCode;
import com.mydbd.common.api.Result;
import com.mydbd.common.exception.BizException;
import com.mydbd.common.security.RequiresPerm;
import com.mydbd.monitor.panel.service.VehiclePanelService;
import com.mydbd.monitor.panel.vo.VehiclePanelVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * F16 车辆详情聚合面板接口（MOD-MON-003 §3.2）。
 * 类级 monitor:view（面板挂在监控页内，不新建权限码）；
 * vehicleId 手动 parse，非法数字 → 40001（不依赖 TypeMismatch 处理）。
 */
@RestController
@RequestMapping("/api/monitor/vehicle-panel")
@RequiredArgsConstructor
@RequiresPerm("monitor:view")
public class VehiclePanelController {

    private final VehiclePanelService vehiclePanelService;

    /** 单一聚合接口：档案 + 终端/司机绑定 + 最新点 + 当日轨迹 + 报警 Top5 + 当日计数 */
    @GetMapping("/{vehicleId}")
    public Result<VehiclePanelVO> panel(@PathVariable String vehicleId) {
        long id;
        try {
            id = Long.parseLong(vehicleId.trim());
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请求参数错误");
        }
        return Result.ok(vehiclePanelService.panel(id));
    }
}
