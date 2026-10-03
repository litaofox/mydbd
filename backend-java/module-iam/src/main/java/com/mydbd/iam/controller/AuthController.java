package com.mydbd.iam.controller;

import com.mydbd.common.api.Result;
import com.mydbd.common.audit.AuditAction;
import com.mydbd.common.audit.AuditLog;
import com.mydbd.iam.dto.IamRequests.ChangePasswordRequest;
import com.mydbd.iam.dto.IamRequests.LoginRequest;
import com.mydbd.iam.dto.IamRequests.MfaDisableRequest;
import com.mydbd.iam.dto.IamRequests.MfaEnableRequest;
import com.mydbd.iam.dto.IamRequests.MfaVerifyRequest;
import com.mydbd.iam.dto.IamRequests.ProfileUpdateRequest;
import com.mydbd.iam.service.AuthService;
import com.mydbd.iam.vo.LoginVO;
import com.mydbd.iam.vo.MfaSetupInfo;
import com.mydbd.iam.vo.ProfileVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证与个人自助接口
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request,
                                 HttpServletRequest httpRequest) {
        return Result.ok(authService.login(request.username(), request.password(), clientIp(httpRequest)));
    }

    @PostMapping("/mfa/verify")
    public Result<LoginVO> verifyMfa(@Valid @RequestBody MfaVerifyRequest request) {
        return Result.ok(authService.verifyMfa(request.mfaToken(), request.totpCode()));
    }

    @PostMapping("/logout")
    @AuditLog(module = "AUTH", action = "LOGOUT", actionName = "退出登录")
    public Result<Void> logout() {
        // JWT 无状态，服务端不做令牌拉黑；登出动作由审计过滤器留痕
        return Result.ok();
    }

    @GetMapping("/profile")
    public Result<ProfileVO> profile() {
        return Result.ok(authService.profile());
    }

    @PutMapping("/profile")
    public Result<Void> updateProfile(@Valid @RequestBody ProfileUpdateRequest request) {
        authService.updateProfile(request.phone(), request.email());
        return Result.ok();
    }

    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request.oldPassword(), request.newPassword());
        return Result.ok();
    }

    @GetMapping("/mfa/setup")
    public Result<MfaSetupInfo> setupMfa() {
        return Result.ok(authService.setupMfa());
    }

    @PostMapping("/mfa/enable")
    public Result<Void> enableMfa(@Valid @RequestBody MfaEnableRequest request) {
        authService.enableMfa(request.totpCode());
        return Result.ok();
    }

    @PostMapping("/mfa/disable")
    public Result<Void> disableMfa(@Valid @RequestBody MfaDisableRequest request) {
        authService.disableMfa(request.password(), request.totpCode());
        return Result.ok();
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String real = request.getHeader("X-Real-IP");
        return StringUtils.hasText(real) ? real.trim() : request.getRemoteAddr();
    }
}
