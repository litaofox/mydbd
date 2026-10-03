package com.mydbd.iam.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

/**
 * 当前登录用户基本信息（id 为雪花大数，字符串输出）
 */
public record UserProfile(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String username,
        String realName,
        String phone,
        String email,
        Long deptId,
        String deptName,
        Integer mfaEnabled,
        LocalDateTime lastLoginTime) {
}
