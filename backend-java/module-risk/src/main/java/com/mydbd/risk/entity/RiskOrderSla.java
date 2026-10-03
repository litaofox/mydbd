package com.mydbd.risk.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * F20 工单处置时限配置（mon.risk_order_sla）
 */
@Data
@TableName("mon.risk_order_sla")
public class RiskOrderSla {

    /** 1低 2中 3高 */
    @TableId(type = IdType.INPUT)
    private Integer riskLevel;

    /** 处置时限（分钟） */
    private Integer limitMin;

    /** 升级宽限（分钟） */
    private Integer graceMin;

    @TableField("update_date")
    private LocalDateTime updateDate;
}
