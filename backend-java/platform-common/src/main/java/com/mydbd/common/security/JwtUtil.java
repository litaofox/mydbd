package com.mydbd.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

/**
 * JWT 签发与解析。
 * access：业务访问令牌（8h，claims uid + purpose=access）
 * mfa：二次验证中间令牌（5min，purpose=mfa，仅可用于 /api/auth/mfa/verify）
 *
 * 算法固定 HS256：signWith 不显式传算法时 JJWT 会按密钥长度自选（≥48 字节选 HS384），
 * 曾导致 processing 侧 PyJWT 仅放行 HS256 时误判 401、前端被踢回登录页（2026-10-06 修复）。
 * 解析侧仍接受该密钥可用的同族 HMAC 算法，保证切换前签发的 HS384 存量令牌平滑过渡。
 */
@Component
public class JwtUtil {

    public static final String PURPOSE_ACCESS = "access";
    public static final String PURPOSE_MFA = "mfa";

    private final SecretKey key;
    private final Duration accessTtl = Duration.ofHours(8);
    private final Duration mfaTtl = Duration.ofMinutes(5);

    public JwtUtil(@Value("${mydbd.jwt.secret:${JWT_HMAC_SECRET:change-me-dev-secret}}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccess(Long userId, String username) {
        return build(userId, username, PURPOSE_ACCESS, accessTtl);
    }

    public String generateMfa(Long userId, String username) {
        return build(userId, username, PURPOSE_MFA, mfaTtl);
    }

    private String build(Long userId, String username, String purpose, Duration ttl) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .claim("uid", userId)
                .claim("purpose", purpose)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttl.toMillis()))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 从 claims 安全取 Long（jjwt 对小整数可能返回 Integer） */
    public static Long readUserId(Claims claims) {
        Object uid = claims.get("uid");
        return uid instanceof Number n ? n.longValue() : null;
    }
}
