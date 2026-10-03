package com.mydbd.risk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mydbd.common.api.ErrorCode;
import com.mydbd.common.api.PageData;
import com.mydbd.common.exception.BizException;
import com.mydbd.risk.dto.RiskRequests.FenceSaveRequest;
import com.mydbd.risk.entity.RiskGeoFence;
import com.mydbd.risk.mapper.RiskGeoFenceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * F18 电子围栏配置服务（几何列用 ST_GeomFromGeoJSON / ST_AsGeoJSON 维护）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeoFenceService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RiskGeoFenceMapper fenceMapper;

    public PageData<RiskGeoFence> page(long page, long size, String fenceName,
                                       String fenceType, Integer status) {
        LambdaQueryWrapper<RiskGeoFence> qw = new LambdaQueryWrapper<RiskGeoFence>()
                .eq(RiskGeoFence::getValidMark, 1)
                .like(StringUtils.hasText(fenceName), RiskGeoFence::getFenceName, fenceName)
                .eq(StringUtils.hasText(fenceType), RiskGeoFence::getFenceType, fenceType)
                .eq(status != null, RiskGeoFence::getStatus, status)
                .orderByDesc(RiskGeoFence::getId);
        Page<RiskGeoFence> result = fenceMapper.selectPage(new Page<>(page, clampSize(size)), qw);
        return new PageData<>(result.getTotal(), result.getCurrent(), result.getSize(),
                result.getRecords());
    }

    public List<RiskGeoFence> listAll() {
        return fenceMapper.selectList(new LambdaQueryWrapper<RiskGeoFence>()
                .eq(RiskGeoFence::getValidMark, 1)
                .orderByDesc(RiskGeoFence::getId));
    }

    public RiskGeoFence detail(Long id) {
        RiskGeoFence fence = loadOrThrow(id);
        if ("POLYGON".equals(fence.getFenceType())) {
            fence.setPoints(readPolygonPoints(id));
        }
        return fence;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(FenceSaveRequest req) {
        String type = normalizeType(req.fenceType());
        validateNameUnique(req.fenceName().trim(), null);
        RiskGeoFence fence = new RiskGeoFence();
        applyBase(fence, req, type);
        validateShape(fence);
        fence.setValidMark(1);
        if (fence.getStatus() == null) {
            fence.setStatus(1);
        }
        fenceMapper.insert(fence);
        writeGeom(fence);
        return fence.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, FenceSaveRequest req) {
        RiskGeoFence fence = loadOrThrow(id);
        String type = normalizeType(req.fenceType());
        validateNameUnique(req.fenceName().trim(), id);
        applyBase(fence, req, type);
        validateShape(fence);
        fenceMapper.updateById(fence);
        writeGeom(fence);
    }

    public void updateStatus(Long id, Integer status) {
        RiskGeoFence fence = loadOrThrow(id);
        fence.setStatus(status);
        fenceMapper.updateById(fence);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        RiskGeoFence fence = loadOrThrow(id);
        fence.setValidMark(0);
        fenceMapper.updateById(fence);
        fenceMapper.deleteFenceState(id);
    }

    // ---- 内部 ----

    private void applyBase(RiskGeoFence fence, FenceSaveRequest req, String type) {
        fence.setFenceName(req.fenceName().trim());
        fence.setFenceType(type);
        if ("CIRCLE".equals(type)) {
            fence.setCenterLng(req.centerLng());
            fence.setCenterLat(req.centerLat());
            fence.setRadiusM(req.radiusM());
            fence.setPoints(null);
        } else {
            fence.setCenterLng(null);
            fence.setCenterLat(null);
            fence.setRadiusM(null);
            // 必须先对原始顶点数做校验，闭合补点不能“凑数”
            if (req.points() == null || req.points().size() < 3) {
                throw new BizException(ErrorCode.BAD_REQUEST, "多边形围栏至少需要 3 个顶点");
            }
            fence.setPoints(closedRing(req.points()));
        }
        fence.setTriggerDir(req.triggerDir());
        fence.setRiskLevel(req.riskLevel());
        fence.setCooldownSec(req.cooldownSec());
        if (req.status() != null) {
            fence.setStatus(req.status());
        }
        fence.setRemark(req.remark());
    }

    private void validateShape(RiskGeoFence fence) {
        if ("CIRCLE".equals(fence.getFenceType())) {
            if (fence.getCenterLng() == null || fence.getCenterLat() == null
                    || fence.getRadiusM() == null) {
                throw new BizException(ErrorCode.BAD_REQUEST, "圆形围栏必须有中心点与半径");
            }
            double lng = fence.getCenterLng().doubleValue();
            double lat = fence.getCenterLat().doubleValue();
            if (lng < 73 || lng > 135 || lat < 3 || lat > 54) {
                throw new BizException(ErrorCode.BAD_REQUEST, "围栏中心点需在中国经纬度范围内");
            }
            if (fence.getRadiusM() < 50 || fence.getRadiusM() > 100000) {
                throw new BizException(ErrorCode.BAD_REQUEST, "围栏半径需在 50~100000 米之间");
            }
        } else {
            List<List<BigDecimal>> pts = fence.getPoints();
            // 闭合环至少 4 个点（3 顶点 + 闭合点）
            if (pts == null || pts.size() < 4) {
                throw new BizException(ErrorCode.BAD_REQUEST, "多边形围栏至少需要 3 个顶点");
            }
        }
    }

    /** 顶点不足闭合时补尾点=首点；GeoJSON Polygon 要求闭合环 */
    private List<List<BigDecimal>> closedRing(List<List<BigDecimal>> raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        List<List<BigDecimal>> ring = new ArrayList<>(raw);
        List<BigDecimal> first = ring.get(0);
        List<BigDecimal> last = ring.get(ring.size() - 1);
        if (first.size() < 2 || last.size() < 2
                || first.get(0).compareTo(last.get(0)) != 0
                || first.get(1).compareTo(last.get(1)) != 0) {
            ring.add(new ArrayList<>(first));
        }
        return ring;
    }

    private void writeGeom(RiskGeoFence fence) {
        if ("POLYGON".equals(fence.getFenceType())) {
            try {
                String geojson = MAPPER.writeValueAsString(Map.of(
                        "type", "Polygon",
                        "coordinates", List.of(fence.getPoints())));
                fenceMapper.updatePolygonGeom(fence.getId(), geojson);
            } catch (Exception e) {
                log.error("围栏多边形 GeoJSON 构造失败", e);
                throw new BizException(ErrorCode.BAD_REQUEST, "围栏图形数据非法");
            }
        } else {
            fenceMapper.clearPolygonGeom(fence.getId());
        }
    }

    @SuppressWarnings("unchecked")
    private List<List<BigDecimal>> readPolygonPoints(Long id) {
        String geoJson = fenceMapper.selectPolygonGeoJson(id);
        if (!StringUtils.hasText(geoJson)) {
            return null;
        }
        try {
            Map<String, Object> root = MAPPER.readValue(geoJson, Map.class);
            List<List<List<Number>>> rings = (List<List<List<Number>>>) root.get("coordinates");
            if (rings == null || rings.isEmpty()) {
                return null;
            }
            List<List<BigDecimal>> result = new ArrayList<>();
            for (List<Number> pt : rings.get(0)) {
                result.add(List.of(
                        new BigDecimal(pt.get(0).toString()),
                        new BigDecimal(pt.get(1).toString())));
            }
            return result;
        } catch (Exception e) {
            log.error("围栏多边形 GeoJSON 解析失败 id={}", id, e);
            return null;
        }
    }

    private void validateNameUnique(String name, Long excludeId) {
        Long cnt = fenceMapper.selectCount(new LambdaQueryWrapper<RiskGeoFence>()
                .eq(RiskGeoFence::getValidMark, 1)
                .eq(RiskGeoFence::getFenceName, name)
                .ne(excludeId != null, RiskGeoFence::getId, excludeId));
        if (cnt != null && cnt > 0) {
            throw new BizException(ErrorCode.CONFLICT, "围栏名称已存在：" + name);
        }
    }

    private String normalizeType(String type) {
        String t = type == null ? "" : type.trim().toUpperCase();
        if (!"CIRCLE".equals(t) && !"POLYGON".equals(t)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "围栏形状非法");
        }
        return t;
    }

    private RiskGeoFence loadOrThrow(Long id) {
        RiskGeoFence fence = fenceMapper.selectById(id);
        if (fence == null || fence.getValidMark() == null || fence.getValidMark() != 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "围栏不存在");
        }
        return fence;
    }

    private long clampSize(long size) {
        if (size < 1) return 10;
        if (size > 200) return 200;
        return size;
    }
}
