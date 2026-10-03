package com.mydbd.common.config;

import com.mydbd.common.security.AuthRealm;
import com.mydbd.common.security.JwtAuthFilter;
import com.mydbd.common.security.JwtUtil;
import com.mydbd.common.security.PermissionCache;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：CORS + JWT 过滤器注册（仅拦截 /api/**）
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtFilterRegistration(
            JwtUtil jwtUtil, ObjectProvider<AuthRealm> authRealmProvider,
            PermissionCache permissionCache) {
        AuthRealm authRealm = authRealmProvider.getIfAvailable();
        FilterRegistrationBean<JwtAuthFilter> registration =
                new FilterRegistrationBean<>(new JwtAuthFilter(jwtUtil, authRealm, permissionCache));
        registration.addUrlPatterns("/api/*");
        registration.setName("jwtAuthFilter");
        registration.setOrder(1);
        return registration;
    }
}
