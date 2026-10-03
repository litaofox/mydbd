package com.mydbd.notify.channel;

import com.mydbd.notify.api.NotifyEvent;
import com.mydbd.system.service.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** App 推送渠道预留：notify.push.enabled */
@Component
@RequiredArgsConstructor
public class PushChannel implements NotifyChannel {

    private final ConfigService configService;

    @Override
    public String name() {
        return "PUSH";
    }

    @Override
    public boolean enabled() {
        return Boolean.TRUE.equals(configService.getBool("notify.push.enabled", false));
    }

    @Override
    public void send(NotifyEvent event, List<Long> receiverIds) {
        throw new UnsupportedOperationException("App 推送渠道未对接推送服务，暂不可用");
    }
}
