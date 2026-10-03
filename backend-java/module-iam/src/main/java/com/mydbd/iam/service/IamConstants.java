package com.mydbd.iam.service;

/**
 * IAM 内置常量
 */
public final class IamConstants {

    /** 内置超级管理员登录名（受硬保护，不可停用/删除/降级） */
    public static final String ADMIN_USERNAME = "admin";

    /** 超级管理员角色码（恒有全部权限、数据范围不限制） */
    public static final String SUPER_ADMIN_CODE = "SUPER_ADMIN";

    /** 连续登录失败锁定阈值 */
    public static final int MAX_FAIL_COUNT = 5;

    /** 锁定时长（分钟） */
    public static final int LOCK_MINUTES = 15;

    private IamConstants() {
    }
}
