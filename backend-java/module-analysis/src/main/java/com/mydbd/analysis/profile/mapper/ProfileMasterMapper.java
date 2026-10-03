package com.mydbd.analysis.profile.mapper;

import com.mydbd.analysis.profile.dto.PlateDriverRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * F23 主数据解析（只读 traj.*）：对象→车牌集、对象名称、车牌→当前主班司机映射、字典标签。
 * 模块间零依赖约定：跨域一律同库跨 schema SQL，不 import mdm 模块类。
 */
@Mapper
public interface ProfileMasterMapper {

    /** 车辆是否存在（valid_mark=1）+ 车牌 + 所属企业 */
    @Select("""
            SELECT v.id AS "id", v.vehicle_no AS "name", v.dept_id AS "deptId"
            FROM traj.traj_vehicle v
            WHERE v.valid_mark = 1 AND v.vehicle_no = #{plateNo}
            LIMIT 1
            """)
    Map<String, Object> findVehicle(@Param("plateNo") String plateNo);

    /** 司机是否存在 + 姓名 */
    @Select("""
            SELECT d.id AS "id", d.driver_name AS "name"
            FROM traj.traj_driver d
            WHERE d.valid_mark = 1 AND d.id = #{driverId}
            """)
    Map<String, Object> findDriver(@Param("driverId") Long driverId);

    /** 企业是否存在 + 名称 */
    @Select("""
            SELECT t.id AS "id", t.dept_name AS "name"
            FROM traj.traj_dept t
            WHERE t.valid_mark = 1 AND t.id = #{deptId}
            """)
    Map<String, Object> findDept(@Param("deptId") Long deptId);

    /** 企业（本级精确）名下车辆车牌集 */
    @Select("""
            SELECT v.vehicle_no FROM traj.traj_vehicle v
            WHERE v.valid_mark = 1 AND v.dept_id = #{deptId}
            """)
    List<String> platesByDept(@Param("deptId") Long deptId);

    /** 司机当前绑定（status=1 且 valid_mark=1 主班）名下车辆车牌集 */
    @Select("""
            SELECT v.vehicle_no
            FROM traj.traj_vehicle_driver vd
            JOIN traj.traj_vehicle v ON v.id = vd.vehicle_id AND v.valid_mark = 1
            WHERE vd.driver_id = #{driverId} AND vd.driver_type = 1
              AND vd.status = 1 AND vd.valid_mark = 1
            """)
    List<String> platesByDriver(@Param("driverId") Long driverId);

    /** 车辆当前绑定主班司机（画像 sub 显示；无绑定返回 null） */
    @Select("""
            SELECT d.id AS "driverId", d.driver_name AS "driverName"
            FROM traj.traj_vehicle v
            JOIN traj.traj_vehicle_driver vd ON vd.vehicle_id = v.id AND vd.driver_type = 1
                 AND vd.status = 1 AND vd.valid_mark = 1
            JOIN traj.traj_driver d ON d.id = vd.driver_id AND d.valid_mark = 1
            WHERE v.valid_mark = 1 AND v.vehicle_no = #{plateNo}
            LIMIT 1
            """)
    Map<String, Object> driverOfVehicle(@Param("plateNo") String plateNo);

    /** 全量车牌→当前主班司机映射（排行 dim=driver 归集；一次取全避免 N+1） */
    @Select("""
            SELECT v.vehicle_no AS plateNo, d.id AS driverId, d.driver_name AS driverName, v.dept_id AS deptId
            FROM traj.traj_vehicle_driver vd
            JOIN traj.traj_vehicle v ON v.id = vd.vehicle_id AND v.valid_mark = 1
            JOIN traj.traj_driver d ON d.id = vd.driver_id AND d.valid_mark = 1
            WHERE vd.driver_type = 1 AND vd.status = 1 AND vd.valid_mark = 1
            """)
    List<PlateDriverRow> allPlateDrivers();

    /** 字典标签（risk_event_code），用于事件码中文名兜底 */
    @Select("""
            SELECT i.item_value AS "value", i.item_label AS "label"
            FROM traj.sys_dict_item i
            JOIN traj.sys_dict_type t ON t.id = i.dict_type_id
            WHERE t.dict_code = #{dictCode} AND i.status = 1
            """)
    List<Map<String, Object>> dictItems(@Param("dictCode") String dictCode);
}
