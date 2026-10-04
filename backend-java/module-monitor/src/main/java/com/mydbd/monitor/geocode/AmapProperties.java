package com.mydbd.monitor.geocode;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 高德地图 Web 服务配置（application.yml: mydbd.amap.key，环境变量 AMAP_KEY） */
@Component
@ConfigurationProperties(prefix = "mydbd.amap")
public class AmapProperties {

    /** 高德 Web 服务 Key；为空时逆地理/检索接口返回 503 */
    private String key = "";

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key == null ? "" : key.trim();
    }

    public boolean isConfigured() {
        return !key.isEmpty();
    }
}
