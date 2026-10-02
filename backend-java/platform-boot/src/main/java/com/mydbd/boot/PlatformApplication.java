package com.mydbd.boot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * mydbd 北斗导航数据业务平台启动入口
 */
@SpringBootApplication(scanBasePackages = "com.mydbd")
@MapperScan("com.mydbd.**.mapper")
public class PlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlatformApplication.class, args);
    }
}
