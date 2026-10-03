package com.mydbd.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mydbd.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("traj.sys_dict_item")
public class DictItem extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long dictTypeId;
    private String itemLabel;
    private String itemValue;
    private Integer sort;
    private Integer status;
    private String cssClass;
    private String remark;
}
