package com.mydbd.iam.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色-自定义数据范围部门关系 Mapper
 */
public interface SysRoleDeptMapper {

    @Select("SELECT dept_id FROM traj.sys_role_dept WHERE role_id = #{roleId}")
    List<Long> selectDeptIds(@Param("roleId") Long roleId);

    @Insert("INSERT INTO traj.sys_role_dept(role_id, dept_id) VALUES(#{roleId}, #{deptId})")
    int insert(@Param("roleId") Long roleId, @Param("deptId") Long deptId);

    @Delete("DELETE FROM traj.sys_role_dept WHERE role_id = #{roleId}")
    int deleteByRole(@Param("roleId") Long roleId);
}
