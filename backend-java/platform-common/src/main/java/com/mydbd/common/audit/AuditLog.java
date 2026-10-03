package com.mydbd.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作审计注解：标注在 Controller 方法上补充业务语义。
 * <p>POST/PUT/DELETE 默认已自动留痕，本注解用于：
 * 1) 让 GET 接口留痕（敏感查询/导出/视频调阅）；
 * 2) 细化模块/动作/对象语义；
 * 3) 通过 SpEL 从入参提取对象ID（如 "#id"、"#req.vehicleId"）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /** 模块编码，留空走路径兜底映射 */
    String module() default "";

    /** 动作编码，见 {@link AuditAction}，留空走 HTTP 方法兜底映射 */
    String action() default "";

    /** 动作中文名，留空走动作字典缺省文案 */
    String actionName() default "";

    /** 对象类型，如 VEHICLE/TERMINAL/DRIVER */
    String objectType() default "";

    /** 对象ID SpEL，引用方法参数名，如 "#id"、"#req.vehicleId"；留空走响应/路径兜底提取 */
    String objectId() default "";
}
