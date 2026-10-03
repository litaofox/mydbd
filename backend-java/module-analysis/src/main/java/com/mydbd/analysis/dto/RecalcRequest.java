package com.mydbd.analysis.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * F22 手动重算请求（MOD-ANA-001 §5.3）
 */
@Data
public class RecalcRequest {

    @NotBlank(message = "start 不能为空")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "start 需为 yyyy-MM-dd")
    private String start;

    @NotBlank(message = "end 不能为空")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "end 需为 yyyy-MM-dd")
    private String end;

    /** 可选，指定司机（traj_driver bigserial，String 传参防精度） */
    private String driverId;
}
