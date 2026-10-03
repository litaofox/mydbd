package com.mydbd.monitor.dto;

/**
 * F17 解除请求体（MOD-MON-004 §4.4）。
 * resultCode ∈ {00,01,02}，resultMsg 可选 ≤200 字；校验在 AlarmService 手写抛 40001。
 */
public record ResolveRequest(String resultCode, String resultMsg) {
}
