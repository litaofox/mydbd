package com.mydbd.monitor.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 实时推送 WebSocket 处理器：单向服务端推送，不处理客户端消息。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeWebSocketHandler extends TextWebSocketHandler {

    private final SessionRegistry sessionRegistry;
    private final RealtimePushScheduler pushScheduler;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessionRegistry.add(session);
        // 立即推送一次位置快照，避免首屏空等
        pushScheduler.pushPointsOnce(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 单向推送，忽略客户端消息；可扩展为 ping/pong 或订阅控制
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionRegistry.remove(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("WS transport error for session {}", session.getId(), exception);
        sessionRegistry.remove(session);
    }
}
