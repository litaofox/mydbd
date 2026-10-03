package com.mydbd.common.security;

import com.mydbd.common.api.ErrorCode;
import com.mydbd.common.exception.BizException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * {@link RequiresPerm} 权限切面：基于 UserContext 已装载的权限集判定，
 * 不查库、不依赖具体 IAM 模块。
 */
@Aspect
@Component
public class PermAspect {

    /** 方法级注解优先 */
    @Around("@annotation(rp)")
    public Object aroundMethod(ProceedingJoinPoint pjp, RequiresPerm rp) throws Throwable {
        check(rp);
        return pjp.proceed();
    }

    /** 类级注解：方法未显式标注时生效 */
    @Around("@within(rp) && !@annotation(com.mydbd.common.security.RequiresPerm)")
    public Object aroundType(ProceedingJoinPoint pjp, RequiresPerm rp) throws Throwable {
        check(rp);
        return pjp.proceed();
    }

    private void check(RequiresPerm rp) {
        UserInfo user = UserContext.get();
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录或登录已失效");
        }
        if (user.superAdmin()) {
            return;
        }
        Set<String> owned = user.perms();
        String[] required = rp.value();
        boolean pass;
        if (rp.logical() == Logical.OR) {
            pass = false;
            for (String code : required) {
                if (owned != null && owned.contains(code)) {
                    pass = true;
                    break;
                }
            }
        } else {
            pass = owned != null;
            if (pass) {
                for (String code : required) {
                    if (!owned.contains(code)) {
                        pass = false;
                        break;
                    }
                }
            }
        }
        if (!pass) {
            throw new BizException(ErrorCode.FORBIDDEN, "无操作权限：" + String.join(" / ", required));
        }
    }
}
