package com.mydbd.mdm.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mydbd.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 企业与组织架构（对应 traj.traj_dept）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("traj.traj_dept")
public class Dept extends BaseEntity {

    /** 上级组织ID，0=根节点 */
    private Long parentId;

    private String deptName;

    private String deptCode;

    /** 1=运输企业 2=车队/部门 */
    private Integer deptType;

    private String contactPerson;

    private String contactPhone;

    private String provinceCode;

    private String cityCode;

    private String countyCode;

    private String address;

    private Integer sortNo;

    /** 1=有效 0=失效 */
    private Integer validMark;

    /** 组织树下有效车辆数（非持久化） */
    @TableField(exist = false)
    private Long vehicleCount;

    /** 组织树子节点（非持久化） */
    @TableField(exist = false)
    private List<Dept> children;
}
