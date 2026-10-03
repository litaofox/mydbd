package com.mydbd.analysis.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * GPS 点行（差分输入）
 */
@Data
public class GpsPointRow {

    private LocalDateTime gpsTime;
    private Integer speed;
}
