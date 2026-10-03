package com.mydbd.iam.service;

import com.mydbd.iam.entity.SysRole;
import com.mydbd.iam.entity.SysUser;
import com.mydbd.iam.mapper.DataScopeDeptMapper;
import com.mydbd.iam.mapper.SysRoleDeptMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 数据范围解析：多角色取并集；任一角色"全部"即 null（不限制）。
 */
@Service
@RequiredArgsConstructor
public class DataScopeService {

    public static final int SCOPE_ALL = 1;
    public static final int SCOPE_ENTERPRISE = 2;
    public static final int SCOPE_DEPT_TREE = 3;
    public static final int SCOPE_DEPT_ONLY = 4;
    public static final int SCOPE_CUSTOM = 5;

    private final DataScopeDeptMapper deptMapper;
    private final SysRoleDeptMapper roleDeptMapper;

    /**
     * @return null=全部数据；空集合=不可见任何部门数据
     */
    public Set<Long> compute(SysUser user, List<SysRole> roles) {
        Set<Long> scope = null;
        Map<Long, DeptTree> trees = loadTrees();
        for (SysRole role : roles) {
            Integer ds = role.getDataScope();
            if (ds == null) {
                continue;
            }
            switch (ds) {
                case SCOPE_ALL -> {
                    return null;
                }
                case SCOPE_ENTERPRISE -> {
                    Long root = user.getDeptId() == null ? null
                            : resolveEnterpriseId(user.getDeptId(), trees);
                    if (root != null) {
                        scope = lazy(scope);
                        scope.addAll(subtree(root, trees));
                    }
                }
                case SCOPE_DEPT_TREE -> {
                    if (user.getDeptId() != null && trees.containsKey(user.getDeptId())) {
                        scope = lazy(scope);
                        scope.addAll(subtree(user.getDeptId(), trees));
                    }
                }
                case SCOPE_DEPT_ONLY -> {
                    if (user.getDeptId() != null && trees.containsKey(user.getDeptId())) {
                        scope = lazy(scope);
                        scope.add(user.getDeptId());
                    }
                }
                case SCOPE_CUSTOM -> {
                    List<Long> custom = roleDeptMapper.selectDeptIds(role.getId());
                    if (!custom.isEmpty()) {
                        scope = lazy(scope);
                        scope.addAll(custom);
                    }
                }
                default -> {
                }
            }
        }
        return scope == null ? Set.of() : scope;
    }

    private Set<Long> lazy(Set<Long> scope) {
        return scope == null ? new HashSet<>() : scope;
    }

    /** 沿父链找到 dept_type=1 的企业节点（自身是企业则返回自身） */
    private Long resolveEnterpriseId(Long deptId, Map<Long, DeptTree> trees) {
        Long current = deptId;
        int guard = 0;
        while (current != null && trees.containsKey(current) && guard++ < 100) {
            DeptTree node = trees.get(current);
            if (node.deptType() != null && node.deptType() == 1) {
                return current;
            }
            current = node.parentId() == null || node.parentId() == 0L ? null : node.parentId();
        }
        // 链路上没有企业节点时退化为当前部门
        return trees.containsKey(deptId) ? deptId : null;
    }

    private Set<Long> subtree(Long rootId, Map<Long, DeptTree> trees) {
        Set<Long> result = new HashSet<>();
        if (!trees.containsKey(rootId)) {
            return result;
        }
        result.add(rootId);
        List<Long> frontier = List.of(rootId);
        while (!frontier.isEmpty()) {
            List<Long> next = new ArrayList<>();
            for (Long id : frontier) {
                DeptTree node = trees.get(id);
                if (node != null && node.children() != null) {
                    next.addAll(node.children());
                }
            }
            result.addAll(next);
            frontier = next;
        }
        return result;
    }

    private Map<Long, DeptTree> loadTrees() {
        List<DataScopeDeptMapper.DeptNode> nodes = deptMapper.selectAllNodes();
        Map<Long, List<Long>> childrenMap = new HashMap<>();
        Map<Long, Integer> typeMap = new HashMap<>();
        Map<Long, Long> parentMap = new HashMap<>();
        for (DataScopeDeptMapper.DeptNode n : nodes) {
            typeMap.put(n.id(), n.deptType());
            parentMap.put(n.id(), n.parentId());
            if (n.parentId() != null && n.parentId() != 0L) {
                childrenMap.computeIfAbsent(n.parentId(), k -> new ArrayList<>()).add(n.id());
            }
        }
        Map<Long, DeptTree> result = new HashMap<>();
        for (DataScopeDeptMapper.DeptNode n : nodes) {
            result.put(n.id(), new DeptTree(n.parentId(), n.deptType(),
                    childrenMap.getOrDefault(n.id(), List.of())));
        }
        return result;
    }

    private record DeptTree(Long parentId, Integer deptType, List<Long> children) {
    }
}
