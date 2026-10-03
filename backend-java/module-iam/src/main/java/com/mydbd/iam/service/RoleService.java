package com.mydbd.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mydbd.common.api.ErrorCode;
import com.mydbd.common.api.PageData;
import com.mydbd.common.exception.BizException;
import com.mydbd.common.security.PermissionCache;
import com.mydbd.iam.dto.IamRequests.RoleSaveRequest;
import com.mydbd.iam.entity.SysRole;
import com.mydbd.iam.mapper.DataScopeDeptMapper;
import com.mydbd.iam.mapper.SysMenuMapper;
import com.mydbd.iam.mapper.SysRoleDeptMapper;
import com.mydbd.iam.mapper.SysRoleMapper;
import com.mydbd.iam.mapper.SysRoleMenuMapper;
import com.mydbd.iam.mapper.SysUserRoleMapper;
import com.mydbd.iam.vo.RoleDetailVO;
import com.mydbd.iam.vo.RoleListVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 角色服务：角色 CRUD + 功能权限/数据范围配置
 */
@Service
@RequiredArgsConstructor
public class RoleService {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,39}$");

    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysRoleDeptMapper roleDeptMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysMenuMapper menuMapper;
    private final DataScopeDeptMapper deptMapper;
    private final PermissionCache permissionCache;

    public PageData<RoleListVO> page(long page, long size, String keyword, Integer status) {
        Page<RoleListVO> result = roleMapper.selectPageVo(new Page<>(page, clampSize(size)), keyword, status);
        return new PageData<>(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    /** 启用角色下拉（用户分配角色用） */
    public List<SysRole> listAllEnabled() {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getValidMark, 1)
                .eq(SysRole::getStatus, 1)
                .orderByAsc(SysRole::getId));
    }

    public RoleDetailVO detail(Long id) {
        SysRole role = requireRole(id);
        RoleDetailVO vo = new RoleDetailVO();
        vo.setId(role.getId());
        vo.setRoleCode(role.getRoleCode());
        vo.setRoleName(role.getRoleName());
        vo.setDataScope(role.getDataScope());
        vo.setBuiltIn(role.getBuiltIn());
        vo.setStatus(role.getStatus());
        vo.setRemark(role.getRemark());
        vo.setMenuIds(roleMenuMapper.selectMenuIds(id));
        vo.setDeptIds(roleDeptMapper.selectDeptIds(id));
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(RoleSaveRequest req) {
        validateCodeUnique(req.roleCode(), null);
        validateScope(req.dataScope());
        validateMenus(req.menuIds());
        validateDepts(req.dataScope(), req.deptIds());

        SysRole role = new SysRole();
        role.setRoleCode(req.roleCode());
        role.setRoleName(req.roleName());
        role.setDataScope(req.dataScope());
        role.setStatus(req.status() == null ? 1 : req.status());
        role.setBuiltIn(0);
        role.setRemark(req.remark());
        role.setValidMark(1);
        roleMapper.insert(role);
        saveRelations(role.getId(), role.getDataScope(), req);
        return role.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, RoleSaveRequest req) {
        SysRole role = requireRole(id);
        boolean builtIn = role.getBuiltIn() != null && role.getBuiltIn() == 1;
        // 内置角色编码、数据范围不可改
        if (!builtIn && !role.getRoleCode().equals(req.roleCode())) {
            validateCodeUnique(req.roleCode(), id);
            role.setRoleCode(req.roleCode());
        }
        if (!builtIn) {
            validateScope(req.dataScope());
            role.setDataScope(req.dataScope());
        }
        validateMenus(req.menuIds());
        validateDepts(role.getDataScope(), req.deptIds());

        role.setRoleName(req.roleName());
        role.setStatus(req.status() == null ? role.getStatus() : req.status());
        role.setRemark(req.remark());
        roleMapper.updateById(role);

        roleMenuMapper.deleteByRole(id);
        roleDeptMapper.deleteByRole(id);
        saveRelations(id, role.getDataScope(), req);
        // 角色变更影响所有在线用户
        permissionCache.evictAll();
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysRole role = requireRole(id);
        if (role.getBuiltIn() != null && role.getBuiltIn() == 1) {
            throw new BizException(ErrorCode.CONFLICT, "内置角色不可删除");
        }
        long count = userRoleMapper.countActiveUsers(id);
        if (count > 0) {
            throw new BizException(ErrorCode.CONFLICT, "该角色已分配给 " + count + " 个用户，请先解除");
        }
        role.setValidMark(0);
        roleMapper.updateById(role);
        roleMenuMapper.deleteByRole(id);
        roleDeptMapper.deleteByRole(id);
        userRoleMapper.deleteByRole(id);
        permissionCache.evictAll();
    }

    /** 用户当前生效（有效+启用）角色实体 */
    public List<SysRole> activeRolesOf(Long userId) {
        List<Long> ids = userRoleMapper.selectRoleIds(userId);
        if (ids.isEmpty()) {
            return List.of();
        }
        return roleMapper.selectBatchIds(ids).stream()
                .filter(r -> r.getValidMark() != null && r.getValidMark() == 1
                        && r.getStatus() != null && r.getStatus() == 1)
                .toList();
    }

    // ===== 内部 =====

    private void saveRelations(Long roleId, Integer dataScope, RoleSaveRequest req) {
        if (req.menuIds() != null) {
            req.menuIds().forEach(menuId -> roleMenuMapper.insert(roleId, menuId));
        }
        // 仅自定义范围保存部门关系，其他范围保持空表，避免脏数据
        if (dataScope != null && dataScope == 5 && req.deptIds() != null) {
            req.deptIds().forEach(deptId -> roleDeptMapper.insert(roleId, deptId));
        }
    }

    private SysRole requireRole(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null || role.getValidMark() == null || role.getValidMark() != 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "角色不存在");
        }
        return role;
    }

    private void validateCodeUnique(String code, Long excludeId) {
        if (!StringUtils.hasText(code) || !CODE_PATTERN.matcher(code).matches()) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "角色编码须为 2~40 位大写字母/数字/下划线，且以大写字母开头");
        }
        Long count = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, code)
                .eq(SysRole::getValidMark, 1)
                .ne(excludeId != null, SysRole::getId, excludeId));
        if (count > 0) {
            throw new BizException(ErrorCode.CONFLICT, "角色编码已存在：" + code);
        }
    }

    private void validateScope(Integer dataScope) {
        if (dataScope == null || dataScope < 1 || dataScope > 5) {
            throw new BizException(ErrorCode.BAD_REQUEST, "数据范围取值非法");
        }
    }

    private void validateMenus(List<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return;
        }
        long count = menuMapper.selectBatchIds(menuIds).size();
        if (count != Set.copyOf(menuIds).size()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "存在无效的权限点");
        }
    }

    private void validateDepts(Integer dataScope, List<Long> deptIds) {
        if (dataScope == null || dataScope != 5) {
            return;
        }
        if (deptIds == null || deptIds.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "自定义数据范围必须勾选至少一个部门");
        }
        Set<Long> valid = Set.copyOf(deptMapper.selectAllNodes().stream()
                .map(DataScopeDeptMapper.DeptNode::id).toList());
        if (!valid.containsAll(deptIds)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "存在无效的部门");
        }
    }

    private long clampSize(long size) {
        return size <= 0 ? 10 : Math.min(size, 100);
    }
}
