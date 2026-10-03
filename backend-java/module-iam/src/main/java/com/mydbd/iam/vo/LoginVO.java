package com.mydbd.iam.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * 登录/二步验证返回。
 * mfaRequired=true 时仅返回 mfaToken；false 时返回 access token 与用户基本信息。
 * userId 为雪花大数，统一以字符串输出，避免前端 JS 精度丢失。
 */
public record LoginVO(
        Boolean mfaRequired,
        String token,
        String mfaToken,
        @JsonSerialize(using = ToStringSerializer.class) Long userId,
        String username,
        String realName) {

    public static LoginVO access(String token, Long userId, String username, String realName) {
        return new LoginVO(false, token, null, userId, username, realName);
    }

    public static LoginVO mfaStep(String mfaToken, Long userId, String username) {
        return new LoginVO(true, null, mfaToken, userId, username, null);
    }
}
