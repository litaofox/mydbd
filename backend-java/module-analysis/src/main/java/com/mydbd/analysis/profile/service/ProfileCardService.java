package com.mydbd.analysis.profile.service;

import com.mydbd.analysis.profile.dto.CompositionRow;
import com.mydbd.analysis.profile.dto.EventTotalRow;
import com.mydbd.analysis.profile.dto.ScoreCurveRow;
import com.mydbd.analysis.profile.mapper.ProfileEventMapper;
import com.mydbd.analysis.profile.mapper.ProfileMasterMapper;
import com.mydbd.analysis.profile.mapper.ProfileOrderMapper;
import com.mydbd.analysis.profile.mapper.ProfileScoreMapper;
import com.mydbd.analysis.profile.vo.ObjectCardVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * F23 对象画像卡（MOD-ANA-002 §3.2/§4.2）：概览 + 评分曲线（降级） + 事件构成 + 漏斗 + 工单 Top5。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileCardService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MM-dd");

    private final ProfileEventMapper eventMapper;
    private final ProfileOrderMapper orderMapper;
    private final ProfileScoreMapper scoreMapper;
    private final ProfileMasterMapper masterMapper;
    private final ProfileSupport support;

    public ObjectCardVO card(String type, String id, LocalDateTime start, LocalDateTime end) {
        List<Long> scope = support.scopeOrNull();
        // 复用维度解析：type 与 dim 的车牌集口径一致（dept→dim=dept 等）
        List<String> plates = support.resolvePlates(type, id, scope);
        if (plates == null || plates.isEmpty()) {
            // 对象存在但无绑定车辆：空画像（对象名仍解析）
            plates = List.of();
        }

        ObjectCardVO vo = new ObjectCardVO();
        vo.setType(type);
        vo.setId(id);
        fillNames(vo, type, id, plates);

        Map<String, String> dict = support.riskEventDict();

        // 概览 + 构成 + 漏斗 + Top 工单
        if (!plates.isEmpty()) {
            EventTotalRow et = eventMapper.eventTotal(start, end, plates);
            vo.setEventTotal(et == null ? 0 : et.getCnt());
            vo.setHighCnt(et == null ? 0 : et.getHigh());

            List<CompositionRow> comp = eventMapper.composition(start, end, plates, scope);
            List<ObjectCardVO.CompositionItem> items = new ArrayList<>();
            for (CompositionRow r : comp) {
                ObjectCardVO.CompositionItem ci = new ObjectCardVO.CompositionItem();
                ci.setCode(r.getEventCode());
                ci.setName(support.eventName(r.getEventCode(), dict));
                ci.setCnt(r.getCnt());
                ci.setHigh(r.getHigh());
                ci.setMid(r.getMid());
                ci.setLow(r.getLow());
                items.add(ci);
            }
            vo.setComposition(items);

            ObjectCardVO.Funnel f = new ObjectCardVO.Funnel();
            f.setEventTotal(vo.getEventTotal());
            long orderTotal = orderMapper.orderTotal(start, end, plates);
            long interventionTotal = orderMapper.interventionTotal(start, end, plates);
            long closedTotal = orderMapper.closedTotal(start, end, plates);
            f.setOrderTotal(orderTotal);
            f.setInterventionTotal(interventionTotal);
            f.setClosedTotal(closedTotal);
            f.setInterventionRate(rate(interventionTotal, orderTotal));
            f.setCloseRate(rate(closedTotal, orderTotal));
            vo.setFunnel(f);

            vo.setTopOrders(orderMapper.topOrders(start, end, plates));
        } else {
            vo.setComposition(List.of());
            ObjectCardVO.Funnel f = new ObjectCardVO.Funnel();
            f.setInterventionRate(null);
            f.setCloseRate(null);
            vo.setFunnel(f);
            vo.setTopOrders(List.of());
        }

        // 评分曲线（独立降级：driver_score 缺失 → scoreReady=false，其余照常）
        fillScore(vo, type, id, start, end);
        return vo;
    }

    private void fillNames(ObjectCardVO vo, String type, String id, List<String> plates) {
        switch (type) {
            case "vehicle": {
                vo.setName(id);
                Map<String, Object> d = masterMapper.driverOfVehicle(id);
                vo.setSub(d == null ? "未绑定司机" : String.valueOf(d.get("driverName")));
                break;
            }
            case "driver": {
                Long driverId = support.parseId(id);
                Map<String, Object> d = masterMapper.findDriver(driverId);
                vo.setName(d == null ? id : String.valueOf(d.get("name")));
                vo.setSub(plates.isEmpty() ? "无绑定车辆" : String.join("、", plates));
                break;
            }
            case "dept": {
                Long deptId = support.parseId(id);
                Map<String, Object> d = masterMapper.findDept(deptId);
                vo.setName(d == null ? id : String.valueOf(d.get("name")));
                vo.setSub(plates.size() + " 辆车");
                break;
            }
            default:
                vo.setName(id);
        }
    }

    private void fillScore(ObjectCardVO vo, String type, String id,
                           LocalDateTime start, LocalDateTime end) {
        LocalDate sd = start.toLocalDate();
        LocalDate ed = end.toLocalDate();
        if (end.toLocalTime().equals(java.time.LocalTime.MIDNIGHT)) {
            ed = ed.minusDays(1);
            if (ed.isBefore(sd)) {
                ed = sd;
            }
        }
        try {
            List<ScoreCurveRow> curve;
            if ("dept".equals(type)) {
                curve = scoreMapper.curveByDept(support.parseId(id), sd, ed);
            } else {
                List<Long> driverIds = driverIdsOf(type, id);
                if (driverIds.isEmpty()) {
                    vo.setScoreReady(true);
                    return;
                }
                curve = scoreMapper.curveByDrivers(driverIds, sd, ed);
            }
            vo.setScoreReady(true);
            if (!curve.isEmpty()) {
                ObjectCardVO.ScoreCurve sc = new ObjectCardVO.ScoreCurve();
                List<String> dates = new ArrayList<>();
                List<BigDecimal> scores = new ArrayList<>();
                List<String> levels = new ArrayList<>();
                for (ScoreCurveRow r : curve) {
                    dates.add(r.getScoreDate().format(DATE_FMT));
                    scores.add(r.getScore());
                    levels.add(r.getLevel());
                }
                sc.setDates(dates);
                sc.setScores(scores);
                sc.setLevels(levels);
                vo.setScoreCurve(sc);
                ScoreCurveRow last = curve.get(curve.size() - 1);
                vo.setLatestScore(new ObjectCardVO.LatestScore(last.getScore(), last.getLevel()));
            }
        } catch (Exception e) {
            log.warn("F23 画像评分降级（driver_score 不可用）: {}", e.getMessage());
            vo.setScoreReady(false);
            vo.setScoreCurve(null);
            vo.setLatestScore(null);
        }
    }

    private List<Long> driverIdsOf(String type, String id) {
        List<Long> ids = new ArrayList<>();
        if ("driver".equals(type)) {
            ids.add(support.parseId(id));
        } else if ("vehicle".equals(type)) {
            Map<String, Object> d = masterMapper.driverOfVehicle(id);
            if (d != null && d.get("driverId") != null) {
                ids.add(((Number) d.get("driverId")).longValue());
            }
        }
        return ids;
    }

    private BigDecimal rate(long part, long total) {
        if (total <= 0) {
            return null;
        }
        return BigDecimal.valueOf(part * 100.0 / total).setScale(1, RoundingMode.HALF_UP);
    }
}
