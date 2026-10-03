package com.mydbd.mdm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mydbd.mdm.entity.VehicleDriver;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface VehicleDriverMapper extends BaseMapper<VehicleDriver> {

    /** 某车辆的全部车-司机绑定记录（含历史），带车辆/司机展示字段，按绑定时间倒序 */
    @Select("""
            SELECT vd.*, v.vehicle_no AS vehicle_no,
                   d.driver_name AS driver_name, d.contact_phone AS contact_phone,
                   d.licence_category AS licence_category
            FROM traj.traj_vehicle_driver vd
            LEFT JOIN traj.traj_vehicle v ON v.id = vd.vehicle_id
            LEFT JOIN traj.traj_driver d  ON d.id = vd.driver_id
            WHERE vd.vehicle_id = #{vehicleId} AND vd.valid_mark = 1
            ORDER BY vd.bind_time DESC NULLS LAST, vd.id DESC
            """)
    List<VehicleDriver> listHistoryByVehicle(@Param("vehicleId") Long vehicleId);
}
