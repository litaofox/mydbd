package com.mydbd.iam.vo;

import java.util.List;

/**
 * 登录后档案：用户信息 + 角色码 + 权限码 + 导航菜单树
 */
public record ProfileVO(
        UserProfile user,
        List<String> roles,
        List<String> perms,
        List<MenuNode> menus) {
}
