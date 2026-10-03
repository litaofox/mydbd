package com.mydbd.analysis.profile.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 评分曲线行（driver_score 只读；dept 维度为日均值） */
@Data
public class ScoreCurveRow {
    private LocalDate scoreDate;
    private BigDecimal score;
    private String level;
}
