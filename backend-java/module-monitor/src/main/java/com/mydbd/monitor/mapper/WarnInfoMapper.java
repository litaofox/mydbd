package com.mydbd.monitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mydbd.monitor.entity.WarnInfo;
import org.apache.ibatis.annotations.Select;

public interface WarnInfoMapper extends BaseMapper<WarnInfo> {

    /** 今日终端报警数 */
    @Select("SELECT count(*) FROM traj.traj_warn_info WHERE start_warn_time::date = CURRENT_DATE")
    long countTodayWarnings();

    /** 有轨迹的在途车辆数（监控域直接读取 traj schema，避免跨模块依赖） */
    @Select("SELECT count(DISTINCT identity_code) FROM traj.traj_gps_point")
    long countActiveVehicles();
}
