package com.mydbd.mdm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mydbd.mdm.entity.VehicleTerminal;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface VehicleTerminalMapper extends BaseMapper<VehicleTerminal> {

    /** 某车辆的全部车-终端绑定记录（含历史），带车辆/终端展示字段，按绑定时间倒序 */
    @Select("""
            SELECT vt.*, v.vehicle_no AS vehicle_no,
                   t.identity_code AS terminal_identity, t.tl_model AS tl_model, t.sim_account AS sim_account
            FROM traj.traj_vehicle_terminal vt
            LEFT JOIN traj.traj_vehicle v  ON v.id = vt.vehicle_id
            LEFT JOIN traj.traj_terminal t ON t.id = vt.terminal_id
            WHERE vt.vehicle_id = #{vehicleId} AND vt.valid_mark = 1
            ORDER BY vt.bind_time DESC NULLS LAST, vt.id DESC
            """)
    List<VehicleTerminal> listHistoryByVehicle(@Param("vehicleId") Long vehicleId);
}
