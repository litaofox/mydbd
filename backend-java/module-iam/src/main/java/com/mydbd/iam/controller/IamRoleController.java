package com.mydbd.iam.controller;

import com.mydbd.common.api.PageData;
import com.mydbd.common.api.Result;
import com.mydbd.common.audit.AuditLog;
import com.mydbd.common.security.Logical;
import com.mydbd.common.security.RequiresPerm;
import com.mydbd.iam.dto.IamRequests.RoleSaveRequest;
import com.mydbd.iam.service.RoleService;
import com.mydbd.iam.vo.OptionRoleVO;
import com.mydbd.iam.vo.RoleDetailVO;
import com.mydbd.iam.vo.RoleListVO;
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

import java.util.List;

/**
 * 角色管理接口（iam:role:view 查看 / iam:role:edit 写操作）
 */
@RestController
@RequestMapping("/api/iam/roles")
@RequiredArgsConstructor
@RequiresPerm("iam:role:view")
public class IamRoleController {

    private final RoleService roleService;

    @GetMapping
    public Result<PageData<RoleListVO>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return Result.ok(roleService.page(page, size, keyword, status));
    }

    /** 启用角色下拉（用户分配角色；有用户维护或角色查看权限即可） */
    @GetMapping("/all")
    @RequiresPerm(value = {"iam:role:view", "iam:user:edit"}, logical = Logical.OR)
    public Result<List<OptionRoleVO>> allEnabled() {
        List<OptionRoleVO> list = roleService.listAllEnabled().stream()
                .map(r -> new OptionRoleVO(r.getId(), r.getRoleCode(), r.getRoleName(), r.getDataScope()))
                .toList();
        return Result.ok(list);
    }

    @GetMapping("/{id}")
    public Result<RoleDetailVO> detail(@PathVariable Long id) {
        return Result.ok(roleService.detail(id));
    }

    @PostMapping
    @RequiresPerm("iam:role:edit")
    @AuditLog(module = "IAM", action = "CREATE", actionName = "新建角色", objectType = "ROLE")
    public Result<String> create(@Valid @RequestBody RoleSaveRequest request) {
        return Result.ok(String.valueOf(roleService.create(request)));
    }

    @PutMapping("/{id}")
    @RequiresPerm("iam:role:edit")
    @AuditLog(module = "IAM", action = "UPDATE", actionName = "编辑角色", objectType = "ROLE", objectId = "#id")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody RoleSaveRequest request) {
        roleService.update(id, request);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPerm("iam:role:edit")
    @AuditLog(module = "IAM", action = "DELETE", actionName = "删除角色", objectType = "ROLE", objectId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return Result.ok();
    }
}
