package com.mydbd.analysis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * F22 驾驶行为日评分快照（mon.driver_score，重算=upsert，无逻辑删除）
 */
@Data
@TableName(value = "mon.driver_score", autoResultMap = true)
public class DriverScore {

    @TableId(type = IdType.AUTO)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private LocalDate scoreDate;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long driverId;

    private String identityCode;

    private String plateNo;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long deptId;

    private BigDecimal score;

    /** A~E（字典 score_level） */
    private String level;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> features;

    private Integer samplePoints;

    private Integer eventCount;

    private LocalDateTime createDate;
}
