package com.mydbd.iam.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mydbd.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 系统用户（traj.sys_user）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("traj.sys_user")
public class SysUser extends BaseEntity {

    private String username;

    /** pbkdf2$iterations$saltB64$hashB64 */
    private String passwordHash;

    private String realName;

    /** 可空字段使用 ALWAYS 更新策略，保证"清空"操作（set null）能真正写入数据库 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String phone;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String email;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long deptId;

    /** 1=启用 0=停用 */
    private Integer status;

    /** 1=已开通 TOTP */
    private Integer mfaEnabled;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String mfaSecret;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String mfaPendingSecret;

    private Integer failCount;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime lockedUntil;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime pwdUpdateTime;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime lastLoginTime;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String lastLoginIp;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;

    /** 1=有效 0=已删除 */
    private Integer validMark;
}
