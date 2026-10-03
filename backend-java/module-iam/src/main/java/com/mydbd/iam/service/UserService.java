package com.mydbd.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mydbd.common.api.ErrorCode;
import com.mydbd.common.api.PageData;
import com.mydbd.common.exception.BizException;
import com.mydbd.common.security.PermissionCache;
import com.mydbd.common.security.UserContext;
import com.mydbd.common.security.UserInfo;
import com.mydbd.iam.dto.IamRequests.UserSaveRequest;
import com.mydbd.iam.entity.SysRole;
import com.mydbd.iam.entity.SysUser;
import com.mydbd.iam.mapper.DataScopeDeptMapper;
import com.mydbd.iam.mapper.SysRoleMapper;
import com.mydbd.iam.mapper.SysUserMapper;
import com.mydbd.iam.mapper.SysUserRoleMapper;
import com.mydbd.iam.vo.UserDetailVO;
import com.mydbd.iam.vo.UserListVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 用户管理服务（管理员侧）
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{4,20}$");

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final DataScopeDeptMapper deptMapper;
    private final PasswordCodec passwordCodec;
    private final PermissionCache permissionCache;

    public PageData<UserListVO> page(long page, long size, String username, String realName,
                                     Integer status, Long deptId, Long roleId) {
        Page<UserListVO> result = userMapper.selectPageVo(new Page<>(page, clampSize(size)),
                username, realName, status, deptId, roleId);
        LocalDateTime now = LocalDateTime.now();
        result.getRecords().forEach(vo -> vo.setLocked(
                vo.getLockedUntil() != null && vo.getLockedUntil().isAfter(now)));
        return new PageData<>(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    public UserDetailVO detail(Long id) {
        requireUser(id);
        UserDetailVO vo = userMapper.selectDetailVo(id);
        if (vo == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        vo.setRoleIds(userRoleMapper.selectRoleIds(id));
        vo.setRoleCodes(roleMapper.selectActiveCodesByUserId(id));
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(UserSaveRequest req) {
        if (!StringUtils.hasText(req.username()) || !USERNAME_PATTERN.matcher(req.username()).matches()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "登录名须为 4~20 位字母、数字或下划线");
        }
        Long nameCount = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, req.username())
                .eq(SysUser::getValidMark, 1));
        if (nameCount > 0) {
            throw new BizException(ErrorCode.CONFLICT, "登录名已存在：" + req.username());
        }
        if (!PasswordCodec.isStrong(req.password())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "初始密码须为 8~20 位且同时包含字母与数字");
        }
        validateDept(req.deptId());
        List<SysRole> roles = validateRoles(req.roleIds());

        SysUser user = new SysUser();
        user.setUsername(req.username());
        user.setPasswordHash(passwordCodec.encode(req.password()));
        user.setRealName(req.realName());
        user.setPhone(emptyToNull(req.phone()));
        user.setEmail(emptyToNull(req.email()));
        user.setDeptId(req.deptId());
        user.setStatus(req.status() == null ? 1 : req.status());
        user.setMfaEnabled(0);
        user.setFailCount(0);
        user.setPwdUpdateTime(LocalDateTime.now());
        user.setRemark(req.remark());
        user.setValidMark(1);
        userMapper.insert(user);
        roles.forEach(r -> userRoleMapper.insert(user.getId(), r.getId()));
        return user.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, UserSaveRequest req) {
        SysUser user = requireUser(id);
        validateDept(req.deptId());
        validateRoles(req.roleIds());

        user.setRealName(req.realName());
        user.setPhone(emptyToNull(req.phone()));
        user.setEmail(emptyToNull(req.email()));
        user.setDeptId(req.deptId());
        user.setRemark(req.remark());
        if (req.status() != null) {
            if (req.status() == 0) {
                guardDisable(user);
            }
            user.setStatus(req.status());
        }
        if (StringUtils.hasText(req.password())) {
            if (!PasswordCodec.isStrong(req.password())) {
                throw new BizException(ErrorCode.BAD_REQUEST, "密码须为 8~20 位且同时包含字母与数字");
            }
            user.setPasswordHash(passwordCodec.encode(req.password()));
            user.setPwdUpdateTime(LocalDateTime.now());
        }
        userMapper.updateById(user);

        if (req.roleIds() != null) {
            guardRoleChange(user, req.roleIds());
            userRoleMapper.deleteByUser(id);
            validateRoles(req.roleIds()).forEach(r -> userRoleMapper.insert(id, r.getId()));
        }
        permissionCache.evict(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "状态值非法");
        }
        SysUser user = requireUser(id);
        if (status == 0) {
            guardDisable(user);
        }
        user.setStatus(status);
        if (status == 1) {
            user.setFailCount(0);
            user.setLockedUntil(null);
        }
        userMapper.updateById(user);
        permissionCache.evict(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id, String newPassword) {
        SysUser user = requireUser(id);
        if (!PasswordCodec.isStrong(newPassword)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "新密码须为 8~20 位且同时包含字母与数字");
        }
        user.setPasswordHash(passwordCodec.encode(newPassword));
        user.setPwdUpdateTime(LocalDateTime.now());
        user.setFailCount(0);
        user.setLockedUntil(null);
        userMapper.updateById(user);
        permissionCache.evict(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long id, List<Long> roleIds) {
        SysUser user = requireUser(id);
        guardRoleChange(user, roleIds);
        List<SysRole> roles = validateRoles(roleIds);
        userRoleMapper.deleteByUser(id);
        roles.forEach(r -> userRoleMapper.insert(id, r.getId()));
        permissionCache.evict(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void unlock(Long id) {
        SysUser user = requireUser(id);
        user.setFailCount(0);
        user.setLockedUntil(null);
        userMapper.updateById(user);
        permissionCache.evict(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysUser user = requireUser(id);
        if (IamConstants.ADMIN_USERNAME.equals(user.getUsername())) {
            throw new BizException(ErrorCode.CONFLICT, "超级管理员账号不可删除");
        }
        UserInfo current = UserContext.get();
        if (current != null && id.equals(current.userId())) {
            throw new BizException(ErrorCode.CONFLICT, "不可删除当前登录账号");
        }
        user.setValidMark(0);
        user.setStatus(0);
        userMapper.updateById(user);
        userRoleMapper.deleteByUser(id);
        permissionCache.evict(id);
    }

    // ===== 保护规则与校验 =====

    private void guardDisable(SysUser user) {
        if (IamConstants.ADMIN_USERNAME.equals(user.getUsername())) {
            throw new BizException(ErrorCode.CONFLICT, "超级管理员账号不可停用");
        }
        UserInfo current = UserContext.get();
        if (current != null && user.getId().equals(current.userId())) {
            throw new BizException(ErrorCode.CONFLICT, "不可停用当前登录账号");
        }
    }

    private void guardRoleChange(SysUser user, List<Long> roleIds) {
        if (IamConstants.ADMIN_USERNAME.equals(user.getUsername())) {
            Long superRoleId = findSuperAdminRoleId();
            if (superRoleId != null && (roleIds == null || !roleIds.contains(superRoleId))) {
                throw new BizException(ErrorCode.CONFLICT, "超级管理员账号必须保留超级管理员角色");
            }
        }
        UserInfo current = UserContext.get();
        if (current != null && user.getId().equals(current.userId())
                && (roleIds == null || roleIds.isEmpty())) {
            throw new BizException(ErrorCode.CONFLICT, "不可移除当前登录账号的全部角色");
        }
    }

    private Long findSuperAdminRoleId() {
        List<SysRole> all = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, IamConstants.SUPER_ADMIN_CODE)
                .eq(SysRole::getValidMark, 1));
        return all.isEmpty() ? null : all.get(0).getId();
    }

    private List<SysRole> validateRoles(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        List<Long> distinct = roleIds.stream().distinct().toList();
        List<SysRole> roles = roleMapper.selectBatchIds(distinct).stream()
                .filter(r -> r.getValidMark() != null && r.getValidMark() == 1
                        && r.getStatus() != null && r.getStatus() == 1)
                .toList();
        if (roles.size() != distinct.size()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "存在无效或已停用的角色");
        }
        return roles;
    }

    private void validateDept(Long deptId) {
        if (deptId == null) {
            return;
        }
        Set<Long> valid = deptMapper.selectAllNodes().stream()
                .map(DataScopeDeptMapper.DeptNode::id)
                .collect(Collectors.toSet());
        if (!valid.contains(deptId)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "归属部门不存在");
        }
    }

    private SysUser requireUser(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null || user.getValidMark() == null || user.getValidMark() != 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private String emptyToNull(String s) {
        return StringUtils.hasText(s) ? s : null;
    }

    private long clampSize(long size) {
        return size <= 0 ? 10 : Math.min(size, 100);
    }
}
