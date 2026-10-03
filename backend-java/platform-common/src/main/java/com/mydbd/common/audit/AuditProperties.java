package com.mydbd.common.audit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 审计配置（audit.*）
 */
@Data
@ConfigurationProperties(prefix = "audit")
public class AuditProperties {

    /** 总开关，默认开启 */
    private boolean enabled = true;

    /** 不做审计的路径（Ant 模式） */
    private List<String> excludePaths = List.of("/api/audit/**");
}
