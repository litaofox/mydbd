package com.mydbd.common.security;

import java.util.Set;

/**
 * 当前登录用户信息（F33 IAM 扩展）。
 *
 * @param userId     用户ID
 * @param username   登录名
 * @param realName   显示名
 * @param deptId     主属部门ID（可空）
 * @param superAdmin 是否超级管理员（恒有全部权限、数据范围不限制）
 * @param perms      功能权限码集合（如 mdm:vehicle:edit）
 * @param deptScope  数据范围部门ID集合；{@code null}=全部数据，空集合=不可见任何部门数据
 */
public record UserInfo(Long userId, String username, String realName, Long deptId,
                       boolean superAdmin, Set<String> perms, Set<Long> deptScope) {

    public boolean hasPerm(String code) {
        return superAdmin || (perms != null && perms.contains(code));
    }

    /** 数据范围是否不限制（全部数据可见） */
    public boolean allData() {
        return superAdmin || deptScope == null;
    }
}
