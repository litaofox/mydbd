package com.mydbd.analysis.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * F22 单人趋势点 VO
 */
@Data
public class TrendVO {

    private LocalDate scoreDate;
    private BigDecimal score;
    private String level;
}
