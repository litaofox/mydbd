package com.mydbd.notify.api;

import java.util.Collection;

/**
 * 实时推送通道抽象：由 module-monitor 的 SessionRegistry 实现，
 * 使 module-notify 不反向依赖 module-monitor（避免循环依赖）。
 */
public interface RealtimePusher {

    /**
     * 向指定用户的在线会话定向推送消息。
     *
     * @param userIds 接收人 id 集合
     * @param type    消息类型（如 NOTIFY）
     * @param data    消息载荷
     * @return 成功送达的会话数
     */
    int sendToUsers(Collection<Long> userIds, String type, Object data);

    /** 指定用户当前是否有在线会话 */
    boolean isOnline(Long userId);
}
