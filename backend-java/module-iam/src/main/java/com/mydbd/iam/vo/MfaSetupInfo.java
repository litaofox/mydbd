package com.mydbd.iam.vo;

/**
 * MFA 绑定准备信息：密钥仅在此响应中出现一次
 */
public record MfaSetupInfo(String secret, String otpauthUri) {
}
