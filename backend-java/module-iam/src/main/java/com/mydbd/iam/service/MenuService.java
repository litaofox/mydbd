package com.mydbd.iam.service;

import com.mydbd.iam.entity.SysMenu;
import com.mydbd.iam.mapper.SysMenuMapper;
import com.mydbd.iam.vo.MenuNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜单树服务：全量管理树 / 用户导航树
 */
@Service
@RequiredArgsConstructor
public class MenuService {

    private final SysMenuMapper menuMapper;

    /** 全量权限点树（角色授权用，含按钮与隐藏项） */
    public List<MenuNode> treeAll() {
        return build(menuMapper.selectAllEnabled());
    }

    /** 用户可见导航树（仅目录/菜单） */
    public List<MenuNode> treeForUser(Long userId) {
        return build(menuMapper.selectNavMenusByUserId(userId));
    }

    private List<MenuNode> build(List<SysMenu> menus) {
        Map<Long, MenuNode> nodes = new LinkedHashMap<>();
        for (SysMenu m : menus) {
            nodes.put(m.getId(), toNode(m));
        }
        List<MenuNode> roots = new ArrayList<>();
        for (SysMenu m : menus) {
            MenuNode node = nodes.get(m.getId());
            Long pid = m.getParentId();
            if (pid != null && pid != 0L && nodes.containsKey(pid)) {
                nodes.get(pid).getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        sortRecursively(roots);
        return roots;
    }

    private void sortRecursively(List<MenuNode> nodes) {
        nodes.sort(Comparator.comparing(MenuNode::getSortNo,
                Comparator.nullsLast(Comparator.naturalOrder())));
        nodes.forEach(n -> sortRecursively(n.getChildren()));
    }

    private MenuNode toNode(SysMenu m) {
        MenuNode node = new MenuNode();
        node.setId(m.getId());
        node.setParentId(m.getParentId());
        node.setMenuName(m.getMenuName());
        node.setMenuType(m.getMenuType());
        node.setPermCode(m.getPermCode());
        node.setPath(m.getPath());
        node.setIcon(m.getIcon());
        node.setSortNo(m.getSortNo());
        node.setVisible(m.getVisible());
        return node;
    }
}
