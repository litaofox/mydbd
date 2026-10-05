package com.mydbd.monitor.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mydbd.notify.api.RealtimePusher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 实时推送会话注册中心（内存态，单机演示）。
 * 同时实现 F19 RealtimePusher：按用户 id 定向推送。
 */
@Slf4j
@Component
public class SessionRegistry implements RealtimePusher {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    /** uid -> sessionId 集合（同一用户多标签页/多设备） */
    private final Map<Long, Set<String>> uidSessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    @Autowired
    public SessionRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void add(WebSocketSession session) {
        sessions.put(session.getId(), session);
        Long uid = resolveUid(session);
        if (uid != null) {
            uidSessions.computeIfAbsent(uid, k -> ConcurrentHashMap.newKeySet()).add(session.getId());
        }
        log.info("WS session added: id={}, uid={}, total={}", session.getId(), uid, sessions.size());
    }

    public void remove(WebSocketSession session) {
        sessions.remove(session.getId());
        Long uid = resolveUid(session);
        if (uid != null) {
            Set<String> ids = uidSessions.get(uid);
            if (ids != null) {
                ids.remove(session.getId());
                if (ids.isEmpty()) {
                    uidSessions.remove(uid);
                }
            }
        }
        log.info("WS session removed: id={}, total={}", session.getId(), sessions.size());
    }

    public int size() {
        return sessions.size();
    }

    /**
     * 向所有在线会话广播消息。单会话发送失败则移除，不影响其他。
     */
    public void broadcast(String type, Object data) {
        if (sessions.isEmpty()) {
            return;
        }
        String payload = serialize(type, data);
        if (payload == null) {
            return;
        }
        TextMessage message = new TextMessage(payload);
        for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
            WebSocketSession session = entry.getValue();
            send(session, message);
        }
    }

    /** 向单个会话推送（如新连接首帧） */
    public void sendTo(WebSocketSession session, String type, Object data) {
        String payload = serialize(type, data);
        if (payload != null) {
            send(session, new TextMessage(payload));
        }
    }

    /**
     * 按会话各自的数据范围广播。
     *
     * @param data    全量数据（在发送环节过滤，DB 只查一次）
     * @param plateOf 从数据行提取车牌
     * @param scopeOf 按会话给出范围：null=不限制；空集合=跳过该会话；非空=按车牌过滤
     *                同一次调用内按范围签名缓存序列化结果，多会话同范围不重复序列化
     */
    public <T> void broadcastScoped(String type, List<T> data,
                                    Function<T, String> plateOf,
                                    Function<WebSocketSession, Set<String>> scopeOf) {
        if (sessions.isEmpty()) {
            return;
        }
        Map<String, String> payloadByScopeKey = new HashMap<>();
        String unrestrictedPayload = null;
        for (WebSocketSession session : sessions.values()) {
            Set<String> scope = scopeOf.apply(session);
            String payload;
            if (scope == null) {
                if (unrestrictedPayload == null) {
                    unrestrictedPayload = serialize(type, data);
                }
                payload = unrestrictedPayload;
            } else {
                if (scope.isEmpty()) {
                    continue;
                }
                String key = new TreeSet<>(scope).stream().collect(Collectors.joining(","));
                payload = payloadByScopeKey.computeIfAbsent(key, k -> serialize(type,
                        data.stream().filter(x -> scope.contains(plateOf.apply(x))).toList()));
            }
            if (payload != null) {
                send(session, new TextMessage(payload));
            }
        }
    }

    /**
     * 单会话按范围推送（新连接首帧）。
     * scope=null 不限制；空集合不发送；非空按车牌过滤。
     */
    public <T> void sendScoped(WebSocketSession session, String type, List<T> data,
                               Function<T, String> plateOf, Set<String> scope) {
        if (scope != null && scope.isEmpty()) {
            return;
        }
        List<T> filtered = scope == null ? data
                : data.stream().filter(x -> scope.contains(plateOf.apply(x))).toList();
        sendTo(session, type, filtered);
    }

    // ============================== F19 RealtimePusher ==============================

    @Override
    public int sendToUsers(Collection<Long> userIds, String type, Object data) {
        if (userIds == null || userIds.isEmpty()) {
            return 0;
        }
        String payload = serialize(type, data);
        if (payload == null) {
            return 0;
        }
        TextMessage message = new TextMessage(payload);
        int delivered = 0;
        for (Long uid : userIds) {
            Set<String> ids = uidSessions.get(uid);
            if (ids == null) {
                continue;
            }
            for (String sessionId : ids) {
                WebSocketSession session = sessions.get(sessionId);
                if (session != null && session.isOpen()) {
                    send(session, message);
                    delivered++;
                }
            }
        }
        return delivered;
    }

    @Override
    public boolean isOnline(Long userId) {
        Set<String> ids = uidSessions.get(userId);
        return ids != null && !ids.isEmpty();
    }

    private Long resolveUid(WebSocketSession session) {
        Object uid = session.getAttributes().get("uid");
        return uid instanceof Number n ? n.longValue() : null;
    }

    private void send(WebSocketSession session, TextMessage message) {
        if (session.isOpen()) {
            try {
                session.sendMessage(message);
            } catch (IOException e) {
                log.warn("WS send failed, removing session {}", session.getId(), e);
                remove(session);
            }
        } else {
            sessions.remove(session.getId());
        }
    }

    private String serialize(String type, Object data) {
        try {
            return objectMapper.writeValueAsString(Map.of("type", type, "data", data));
        } catch (Exception e) {
            log.error("WS serialize failed for type={}", type, e);
            return null;
        }
    }
}
