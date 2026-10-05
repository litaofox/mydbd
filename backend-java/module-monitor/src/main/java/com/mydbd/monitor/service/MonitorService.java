package com.mydbd.monitor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mydbd.common.api.PageData;
import com.mydbd.common.exception.BizException;
import com.mydbd.common.api.ErrorCode;
import com.mydbd.common.security.UserContext;
import com.mydbd.common.security.UserInfo;
import com.mydbd.monitor.entity.RiskEvent;
import com.mydbd.monitor.entity.VideoAnalysis;
import com.mydbd.monitor.entity.WarnInfo;
import com.mydbd.monitor.mapper.RiskEventMapper;
import com.mydbd.monitor.mapper.VideoAnalysisMapper;
import com.mydbd.monitor.mapper.WarnInfoMapper;
import com.mydbd.monitor.dashboard.mapper.DashboardMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 实时监控与风险预警服务
 */
@Service
@RequiredArgsConstructor
public class MonitorService {

    private final RiskEventMapper riskEventMapper;
    private final VideoAnalysisMapper videoAnalysisMapper;
    private final WarnInfoMapper warnInfoMapper;
    private final DashboardMapper dashboardMapper;

    /**
     * 监控总览：在途车辆 / 今日风险 / 未处置风险 / 今日终端报警。
     * 四项计数全部按当前用户部门数据范围口径（受限用户空范围时全为 0）。
     */
    public Map<String, Object> overview() {
        List<String> plates = currentPlates();
        Map<String, Object> data = new LinkedHashMap<>();
        if (plates != null && plates.isEmpty()) {
            data.put("activeVehicles", 0L);
            data.put("todayRisks", 0L);
            data.put("pendingRisks", 0L);
            data.put("todayWarnings", 0L);
            return data;
        }
        data.put("activeVehicles", warnInfoMapper.countActiveVehicles(plates));
        data.put("todayRisks", riskEventMapper.selectCount(new LambdaQueryWrapper<RiskEvent>()
                .ge(RiskEvent::getEventTime, LocalDate.now().atStartOfDay())
                .in(plates != null, RiskEvent::getPlateNo, plates == null ? List.of() : plates)));
        data.put("pendingRisks", riskEventMapper.selectCount(new LambdaQueryWrapper<RiskEvent>()
                .eq(RiskEvent::getHandleStatus, 0)
                .in(plates != null, RiskEvent::getPlateNo, plates == null ? List.of() : plates)));
        data.put("todayWarnings", warnInfoMapper.countTodayWarnings(plates));
        return data;
    }

    /**
     * 风险事件分页查询
     * @param plateNo  车牌（模糊匹配，大屏/明细页钻取）
     * @param cityCode 车辆注册地城市代码（区域分布柱钻取；经车辆表换算车牌集合过滤）
     */
    public PageData<RiskEvent> pageRisks(long page, long size, String eventSource,
                                         Integer riskLevel, Integer handleStatus, Long ruleId,
                                         String plateNo, String cityCode) {
        List<String> plates = currentPlates();
        if (plates != null && plates.isEmpty()) {
            return new PageData<>(0, page, size, List.of());
        }
        // 区域钻取：先按注册地换算车牌集合，空集合直接返回空页
        List<String> cityPlates = null;
        if (StringUtils.hasText(cityCode)) {
            cityPlates = dashboardMapper.selectVehicleNosByCityCode(cityCode);
            if (cityPlates.isEmpty()) {
                return new PageData<>(0, page, size, List.of());
            }
        }
        LambdaQueryWrapper<RiskEvent> wrapper = new LambdaQueryWrapper<RiskEvent>()
                .eq(StringUtils.hasText(eventSource), RiskEvent::getEventSource, eventSource)
                .eq(riskLevel != null, RiskEvent::getRiskLevel, riskLevel)
                .eq(handleStatus != null, RiskEvent::getHandleStatus, handleStatus)
                .eq(ruleId != null, RiskEvent::getRuleId, ruleId)
                .like(StringUtils.hasText(plateNo), RiskEvent::getPlateNo, plateNo)
                .in(cityPlates != null, RiskEvent::getPlateNo, cityPlates)
                .in(plates != null, RiskEvent::getPlateNo, plates == null ? List.of() : plates)
                .orderByDesc(RiskEvent::getEventTime);
        Page<RiskEvent> result = riskEventMapper.selectPage(new Page<>(page, size), wrapper);
        return new PageData<>(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    /**
     * 处置风险事件
     * 数据范围：不存在与越权同一口径 40401，避免借处置接口探测存在性（对齐 AlarmService）。
     */
    @Transactional
    public void handleRisk(Long id, String remark) {
        RiskEvent event = riskEventMapper.selectById(id);
        if (event == null || !canSee(event.getPlateNo())) {
            throw new BizException(ErrorCode.NOT_FOUND, "风险事件不存在");
        }
        event.setHandleStatus(1);
        event.setHandleRemark(remark);
        riskEventMapper.updateById(event);
    }

    /**
     * 风险类型分布（饼图）：按当前用户范围过滤后聚合。
     */
    public List<Map<String, Object>> riskTypeStats(LocalDateTime from, LocalDateTime to) {
        List<String> plates = currentPlates();
        if (plates != null && plates.isEmpty()) {
            return List.of();
        }
        List<RiskEvent> all = riskEventMapper.selectList(new LambdaQueryWrapper<RiskEvent>()
                .ge(from != null, RiskEvent::getEventTime, from)
                .le(to != null, RiskEvent::getEventTime, to)
                .in(plates != null, RiskEvent::getPlateNo, plates == null ? List.of() : plates));
        Map<String, Long> grouped = new LinkedHashMap<>();
        for (RiskEvent event : all) {
            grouped.merge(event.getEventCode(), 1L, Long::sum);
        }
        return grouped.entrySet().stream()
                .map(e -> Map.<String, Object>of("name", e.getKey(), "value", e.getValue()))
                .toList();
    }

    /**
     * 终端报警列表（最近 100 条）：按当前用户范围过滤。
     */
    public List<WarnInfo> listWarnings() {
        List<String> plates = currentPlates();
        if (plates != null && plates.isEmpty()) {
            return List.of();
        }
        return warnInfoMapper.selectList(new LambdaQueryWrapper<WarnInfo>()
                .in(plates != null, WarnInfo::getPlateNo, plates == null ? List.of() : plates)
                .orderByDesc(WarnInfo::getStartWarnTime)
                .last("limit 100"));
    }

    /**
     * 视频分析任务列表：按当前用户范围过滤。
     */
    public List<VideoAnalysis> listVideoAnalyses() {
        List<String> plates = currentPlates();
        if (plates != null && plates.isEmpty()) {
            return List.of();
        }
        return videoAnalysisMapper.selectList(new LambdaQueryWrapper<VideoAnalysis>()
                .in(plates != null, VideoAnalysis::getPlateNo, plates == null ? List.of() : plates)
                .orderByDesc(VideoAnalysis::getCreateDate));
    }

    // ===== 内部方法 =====

    /** 当前用户是否可见指定车牌（null 车牌对受限用户不可见） */
    private boolean canSee(String plateNo) {
        List<String> plates = currentPlates();
        if (plates == null) {
            return true;
        }
        return !plates.isEmpty() && plateNo != null && plates.contains(plateNo);
    }

    /**
     * 当前用户可见车牌：null=不限制；空列表=不可见任何数据；非空=可见车牌。
     * 部门→车牌在监控域经 DashboardMapper 跨 schema 直查（同既有先例）。
     */
    private List<String> currentPlates() {
        UserInfo current = UserContext.get();
        if (current == null || current.allData()) {
            return null;
        }
        Set<Long> scope = current.deptScope();
        if (scope == null || scope.isEmpty()) {
            return List.of();
        }
        return dashboardMapper.selectVehicleNosByDeptScope(scope);
    }
}
