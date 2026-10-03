package com.mydbd.iam.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 菜单/按钮权限点（traj.sys_menu，版本种子维护）
 */
@Data
@TableName("traj.sys_menu")
public class SysMenu {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long parentId;

    private String menuName;

    /** 1=目录 2=菜单 3=按钮 */
    private Integer menuType;

    private String permCode;

    private String path;

    private String icon;

    private Integer sortNo;

    private Integer visible;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createDate;
}
