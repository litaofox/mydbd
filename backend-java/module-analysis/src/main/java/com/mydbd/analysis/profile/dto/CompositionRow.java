package com.mydbd.analysis.profile.dto;

import lombok.Data;

/** 事件构成行（按 event_code 分等级计数） */
@Data
public class CompositionRow {
    private String eventCode;
    private long cnt;
    private long high;
    private long mid;
    private long low;
}
