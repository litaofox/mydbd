package com.mydbd.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 审计采集注册：audit.enabled=false 时整体关闭（切面同步关闭）。
 * 过滤器 order=0，位于 JwtAuthFilter(order=1) 外层，可捕获鉴权短路的匿名访问。
 */
@Configuration
@EnableConfigurationProperties(AuditProperties.class)
@ConditionalOnProperty(prefix = "audit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AuditRegistrar {

    @Bean
    public FilterRegistrationBean<AuditFilter> auditFilterRegistration(
            ApplicationEventPublisher publisher, AuditProperties properties,
            ObjectMapper objectMapper) {
        FilterRegistrationBean<AuditFilter> registration = new FilterRegistrationBean<>(
                new AuditFilter(publisher, properties, objectMapper));
        registration.addUrlPatterns("/api/*");
        registration.setName("auditFilter");
        registration.setOrder(0);
        return registration;
    }
}
