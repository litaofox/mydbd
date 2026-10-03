package com.mydbd.iam.controller;

import com.mydbd.common.api.Result;
import com.mydbd.common.security.Logical;
import com.mydbd.common.security.RequiresPerm;
import com.mydbd.iam.service.MenuService;
import com.mydbd.iam.vo.MenuNode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 菜单/权限点接口（角色授权树、用户分配场景使用）
 */
@RestController
@RequestMapping("/api/iam/menus")
@RequiredArgsConstructor
public class IamMenuController {

    private final MenuService menuService;

    @GetMapping("/tree")
    @RequiresPerm(value = {"iam:role:view", "iam:user:edit"}, logical = Logical.OR)
    public Result<List<MenuNode>> tree() {
        return Result.ok(menuService.treeAll());
    }
}
