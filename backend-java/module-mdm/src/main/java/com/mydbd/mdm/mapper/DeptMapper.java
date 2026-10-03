package com.mydbd.mdm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mydbd.mdm.entity.Dept;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface DeptMapper extends BaseMapper<Dept> {

    /** 各有效组织下的有效车辆数 */
    @Select("SELECT dept_id AS deptId, COUNT(1) AS cnt FROM traj.traj_vehicle "
            + "WHERE valid_mark = 1 AND dept_id IS NOT NULL GROUP BY dept_id")
    List<Map<String, Object>> countVehiclesGroupByDept();
}
