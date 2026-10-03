package com.mydbd.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mydbd.iam.entity.SysMenu;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 菜单/权限点 Mapper
 */
public interface SysMenuMapper extends BaseMapper<SysMenu> {

    /** 用户拥有的非空权限码（启用角色 + 启用菜单） */
    @Select("""
            SELECT DISTINCT m.perm_code
              FROM traj.sys_user_role ur
              JOIN traj.sys_role r ON r.id = ur.role_id AND r.valid_mark = 1 AND r.status = 1
              JOIN traj.sys_role_menu rm ON rm.role_id = r.id
              JOIN traj.sys_menu m ON m.id = rm.menu_id AND m.status = 1
             WHERE ur.user_id = #{userId} AND m.perm_code IS NOT NULL
            """)
    List<String> selectPermsByUserId(@Param("userId") Long userId);

    /** 用户可见的目录与菜单（不含按钮），按排序返回 */
    @Select("""
            SELECT DISTINCT m.id, m.parent_id AS "parentId", m.menu_name AS "menuName",
                   m.menu_type AS "menuType", m.perm_code AS "permCode",
                   m.path, m.icon, m.sort_no AS "sortNo", m.visible, m.status,
                   m.create_date AS "createDate"
              FROM traj.sys_user_role ur
              JOIN traj.sys_role r ON r.id = ur.role_id AND r.valid_mark = 1 AND r.status = 1
              JOIN traj.sys_role_menu rm ON rm.role_id = r.id
              JOIN traj.sys_menu m ON m.id = rm.menu_id AND m.status = 1
             WHERE ur.user_id = #{userId}
               AND m.menu_type IN (1, 2) AND m.visible = 1
             ORDER BY m.sort_no, m.id
            """)
    List<SysMenu> selectNavMenusByUserId(@Param("userId") Long userId);

    /** 全部启用菜单（超管用），按排序返回 */
    @Select("""
            SELECT id, parent_id AS "parentId", menu_name AS "menuName",
                   menu_type AS "menuType", perm_code AS "permCode",
                   path, icon, sort_no AS "sortNo", visible, status,
                   create_date AS "createDate"
              FROM traj.sys_menu
             WHERE status = 1
             ORDER BY sort_no, id
            """)
    List<SysMenu> selectAllEnabled();
}
