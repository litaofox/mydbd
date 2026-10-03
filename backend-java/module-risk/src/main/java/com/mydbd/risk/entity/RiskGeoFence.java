package com.mydbd.risk.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * F18 电子围栏（mon.risk_geo_fence）
 * polygon_geom 不走 MP，由 RiskGeoFenceMapper 的 ST_GeomFromGeoJSON 维护；
 * points 为瞬态字段：详情时由 ST_AsGeoJSON 解析回传，保存时入 GeoJSON。
 */
@Data
@TableName("mon.risk_geo_fence")
public class RiskGeoFence {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String fenceName;

    /** CIRCLE / POLYGON */
    private String fenceType;

    private BigDecimal centerLng;

    private BigDecimal centerLat;

    private Integer radiusM;

    /** 不参与 MP 自动 SQL（自定义 SQL 维护） */
    @TableField(exist = false)
    private List<List<BigDecimal>> points;

    /** 1进入 2离开 3进出 */
    private Integer triggerDir;

    private Integer riskLevel;

    private Integer cooldownSec;

    private Integer status;

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
