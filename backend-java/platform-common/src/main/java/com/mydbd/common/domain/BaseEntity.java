package com.mydbd.common.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业务实体公共字段（对应 DDL 公共字段规范）
 */
@Data
public abstract class BaseEntity {

    @TableId
    private Long id;

    private String creator;

    @TableField("create_date")
    private LocalDateTime createDate;

    private String updater;

    @TableField("update_date")
    private LocalDateTime updateDate;
}
