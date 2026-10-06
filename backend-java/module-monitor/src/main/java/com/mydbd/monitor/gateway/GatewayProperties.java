package com.mydbd.monitor.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** processing-service 内部接口配置（application.yml: mydbd.processing.base-url） */
@Component
@ConfigurationProperties(prefix = "mydbd.processing")
public class GatewayProperties {

    /** processing-service 内部地址（附件中转 / 接入状态代理） */
    private String baseUrl = "http://processing-app:8000";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        String v = baseUrl == null ? "" : baseUrl.trim();
        // 去掉尾部斜杠，拼接 path 时统一以 / 开头
        this.baseUrl = v.endsWith("/") ? v.substring(0, v.length() - 1) : v;
    }
}
