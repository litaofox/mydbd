package com.mydbd.analysis.profile.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 趋势分桶行（bucket + total/high/mid/low） */
@Data
public class TrendBucketRow {
    private LocalDateTime bucket;
    private long total;
    private long highCnt;
    private long midCnt;
    private long lowCnt;
}
