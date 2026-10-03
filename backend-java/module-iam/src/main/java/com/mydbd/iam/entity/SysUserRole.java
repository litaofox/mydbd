package com.mydbd.iam.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户-角色关系（复合主键，仅用于 MyBatis-Plus 类型映射）
 */
@Data
@TableName("traj.sys_user_role")
public class SysUserRole {

    private Long userId;

    private Long roleId;
}
