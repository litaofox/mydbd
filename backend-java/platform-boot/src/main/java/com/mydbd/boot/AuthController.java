package com.mydbd.boot;

import com.mydbd.common.api.ErrorCode;
import com.mydbd.common.api.Result;
import com.mydbd.common.exception.BizException;
import com.mydbd.common.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 登录认证（骨架阶段内置演示账号；后续迭代接入 IAM 用户体系）
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String DEMO_USER = "admin";
    private static final String DEMO_PASSWORD = "admin123";
    private static final String DEMO_ROLE = "ADMIN";

    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequest request) {
        if (request == null
                || !DEMO_USER.equals(request.username())
                || !DEMO_PASSWORD.equals(request.password())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        String token = jwtUtil.generate(DEMO_USER, DEMO_ROLE);
        return Result.ok(Map.of(
                "token", token,
                "username", DEMO_USER,
                "role", DEMO_ROLE));
    }

    public record LoginRequest(String username, String password) {
    }
}
