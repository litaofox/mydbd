package com.mydbd.analysis.profile.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** Top5 事件码分桶序列行 */
@Data
public class CodeBucketRow {
    private LocalDateTime bucket;
    private String eventCode;
    private long cnt;
}
