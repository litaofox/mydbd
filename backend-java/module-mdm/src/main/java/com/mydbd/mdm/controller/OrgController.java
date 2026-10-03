package com.mydbd.mdm.controller;

import com.mydbd.common.api.Result;
import com.mydbd.common.security.Logical;
import com.mydbd.common.security.RequiresPerm;
import com.mydbd.mdm.dto.DeptSaveRequest;
import com.mydbd.mdm.entity.Dept;
import com.mydbd.mdm.service.OrgService;
import com.mydbd.mdm.vo.OptionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 组织架构接口
 */
@RestController
@RequestMapping("/api/mdm/depts")
@RequiredArgsConstructor
public class OrgController {

    private final OrgService orgService;

    /** 组织树：主数据查看或用户维护（用户归属部门选择）均需要 */
    @GetMapping("/tree")
    @RequiresPerm(value = {"mdm:org:view", "iam:user:edit"}, logical = Logical.OR)
    public Result<List<Dept>> tree() {
        return Result.ok(orgService.tree());
    }

    @GetMapping("/options")
    @RequiresPerm(value = {"mdm:org:view", "iam:user:edit"}, logical = Logical.OR)
    public Result<List<OptionVO>> options() {
        return Result.ok(orgService.options());
    }

    @PostMapping
    @RequiresPerm("mdm:org:edit")
    public Result<Long> create(@Valid @RequestBody DeptSaveRequest request) {
        return Result.ok(orgService.create(request));
    }

    @PutMapping("/{id}")
    @RequiresPerm("mdm:org:edit")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody DeptSaveRequest request) {
        orgService.update(id, request);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPerm("mdm:org:edit")
    public Result<Void> delete(@PathVariable Long id) {
        orgService.delete(id);
        return Result.ok();
    }
}
