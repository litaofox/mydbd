package com.mydbd.analysis.profile.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 黑点网格聚合行 */
@Data
public class HotspotRow {
    private BigDecimal lng;
    private BigDecimal lat;
    private long eventCnt;
    private long weightedScore;
    private long plateCnt;
    private String topPlate;
    private String topCode;
}
