package com.mydbd.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mydbd.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("traj.sys_dict_type")
public class DictType extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String dictCode;
    private String dictName;
    private Integer status;
    private String remark;
}
