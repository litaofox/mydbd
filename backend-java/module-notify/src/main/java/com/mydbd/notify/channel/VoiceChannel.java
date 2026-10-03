package com.mydbd.notify.channel;

import com.mydbd.notify.api.NotifyEvent;
import com.mydbd.system.service.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** 语音外呼渠道预留：notify.voice.enabled */
@Component
@RequiredArgsConstructor
public class VoiceChannel implements NotifyChannel {

    private final ConfigService configService;

    @Override
    public String name() {
        return "VOICE";
    }

    @Override
    public boolean enabled() {
        return Boolean.TRUE.equals(configService.getBool("notify.voice.enabled", false));
    }

    @Override
    public void send(NotifyEvent event, List<Long> receiverIds) {
        throw new UnsupportedOperationException("语音外呼渠道未对接网关，暂不可用");
    }
}
