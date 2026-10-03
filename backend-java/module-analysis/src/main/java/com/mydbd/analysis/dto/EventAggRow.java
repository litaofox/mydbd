package com.mydbd.analysis.dto;

import lombok.Data;

/**
 * 事件聚合行（车牌 × 事件码计数）
 */
@Data
public class EventAggRow {

    private String plateNo;
    private String eventCode;
    private Integer lv;
    private Integer cnt;
}
