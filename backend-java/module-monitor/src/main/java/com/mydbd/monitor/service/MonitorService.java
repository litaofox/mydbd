package com.mydbd.monitor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mydbd.common.api.PageData;
import com.mydbd.common.exception.BizException;
import com.mydbd.common.api.ErrorCode;
import com.mydbd.monitor.entity.RiskEvent;
import com.mydbd.monitor.entity.VideoAnalysis;
import com.mydbd.monitor.entity.WarnInfo;
import com.mydbd.monitor.mapper.RiskEventMapper;
import com.mydbd.monitor.mapper.VideoAnalysisMapper;
import com.mydbd.monitor.mapper.WarnInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 实时监控与风险预警服务
 */
@Service
@RequiredArgsConstructor
public class MonitorService {

    private final RiskEventMapper riskEventMapper;
    private final VideoAnalysisMapper videoAnalysisMapper;
    private final WarnInfoMapper warnInfoMapper;

    /**
     * 监控总览：在途车辆 / 今日风险 / 未处置风险 / 今日终端报警
     */
    public Map<String, Object> overview() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("activeVehicles", warnInfoMapper.countActiveVehicles());
        data.put("todayRisks", riskEventMapper.selectCount(new LambdaQueryWrapper<RiskEvent>()
                .ge(RiskEvent::getEventTime, LocalDate.now().atStartOfDay())));
        data.put("pendingRisks", riskEventMapper.selectCount(new LambdaQueryWrapper<RiskEvent>()
                .eq(RiskEvent::getHandleStatus, 0)));
        data.put("todayWarnings", warnInfoMapper.countTodayWarnings());
        return data;
    }

    /**
     * 风险事件分页查询
     */
    public PageData<RiskEvent> pageRisks(long page, long size, String eventSource,
                                         Integer riskLevel, Integer handleStatus, Long ruleId) {
        LambdaQueryWrapper<RiskEvent> wrapper = new LambdaQueryWrapper<RiskEvent>()
                .eq(StringUtils.hasText(eventSource), RiskEvent::getEventSource, eventSource)
                .eq(riskLevel != null, RiskEvent::getRiskLevel, riskLevel)
                .eq(handleStatus != null, RiskEvent::getHandleStatus, handleStatus)
                .eq(ruleId != null, RiskEvent::getRuleId, ruleId)
                .orderByDesc(RiskEvent::getEventTime);
        Page<RiskEvent> result = riskEventMapper.selectPage(new Page<>(page, size), wrapper);
        return new PageData<>(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    /**
     * 处置风险事件
     */
    @Transactional
    public void handleRisk(Long id, String remark) {
        RiskEvent event = riskEventMapper.selectById(id);
        if (event == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        event.setHandleStatus(1);
        event.setHandleRemark(remark);
        riskEventMapper.updateById(event);
    }

    /**
     * 风险类型分布（饼图）
     */
    public List<Map<String, Object>> riskTypeStats(LocalDateTime from, LocalDateTime to) {
        // 骨架阶段用全量事件在内存聚合，数据量小；后续迭代下推为 GROUP BY SQL
        List<RiskEvent> all = riskEventMapper.selectList(new LambdaQueryWrapper<RiskEvent>()
                .ge(from != null, RiskEvent::getEventTime, from)
                .le(to != null, RiskEvent::getEventTime, to));
        Map<String, Long> grouped = new LinkedHashMap<>();
        for (RiskEvent event : all) {
            grouped.merge(event.getEventCode(), 1L, Long::sum);
        }
        return grouped.entrySet().stream()
                .map(e -> Map.<String, Object>of("name", e.getKey(), "value", e.getValue()))
                .toList();
    }

    /**
     * 终端报警列表（最近 100 条）
     */
    public List<WarnInfo> listWarnings() {
        return warnInfoMapper.selectList(new LambdaQueryWrapper<WarnInfo>()
                .orderByDesc(WarnInfo::getStartWarnTime)
                .last("limit 100"));
    }

    /**
     * 视频分析任务列表
     */
    public List<VideoAnalysis> listVideoAnalyses() {
        return videoAnalysisMapper.selectList(new LambdaQueryWrapper<VideoAnalysis>()
                .orderByDesc(VideoAnalysis::getCreateDate));
    }
}
