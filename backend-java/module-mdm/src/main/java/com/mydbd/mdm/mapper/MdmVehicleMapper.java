package com.mydbd.mdm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mydbd.mdm.entity.Vehicle;

/**
 * 主数据-车辆 Mapper。
 * 类名加 Mdm 前缀，避免与轨迹模块 com.mydbd.traj.mapper.VehicleMapper 的 Bean 名冲突。
 */
public interface MdmVehicleMapper extends BaseMapper<Vehicle> {
}
