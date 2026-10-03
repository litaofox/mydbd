package com.mydbd.monitor.websocket;

import com.mydbd.common.security.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Map;

/**
 * WebSocket 握手鉴权：从 query 参数 token 解析 JWT，失败则拒绝握手。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = extractToken(request.getURI());
        if (token == null || token.isBlank()) {
            log.warn("WS handshake rejected: missing token");
            return false;
        }
        try {
            Claims claims = jwtUtil.parse(token);
            Object purpose = claims.get("purpose");
            if (!JwtUtil.PURPOSE_ACCESS.equals(purpose)) {
                log.warn("WS handshake rejected: token purpose={}", purpose);
                return false;
            }
            attributes.put("uid", JwtUtil.readUserId(claims));
            attributes.put("username", claims.getSubject());
            return true;
        } catch (Exception e) {
            log.warn("WS handshake rejected: invalid token, {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    private String extractToken(URI uri) {
        String query = uri.getQuery();
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && "token".equals(kv[0])) {
                return kv[1];
            }
        }
        return null;
    }
}
