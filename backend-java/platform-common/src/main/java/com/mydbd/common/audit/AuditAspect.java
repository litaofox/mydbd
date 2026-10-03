package com.mydbd.common.audit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * 审计语义切面：解析 {@link AuditLog#objectId()} SpEL 表达式（引用方法入参），
 * 结果经 request attribute 传给 AuditFilter。过滤器层拿不到方法入参，故用 AOP 补齐。
 */
@Slf4j
@Aspect
@Component
@ConditionalOnProperty(prefix = "audit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AuditAspect {

    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer parameterNameDiscoverer =
            new DefaultParameterNameDiscoverer();

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint pjp, AuditLog auditLog) throws Throwable {
        if (!auditLog.objectId().isBlank()) {
            try {
                MethodSignature signature = (MethodSignature) pjp.getSignature();
                Method method = signature.getMethod();
                String[] paramNames = parameterNameDiscoverer.getParameterNames(method);
                if (paramNames != null) {
                    EvaluationContext context = new StandardEvaluationContext();
                    Object[] args = pjp.getArgs();
                    for (int i = 0; i < paramNames.length && i < args.length; i++) {
                        context.setVariable(paramNames[i], args[i]);
                    }
                    String objectId = parser.parseExpression(auditLog.objectId())
                            .getValue(context, String.class);
                    if (objectId != null && !objectId.isBlank()
                            && RequestContextHolder.getRequestAttributes()
                            instanceof ServletRequestAttributes sra) {
                        HttpServletRequest request = sra.getRequest();
                        request.setAttribute(AuditFilter.ATTR_SEMANTIC, new AuditSemantic(objectId));
                    }
                }
            } catch (Exception ex) {
                log.warn("审计 objectId SpEL 解析失败: expr={}", auditLog.objectId(), ex);
            }
        }
        return pjp.proceed();
    }
}
