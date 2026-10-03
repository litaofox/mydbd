package com.mydbd.risk.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * F18 风控规则（mon.risk_rule）
 */
@Data
@TableName(value = "mon.risk_rule", autoResultMap = true)
public class RiskRule {

    /** 低频配置表，bigserial 小号 id */
    @TableId(type = IdType.AUTO)
    private Long id;

    private String ruleCode;

    private String ruleName;

    /** SPEED / FATIGUE / SIGNAL / COMBO */
    private String ruleType;

    /** 产出事件编码；SIGNAL 类即信号码 */
    private String eventCode;

    /** 1低 2中 3高 */
    private Integer riskLevel;

    /** 类型化参数（jsonb） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> params;

    /** 同车同规则冷却秒数 */
    private Integer cooldownSec;

    /** 1启用 0停用 */
    private Integer status;

    /** 1内置（不可删） */
    private Integer builtIn;

    private Integer validMark;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private String creator;

    @TableField(value = "create_date", fill = FieldFill.INSERT)
    private LocalDateTime createDate;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updater;

    @TableField(value = "update_date", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateDate;
}
