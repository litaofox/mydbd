package com.mydbd.analysis.profile.dto;

import lombok.Data;

/** 排行行（按车牌聚合） */
@Data
public class RankRow {
    private String plateNo;
    private String identityCode;
    private long eventCnt;
    private long highCnt;
    private long weightedScore;
}
