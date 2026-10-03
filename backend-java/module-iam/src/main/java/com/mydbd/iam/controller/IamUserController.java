package com.mydbd.iam.controller;

import com.mydbd.common.api.PageData;
import com.mydbd.common.api.Result;
import com.mydbd.common.audit.AuditLog;
import com.mydbd.common.security.RequiresPerm;
import com.mydbd.iam.dto.IamRequests.AssignRolesRequest;
import com.mydbd.iam.dto.IamRequests.ResetPasswordRequest;
import com.mydbd.iam.dto.IamRequests.UserSaveRequest;
import com.mydbd.iam.service.AuthService;
import com.mydbd.iam.service.UserService;
import com.mydbd.iam.vo.UserDetailVO;
import com.mydbd.iam.vo.UserListVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理接口（iam:user:view 查看 / iam:user:edit 写操作）
 */
@RestController
@RequestMapping("/api/iam/users")
@RequiredArgsConstructor
@RequiresPerm("iam:user:view")
public class IamUserController {

    private final UserService userService;
    private final AuthService authService;

    @GetMapping
    public Result<PageData<UserListVO>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String realName,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) Long roleId) {
        return Result.ok(userService.page(page, size, username, realName, status, deptId, roleId));
    }

    @GetMapping("/{id}")
    public Result<UserDetailVO> detail(@PathVariable Long id) {
        return Result.ok(userService.detail(id));
    }

    @PostMapping
    @RequiresPerm("iam:user:edit")
    @AuditLog(module = "IAM", action = "CREATE", actionName = "新建用户", objectType = "USER")
    public Result<String> create(@Valid @RequestBody UserSaveRequest request) {
        return Result.ok(String.valueOf(userService.create(request)));
    }

    @PutMapping("/{id}")
    @RequiresPerm("iam:user:edit")
    @AuditLog(module = "IAM", action = "UPDATE", actionName = "编辑用户", objectType = "USER", objectId = "#id")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UserSaveRequest request) {
        userService.update(id, request);
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    @RequiresPerm("iam:user:edit")
    @AuditLog(module = "IAM", action = "UPDATE", actionName = "启停用用户", objectType = "USER", objectId = "#id")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        userService.changeStatus(id, status);
        return Result.ok();
    }

    @PutMapping("/{id}/reset-password")
    @RequiresPerm("iam:user:edit")
    @AuditLog(module = "IAM", action = "UPDATE", actionName = "重置密码", objectType = "USER", objectId = "#id")
    public Result<Void> resetPassword(@PathVariable Long id,
                                      @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(id, request.newPassword());
        return Result.ok();
    }

    @PutMapping("/{id}/roles")
    @RequiresPerm("iam:user:edit")
    @AuditLog(module = "IAM", action = "UPDATE", actionName = "分配角色", objectType = "USER", objectId = "#id")
    public Result<Void> assignRoles(@PathVariable Long id,
                                    @Valid @RequestBody AssignRolesRequest request) {
        userService.assignRoles(id, request.roleIds());
        return Result.ok();
    }

    @PutMapping("/{id}/unlock")
    @RequiresPerm("iam:user:edit")
    @AuditLog(module = "IAM", action = "HANDLE", actionName = "解锁用户", objectType = "USER", objectId = "#id")
    public Result<Void> unlock(@PathVariable Long id) {
        userService.unlock(id);
        return Result.ok();
    }

    @PutMapping("/{id}/mfa/disable")
    @RequiresPerm("iam:user:edit")
    @AuditLog(module = "IAM", action = "UPDATE", actionName = "关闭动态口令", objectType = "USER", objectId = "#id")
    public Result<Void> disableMfa(@PathVariable Long id) {
        authService.adminDisableMfa(id);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPerm("iam:user:edit")
    @AuditLog(module = "IAM", action = "DELETE", actionName = "删除用户", objectType = "USER", objectId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.ok();
    }
}
