package com.mydbd.monitor.websocket;

import com.mydbd.common.security.AuthRealm;
import com.mydbd.common.security.PermissionCache;
import com.mydbd.common.security.UserInfo;
import com.mydbd.monitor.dashboard.mapper.DashboardMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashSet;
import java.util.Set;

/**
 * WebSocket 会话数据范围解析：把握手时存的 uid 换算为可见车牌集合。
 * 与 HTTP 请求链路共用 PermissionCache，权限调整两边同时收敛（TTL 120s）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WsScopeResolver {

    private final PermissionCache permissionCache;
    private final AuthRealm authRealm;
    private final DashboardMapper dashboardMapper;

    /**
     * @return {@code null}=不限制（全部数据）；空集合=不可见任何数据（fail-closed）；
     *         非空集合=可见车牌
     */
    public Set<String> allowedPlates(WebSocketSession session) {
        Object uidAttr = session.getAttributes().get("uid");
        if (!(uidAttr instanceof Number n)) {
            // 正常握手必写入 uid；缺失属异常情况，fail-closed
            return Set.of();
        }
        long uid = n.longValue();
        UserInfo user = permissionCache.getOrLoad(uid, () -> authRealm.loadByUserId(uid));
        if (user == null) {
            // 用户已停用/删除：停止向其推送任何数据
            return Set.of();
        }
        if (user.allData()) {
            return null;
        }
        Set<Long> scope = user.deptScope();
        if (scope == null || scope.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(dashboardMapper.selectVehicleNosByDeptScope(scope));
    }
}
