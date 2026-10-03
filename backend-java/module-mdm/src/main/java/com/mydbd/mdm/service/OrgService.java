package com.mydbd.mdm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mydbd.common.api.ErrorCode;
import com.mydbd.common.exception.BizException;
import com.mydbd.common.security.UserContext;
import com.mydbd.common.security.UserInfo;
import com.mydbd.mdm.dto.DeptSaveRequest;
import com.mydbd.mdm.entity.Dept;
import com.mydbd.mdm.entity.Vehicle;
import com.mydbd.mdm.mapper.DeptMapper;
import com.mydbd.mdm.mapper.MdmVehicleMapper;
import com.mydbd.mdm.vo.OptionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 组织架构服务：组织树、CRUD 与占用校验
 */
@Service
@RequiredArgsConstructor
public class OrgService {

    private final DeptMapper deptMapper;
    private final MdmVehicleMapper vehicleMapper;

    /** 组织树（带有效车辆数；受 IAM 数据范围裁剪，自定义范围补全祖先链以维持树形） */
    public List<Dept> tree() {
        List<Dept> all = deptMapper.selectList(new LambdaQueryWrapper<Dept>()
                .eq(Dept::getValidMark, 1)
                .orderByAsc(Dept::getSortNo)
                .orderByAsc(Dept::getId));

        Set<Long> scope = currentScope();
        Set<Long> visibleIds;
        if (scope != null) {
            if (scope.isEmpty()) {
                return List.of();
            }
            visibleIds = appendAncestors(scope, all);
            Set<Long> finalVisible = visibleIds;
            all = all.stream().filter(d -> finalVisible.contains(d.getId())).toList();
        } else {
            visibleIds = null;
        }
        Set<Long> rootCutIds = visibleIds;

        Map<Long, Long> countMap = new HashMap<>();
        for (Map<String, Object> row : deptMapper.countVehiclesGroupByDept()) {
            Object id = row.get("deptid");
            Object cnt = row.get("cnt");
            if (id != null) {
                countMap.put(((Number) id).longValue(), cnt == null ? 0L : ((Number) cnt).longValue());
            }
        }
        all.forEach(d -> d.setVehicleCount(countMap.getOrDefault(d.getId(), 0L)));

        Map<Long, List<Dept>> byParent = all.stream()
                .collect(Collectors.groupingBy(d -> normalizeParent(d.getParentId())));
        all.forEach(d -> d.setChildren(byParent.getOrDefault(d.getId(), List.of())));
        // 根 = parent=0，或父节点不在可见集合内（裁剪后的祖先补全根）
        return all.stream()
                .filter(d -> normalizeParent(d.getParentId()) == 0L
                        || rootCutIds != null && !rootCutIds.contains(d.getParentId()))
                .collect(Collectors.toList());
    }

    /** 组织下拉（受 IAM 数据范围约束） */
    public List<OptionVO> options() {
        LambdaQueryWrapper<Dept> wrapper = new LambdaQueryWrapper<Dept>()
                .eq(Dept::getValidMark, 1);
        Set<Long> scope = currentScope();
        if (scope != null) {
            if (scope.isEmpty()) {
                return List.of();
            }
            wrapper.in(Dept::getId, scope);
        }
        wrapper.orderByAsc(Dept::getSortNo).orderByAsc(Dept::getId);
        return deptMapper.selectList(wrapper).stream()
                .map(d -> new OptionVO(d.getId(), d.getDeptName()))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(DeptSaveRequest req) {
        long parentId = normalizeParent(req.parentId());
        if (parentId != 0L && getValidDept(parentId) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "上级组织不存在");
        }
        checkNameConflict(parentId, req.deptName(), null);
        if (StringUtils.hasText(req.deptCode())) {
            checkCodeConflict(req.deptCode(), null);
        }
        Dept dept = new Dept();
        applyFields(dept, req, parentId);
        dept.setValidMark(1);
        deptMapper.insert(dept);
        return dept.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, DeptSaveRequest req) {
        Dept dept = requireDept(id);
        long newParent = normalizeParent(req.parentId());
        if (newParent != 0L) {
            if (newParent == id) {
                throw new BizException(ErrorCode.CONFLICT, "上级组织不能选择自己");
            }
            if (getValidDept(newParent) == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "上级组织不存在");
            }
            if (collectDescendantIds(id).contains(newParent)) {
                throw new BizException(ErrorCode.CONFLICT, "上级组织不能选择自己的下级");
            }
        }
        checkNameConflict(newParent, req.deptName(), id);
        if (StringUtils.hasText(req.deptCode())) {
            checkCodeConflict(req.deptCode(), id);
        }
        applyFields(dept, req, newParent);
        deptMapper.updateById(dept);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Dept dept = requireDept(id);
        long childCount = deptMapper.selectCount(new LambdaQueryWrapper<Dept>()
                .eq(Dept::getParentId, id)
                .eq(Dept::getValidMark, 1));
        if (childCount > 0) {
            throw new BizException(ErrorCode.CONFLICT, "该组织下存在 " + childCount + " 个下级组织，不能删除");
        }
        long vehicleCount = vehicleMapper.selectCount(new LambdaQueryWrapper<Vehicle>()
                .eq(Vehicle::getDeptId, id)
                .eq(Vehicle::getValidMark, 1));
        if (vehicleCount > 0) {
            throw new BizException(ErrorCode.CONFLICT, "该组织下还有 " + vehicleCount + " 台有效车辆，不能删除");
        }
        dept.setValidMark(0);
        deptMapper.updateById(dept);
    }

    // ===== 内部方法 =====

    private Dept requireDept(Long id) {
        Dept dept = deptMapper.selectById(id);
        if (dept == null || dept.getValidMark() == null || dept.getValidMark() != 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "组织不存在");
        }
        return dept;
    }

    private Dept getValidDept(Long id) {
        Dept dept = deptMapper.selectById(id);
        return (dept != null && dept.getValidMark() != null && dept.getValidMark() == 1) ? dept : null;
    }

    private void checkNameConflict(long parentId, String name, Long excludeId) {
        Long count = deptMapper.selectCount(new LambdaQueryWrapper<Dept>()
                .eq(Dept::getParentId, parentId)
                .eq(Dept::getDeptName, name)
                .eq(Dept::getValidMark, 1)
                .ne(excludeId != null, Dept::getId, excludeId));
        if (count > 0) {
            throw new BizException(ErrorCode.CONFLICT, "同一上级下已存在同名组织");
        }
    }

    private void checkCodeConflict(String code, Long excludeId) {
        Long count = deptMapper.selectCount(new LambdaQueryWrapper<Dept>()
                .eq(Dept::getDeptCode, code)
                .eq(Dept::getValidMark, 1)
                .ne(excludeId != null, Dept::getId, excludeId));
        if (count > 0) {
            throw new BizException(ErrorCode.CONFLICT, "组织编码已存在");
        }
    }

    /** 收集某组织的全部后代 ID */
    private List<Long> collectDescendantIds(Long rootId) {
        List<Long> result = new ArrayList<>();
        List<Long> frontier = List.of(rootId);
        while (!frontier.isEmpty()) {
            List<Dept> children = deptMapper.selectList(new LambdaQueryWrapper<Dept>()
                    .in(Dept::getParentId, frontier)
                    .eq(Dept::getValidMark, 1));
            if (children.isEmpty()) {
                break;
            }
            frontier = children.stream().map(Dept::getId).toList();
            result.addAll(frontier);
        }
        return result;
    }

    private void applyFields(Dept dept, DeptSaveRequest req, long parentId) {
        dept.setParentId(parentId);
        dept.setDeptName(req.deptName());
        dept.setDeptCode(StringUtils.hasText(req.deptCode()) ? req.deptCode() : null);
        dept.setDeptType(req.deptType());
        dept.setContactPerson(req.contactPerson());
        dept.setContactPhone(req.contactPhone());
        dept.setProvinceCode(req.provinceCode());
        dept.setCityCode(req.cityCode());
        dept.setCountyCode(req.countyCode());
        dept.setAddress(req.address());
        dept.setSortNo(req.sortNo() == null ? 0 : req.sortNo());
    }

    private long normalizeParent(Long parentId) {
        return parentId == null ? 0L : parentId;
    }

    /** 当前用户数据范围：null=全部，空集合=不可见任何部门数据 */
    private Set<Long> currentScope() {
        UserInfo current = UserContext.get();
        return current == null ? null : current.deptScope();
    }

    /** 为自定义范围的部门补全祖先链，使裁剪后仍可组成完整树（祖先仅作展示容器） */
    private Set<Long> appendAncestors(Set<Long> scope, List<Dept> all) {
        Map<Long, Long> parentMap = new HashMap<>();
        all.forEach(d -> parentMap.put(d.getId(), normalizeParent(d.getParentId())));
        Set<Long> result = new HashSet<>(scope);
        for (Long deptId : scope) {
            Long cursor = deptId;
            while (cursor != null && cursor != 0L && parentMap.containsKey(cursor)) {
                cursor = parentMap.get(cursor);
                if (cursor != null && cursor != 0L) {
                    result.add(cursor);
                }
            }
        }
        return result;
    }
}
