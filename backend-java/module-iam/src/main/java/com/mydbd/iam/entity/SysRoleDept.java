package com.mydbd.iam.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 角色-自定义数据范围部门关系
 */
@Data
@TableName("traj.sys_role_dept")
public class SysRoleDept {

    private Long roleId;

    private Long deptId;
}
