package com.mydbd.iam.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户-角色关系 Mapper（复合主键，注解 SQL）
 */
public interface SysUserRoleMapper {

    @Select("SELECT role_id FROM traj.sys_user_role WHERE user_id = #{userId}")
    List<Long> selectRoleIds(@Param("userId") Long userId);

    @Select("SELECT user_id FROM traj.sys_user_role WHERE role_id = #{roleId}")
    List<Long> selectUserIds(@Param("roleId") Long roleId);

    @Select("SELECT COUNT(1) FROM traj.sys_user_role ur "
            + "JOIN traj.sys_user u ON u.id = ur.user_id AND u.valid_mark = 1 "
            + "WHERE ur.role_id = #{roleId}")
    long countActiveUsers(@Param("roleId") Long roleId);

    @Insert("INSERT INTO traj.sys_user_role(user_id, role_id) VALUES(#{userId}, #{roleId})")
    int insert(@Param("userId") Long userId, @Param("roleId") Long roleId);

    @Delete("DELETE FROM traj.sys_user_role WHERE user_id = #{userId}")
    int deleteByUser(@Param("userId") Long userId);

    @Delete("DELETE FROM traj.sys_user_role WHERE role_id = #{roleId}")
    int deleteByRole(@Param("roleId") Long roleId);
}
