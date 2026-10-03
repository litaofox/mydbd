package com.mydbd.analysis.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * F22 等级分布汇总 VO
 */
@Data
public class SummaryVO {

    private BigDecimal avgScore;
    private long recordCount;
    private long driverCount;
    private List<LevelCount> levelDist;

    @Data
    public static class LevelCount {
        private String level;
        private long count;
    }
}
