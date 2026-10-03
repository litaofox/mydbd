package com.mydbd.notify.channel;

import com.mydbd.notify.api.NotifyEvent;
import com.mydbd.system.service.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** 短信渠道预留：notify.sms.enabled 开启且对接厂商后实现真实发送 */
@Component
@RequiredArgsConstructor
public class SmsChannel implements NotifyChannel {

    private final ConfigService configService;

    @Override
    public String name() {
        return "SMS";
    }

    @Override
    public boolean enabled() {
        return Boolean.TRUE.equals(configService.getBool("notify.sms.enabled", false));
    }

    @Override
    public void send(NotifyEvent event, List<Long> receiverIds) {
        throw new UnsupportedOperationException("短信渠道未对接厂商，暂不可用");
    }
}
