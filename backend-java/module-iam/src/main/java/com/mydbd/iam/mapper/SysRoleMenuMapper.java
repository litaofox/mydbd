package com.mydbd.iam.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色-菜单/权限点关系 Mapper
 */
public interface SysRoleMenuMapper {

    @Select("SELECT menu_id FROM traj.sys_role_menu WHERE role_id = #{roleId}")
    List<Long> selectMenuIds(@Param("roleId") Long roleId);

    @Insert("INSERT INTO traj.sys_role_menu(role_id, menu_id) VALUES(#{roleId}, #{menuId})")
    int insert(@Param("roleId") Long roleId, @Param("menuId") Long menuId);

    @Delete("DELETE FROM traj.sys_role_menu WHERE role_id = #{roleId}")
    int deleteByRole(@Param("roleId") Long roleId);
}
