package com.mydbd.analysis.profile.service;

import com.mydbd.analysis.profile.constant.ProfileConstants;
import com.mydbd.analysis.profile.mapper.ProfileMasterMapper;
import com.mydbd.common.api.ErrorCode;
import com.mydbd.common.exception.BizException;
import com.mydbd.common.security.UserContext;
import com.mydbd.common.security.UserInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * F23 公共支撑：数据范围解析、对象→车牌集解析、事件码中文名。
 */
@Component
@RequiredArgsConstructor
public class ProfileSupport {

    private final ProfileMasterMapper masterMapper;

    /** null=不限制；非 null（可能空集）=按部门集合过滤 */
    public List<Long> scopeOrNull() {
        UserInfo u = UserContext.get();
        if (u == null || u.allData()) {
            return null;
        }
        Set<Long> scope = u.deptScope();
        if (scope == null) {
            return null;
        }
        return new ArrayList<>(scope);
    }

    /**
     * 解析对象车牌集（dim=global 返回 null=全网）。
     * 对象不存在 / 越权（dept 不在 scope）→ 40401；对象无车辆 → 空列表（结果为空数据，非 404）。
     */
    public List<String> resolvePlates(String dim, String id, List<Long> scope) {
        if ("global".equals(dim)) {
            return null;
        }
        if (id == null || id.isBlank()) {
            throw new BizException(ProfileConstants.ERR_DIM, "dim=" + dim + " 时 id 必填");
        }
        switch (dim) {
            case "vehicle": {
                Map<String, Object> v = masterMapper.findVehicle(id.trim());
                if (v == null) {
                    throw new BizException(ErrorCode.NOT_FOUND, "车辆不存在: " + id);
                }
                Object deptId = v.get("deptId");
                if (scope != null && !(deptId != null && scope.contains(((Number) deptId).longValue()))) {
                    throw new BizException(ErrorCode.NOT_FOUND, "车辆不存在: " + id);
                }
                return List.of(id.trim());
            }
            case "driver": {
                Long driverId = parseId(id);
                if (masterMapper.findDriver(driverId) == null) {
                    throw new BizException(ErrorCode.NOT_FOUND, "司机不存在");
                }
                return masterMapper.platesByDriver(driverId);
            }
            case "dept": {
                Long deptId = parseId(id);
                if (masterMapper.findDept(deptId) == null) {
                    throw new BizException(ErrorCode.NOT_FOUND, "企业不存在");
                }
                if (scope != null && !scope.contains(deptId)) {
                    // 越权不泄露存在性
                    throw new BizException(ErrorCode.NOT_FOUND, "企业不存在");
                }
                return masterMapper.platesByDept(deptId);
            }
            default:
                throw new BizException(ProfileConstants.ERR_DIM, "非法维度: " + dim);
        }
    }

    /** 事件码字典（每请求取一次，Service 内复用） */
    public Map<String, String> riskEventDict() {
        List<Map<String, Object>> items = masterMapper.dictItems("risk_event_code");
        Map<String, String> map = new HashMap<>();
        for (Map<String, Object> it : items) {
            map.put(String.valueOf(it.get("value")), String.valueOf(it.get("label")));
        }
        return map;
    }

    /** 事件码中文名：字典 > 代码兜底表 > 原码 */
    public String eventName(String code, Map<String, String> dict) {
        if (code == null) {
            return "未知";
        }
        String label = dict.get(code);
        if (label != null) {
            return label;
        }
        return ProfileConstants.EVENT_NAME_FALLBACK.getOrDefault(code, code);
    }

    public Long parseId(String id) {
        try {
            return Long.parseLong(id.trim());
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.NOT_FOUND, "对象不存在: " + id);
        }
    }
}
