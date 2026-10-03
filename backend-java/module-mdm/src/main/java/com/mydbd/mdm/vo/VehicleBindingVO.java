package com.mydbd.mdm.vo;

import com.mydbd.mdm.entity.VehicleDriver;
import com.mydbd.mdm.entity.VehicleTerminal;

import java.util.List;

/**
 * 车辆绑定视图：当前终端 + 在班司机 + 两类绑定历史
 */
public record VehicleBindingVO(
        Long vehicleId,
        String vehicleNo,
        VehicleTerminal currentTerminal,
        List<VehicleDriver> activeDrivers,
        List<VehicleTerminal> terminalHistory,
        List<VehicleDriver> driverHistory) {
}
