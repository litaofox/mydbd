package com.mydbd.iam.realm;

import com.mydbd.common.security.AuthRealm;
import com.mydbd.common.security.UserInfo;
import com.mydbd.iam.entity.SysRole;
import com.mydbd.iam.entity.SysUser;
import com.mydbd.iam.mapper.SysMenuMapper;
import com.mydbd.iam.mapper.SysUserMapper;
import com.mydbd.iam.service.DataScopeService;
import com.mydbd.iam.service.IamConstants;
import com.mydbd.iam.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 基于 IAM 库表的认证主体装载实现。
 */
@Component
@RequiredArgsConstructor
public class DbAuthRealm implements AuthRealm {

    private final SysUserMapper userMapper;
    private final SysMenuMapper menuMapper;
    private final RoleService roleService;
    private final DataScopeService dataScopeService;

    @Override
    public UserInfo loadByUserId(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null || user.getValidMark() == null || user.getValidMark() != 1
                || user.getStatus() == null || user.getStatus() != 1) {
            return null;
        }
        List<SysRole> roles = roleService.activeRolesOf(userId);
        boolean superAdmin = roles.stream().anyMatch(r -> IamConstants.SUPER_ADMIN_CODE.equals(r.getRoleCode()));
        Set<String> perms = superAdmin
                ? Set.of()
                : Set.copyOf(menuMapper.selectPermsByUserId(userId));
        Set<Long> deptScope = superAdmin ? null : dataScopeService.compute(user, roles);
        return new UserInfo(userId, user.getUsername(), user.getRealName(), user.getDeptId(),
                superAdmin, perms, deptScope);
    }
}
