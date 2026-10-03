package com.mydbd.iam.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mydbd.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统角色（traj.sys_role）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("traj.sys_role")
public class SysRole extends BaseEntity {

    private String roleCode;

    private String roleName;

    /** 1=全部 2=本企业及以下 3=本部门及以下 4=仅本部门 5=自定义 */
    private Integer dataScope;

    /** 1=内置 */
    private Integer builtIn;

    /** 1=启用 0=停用 */
    private Integer status;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;

    private Integer validMark;
}
