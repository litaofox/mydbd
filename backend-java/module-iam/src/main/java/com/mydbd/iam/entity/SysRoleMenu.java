package com.mydbd.iam.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 角色-菜单/权限点关系
 */
@Data
@TableName("traj.sys_role_menu")
public class SysRoleMenu {

    private Long roleId;

    private Long menuId;
}
