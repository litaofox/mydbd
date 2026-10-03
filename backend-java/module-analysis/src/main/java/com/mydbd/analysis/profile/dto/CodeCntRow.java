package com.mydbd.analysis.profile.dto;

import lombok.Data;

/** 事件码计数行（Top5 选码 / 构成通用） */
@Data
public class CodeCntRow {
    private String eventCode;
    private long cnt;
}
