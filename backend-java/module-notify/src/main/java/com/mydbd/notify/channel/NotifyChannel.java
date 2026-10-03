package com.mydbd.notify.channel;

import com.mydbd.notify.api.NotifyEvent;

import java.util.List;

/**
 * 外部通知渠道抽象（短信/语音/App 推送）。
 * 本期仅接口 + 开关 + 发送意图日志，未对接厂商时状态记 SKIPPED。
 */
public interface NotifyChannel {

    /** 渠道名：SMS / VOICE / PUSH */
    String name();

    /** 是否启用（读系统参数） */
    boolean enabled();

    /** 发送；抛异常由调用方捕获记 FAILED */
    void send(NotifyEvent event, List<Long> receiverIds);
}
