package com.mydbd.iam.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 组织部门查询 Mapper（IAM 引导用）。
 * 仅按 dept_code 读取 traj.traj_dept，不引入对 MDM 模块的依赖。
 */
public interface ProvDeptMapper {

    @Select("SELECT id FROM traj.traj_dept WHERE dept_code = #{deptCode} AND valid_mark = 1")
    Long selectIdByCode(@Param("deptCode") String deptCode);
}
