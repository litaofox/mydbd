package com.mydbd.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mydbd.common.api.ErrorCode;
import com.mydbd.common.exception.BizException;
import com.mydbd.common.security.JwtUtil;
import com.mydbd.common.security.PermissionCache;
import com.mydbd.common.security.UserContext;
import com.mydbd.common.security.UserInfo;
import com.mydbd.iam.entity.SysUser;
import com.mydbd.iam.mapper.SysMenuMapper;
import com.mydbd.iam.mapper.SysRoleMapper;
import com.mydbd.iam.mapper.SysUserMapper;
import com.mydbd.iam.vo.LoginVO;
import com.mydbd.iam.vo.MenuNode;
import com.mydbd.iam.vo.MfaSetupInfo;
import com.mydbd.iam.vo.ProfileVO;
import com.mydbd.iam.vo.UserProfile;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 认证与自助服务：登录（含失败锁定）、TOTP 二步验证、个人档案、改密、MFA 绑定。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysMenuMapper menuMapper;
    private final PasswordCodec passwordCodec;
    private final TotpService totpService;
    private final JwtUtil jwtUtil;
    private final PermissionCache permissionCache;
    private final MenuService menuService;

    // 注意：本方法不能加 @Transactional——密码错误会抛异常，若存在事务，失败计数/锁定时间的
    // UPDATE 将被一并回滚，导致"连续失败 N 次锁定"策略失效。此处仅有单表单行写入，无需事务。
    public LoginVO login(String username, String password, String clientIp) {
        SysUser user = findActiveByUsername(username);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "账号已停用，请联系管理员");
        }
        LocalDateTime now = LocalDateTime.now();
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            throw new BizException(ErrorCode.LOCKED, lockMessage(user.getLockedUntil()));
        }

        if (!passwordCodec.matches(password, user.getPasswordHash())) {
            int fail = (user.getFailCount() == null ? 0 : user.getFailCount()) + 1;
            user.setFailCount(fail);
            if (fail >= IamConstants.MAX_FAIL_COUNT) {
                LocalDateTime until = now.plusMinutes(IamConstants.LOCK_MINUTES);
                user.setLockedUntil(until);
                userMapper.updateById(user);
                permissionCache.evict(user.getId());
                throw new BizException(ErrorCode.LOCKED,
                        "连续输错密码 " + IamConstants.MAX_FAIL_COUNT + " 次，账号已锁定至 "
                                + until.toLocalTime());
            }
            userMapper.updateById(user);
            permissionCache.evict(user.getId());
            throw new BizException(ErrorCode.UNAUTHORIZED,
                    "用户名或密码错误，剩余尝试次数 " + (IamConstants.MAX_FAIL_COUNT - fail) + " 次");
        }

        // 登录成功
        user.setFailCount(0);
        user.setLockedUntil(null);
        user.setLastLoginTime(now);
        user.setLastLoginIp(clientIp);
        userMapper.updateById(user);
        permissionCache.evict(user.getId());

        if (user.getMfaEnabled() != null && user.getMfaEnabled() == 1
                && StringUtils.hasText(user.getMfaSecret())) {
            return LoginVO.mfaStep(jwtUtil.generateMfa(user.getId(), user.getUsername()),
                    user.getId(), user.getUsername());
        }
        return LoginVO.access(jwtUtil.generateAccess(user.getId(), user.getUsername()),
                user.getId(), user.getUsername(), user.getRealName());
    }

    public LoginVO verifyMfa(String mfaToken, String totpCode) {
        Claims claims;
        try {
            claims = jwtUtil.parse(mfaToken);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "二次验证会话已失效，请重新登录");
        }
        if (!JwtUtil.PURPOSE_MFA.equals(claims.get("purpose", String.class))) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "验证会话无效");
        }
        Long userId = JwtUtil.readUserId(claims);
        SysUser user = userId == null ? null : userMapper.selectById(userId);
        if (user == null || user.getValidMark() == null || user.getValidMark() != 1
                || user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "账号不存在或已停用");
        }
        if (!totpService.verify(user.getMfaSecret(), totpCode)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "动态口令不正确");
        }
        return LoginVO.access(jwtUtil.generateAccess(user.getId(), user.getUsername()),
                user.getId(), user.getUsername(), user.getRealName());
    }

    public ProfileVO profile() {
        Long userId = currentUserId();
        SysUser user = requireSelf(userId);
        List<String> roles = roleMapper.selectActiveCodesByUserId(userId);
        boolean superAdmin = roles.contains(IamConstants.SUPER_ADMIN_CODE);
        List<String> perms = superAdmin
                ? menuMapper.selectAllEnabled().stream()
                    .map(m -> m.getPermCode()).filter(StringUtils::hasText).distinct().toList()
                : menuMapper.selectPermsByUserId(userId);
        List<MenuNode> menus = menuService.treeForUser(userId);
        String deptName = null;
        if (user.getDeptId() != null) {
            var detail = userMapper.selectDetailVo(userId);
            deptName = detail == null ? null : detail.getDeptName();
        }
        UserProfile profile = new UserProfile(user.getId(), user.getUsername(), user.getRealName(),
                user.getPhone(), user.getEmail(), user.getDeptId(), deptName,
                user.getMfaEnabled(), user.getLastLoginTime());
        return new ProfileVO(profile, roles, perms, menus);
    }

    public void changePassword(String oldPassword, String newPassword) {
        SysUser user = requireSelf(currentUserId());
        if (!passwordCodec.matches(oldPassword, user.getPasswordHash())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "原密码不正确");
        }
        if (!PasswordCodec.isStrong(newPassword)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "新密码须为 8~20 位且同时包含字母与数字");
        }
        if (passwordCodec.matches(newPassword, user.getPasswordHash())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "新密码不能与原密码相同");
        }
        user.setPasswordHash(passwordCodec.encode(newPassword));
        user.setPwdUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        permissionCache.evict(user.getId());
    }

    public void updateProfile(String phone, String email) {
        SysUser user = requireSelf(currentUserId());
        user.setPhone(StringUtils.hasText(phone) ? phone.trim() : null);
        user.setEmail(StringUtils.hasText(email) ? email.trim() : null);
        userMapper.updateById(user);
        permissionCache.evict(user.getId());
    }

    public MfaSetupInfo setupMfa() {
        SysUser user = requireSelf(currentUserId());
        if (user.getMfaEnabled() != null && user.getMfaEnabled() == 1) {
            throw new BizException(ErrorCode.CONFLICT, "动态口令已开通，如需更换请先关闭");
        }
        String secret = totpService.generateSecret();
        user.setMfaPendingSecret(secret);
        userMapper.updateById(user);
        return new MfaSetupInfo(secret, totpService.otpauthUri(user.getUsername(), secret));
    }

    public void enableMfa(String totpCode) {
        SysUser user = requireSelf(currentUserId());
        String pending = user.getMfaPendingSecret();
        if (!StringUtils.hasText(pending)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请先获取绑定密钥");
        }
        if (!totpService.verify(pending, totpCode)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "动态口令不正确，请确认手机时间与服务器一致");
        }
        user.setMfaEnabled(1);
        user.setMfaSecret(pending);
        user.setMfaPendingSecret(null);
        userMapper.updateById(user);
        permissionCache.evict(user.getId());
    }

    public void disableMfa(String password, String totpCode) {
        SysUser user = requireSelf(currentUserId());
        if (!passwordCodec.matches(password, user.getPasswordHash())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "登录密码不正确");
        }
        if (user.getMfaEnabled() != null && user.getMfaEnabled() == 1
                && !totpService.verify(user.getMfaSecret(), totpCode)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "动态口令不正确");
        }
        user.setMfaEnabled(0);
        user.setMfaSecret(null);
        user.setMfaPendingSecret(null);
        userMapper.updateById(user);
        permissionCache.evict(user.getId());
    }

    /** 管理员代用户关闭 MFA（手机丢失兜底） */
    public void adminDisableMfa(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null || user.getValidMark() == null || user.getValidMark() != 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        user.setMfaEnabled(0);
        user.setMfaSecret(null);
        user.setMfaPendingSecret(null);
        userMapper.updateById(user);
        permissionCache.evict(userId);
    }

    // ===== 内部 =====

    private Long currentUserId() {
        UserInfo current = UserContext.get();
        if (current == null || current.userId() == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录或登录已失效");
        }
        return current.userId();
    }

    private SysUser requireSelf(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null || user.getValidMark() == null || user.getValidMark() != 1) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "账号不存在或已停用");
        }
        return user;
    }

    private SysUser findActiveByUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        List<SysUser> list = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username.trim())
                .eq(SysUser::getValidMark, 1)
                .last("limit 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    private String lockMessage(LocalDateTime until) {
        return "账号已锁定，请于 " + until.toLocalTime() + " 后重试，或联系管理员解锁";
    }
}
