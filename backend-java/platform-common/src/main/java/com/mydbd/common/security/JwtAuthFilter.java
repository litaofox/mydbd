package com.mydbd.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mydbd.common.api.ErrorCode;
import com.mydbd.common.api.Result;
import com.mydbd.common.audit.AuditFilter;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * JWT 认证过滤器：校验 Bearer Token，经 {@link AuthRealm} 装载用户主体并写入 UserContext。
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Set<String> WHITELIST = Set.of(
            "/api/auth/login",
            "/api/auth/mfa/verify");

    private final JwtUtil jwtUtil;
    private final AuthRealm authRealm;
    private final PermissionCache permissionCache;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JwtAuthFilter(JwtUtil jwtUtil, AuthRealm authRealm, PermissionCache permissionCache) {
        this.jwtUtil = jwtUtil;
        this.authRealm = authRealm;
        this.permissionCache = permissionCache;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return WHITELIST.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (!StringUtils.hasText(token)) {
            writeUnauthorized(response, "缺少认证令牌");
            return;
        }
        try {
            Claims claims = jwtUtil.parse(token);
            String purpose = claims.get("purpose", String.class);
            if (!JwtUtil.PURPOSE_ACCESS.equals(purpose)) {
                writeUnauthorized(response, "令牌类型无效");
                return;
            }
            Long userId = JwtUtil.readUserId(claims);
            if (userId == null) {
                writeUnauthorized(response, "令牌内容无效，请重新登录");
                return;
            }
            UserInfo user = permissionCache.getOrLoad(userId, () ->
                    authRealm == null ? null : authRealm.loadByUserId(userId));
            if (user == null) {
                writeUnauthorized(response, "账号不存在或已停用");
                return;
            }
            UserContext.set(user);
            // 供外层 AuditFilter 在链返回后读取（此时 ThreadLocal 已被清理）
            request.setAttribute(AuditFilter.ATTR_USER, user);
            chain.doFilter(request, response);
        } catch (Exception ex) {
            writeUnauthorized(response, "令牌无效或已过期");
        } finally {
            UserContext.clear();
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                Result.fail(ErrorCode.UNAUTHORIZED.code(), message)));
    }
}
