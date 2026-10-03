package com.mydbd.iam.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.List;

/**
 * 角色详情（含权限点与自定义部门；角色 id 字符串输出，菜单/部门 id 为小号数值）
 */
@Data
public class RoleDetailVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String roleCode;
    private String roleName;
    private Integer dataScope;
    private Integer builtIn;
    private Integer status;
    private String remark;

    private List<Long> menuIds;
    private List<Long> deptIds;
}
