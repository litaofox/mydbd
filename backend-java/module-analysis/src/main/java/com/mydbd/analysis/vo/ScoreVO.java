package com.mydbd.analysis.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * F22 评分列表行 VO（id/driverId/deptId 字符串化防 JS 精度）
 */
@Data
public class ScoreVO {

    private String id;
    private LocalDate scoreDate;
    private String driverId;
    private String driverName;
    private String identityCode;
    private String plateNo;
    private String deptId;
    private BigDecimal score;
    private String level;
    private Integer samplePoints;
    private Integer eventCount;
    private Map<String, Object> features;
}
