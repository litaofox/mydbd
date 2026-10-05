package com.mydbd.monitor.dashboard.service;

import com.mydbd.common.security.UserContext;
import com.mydbd.common.security.UserInfo;
import com.mydbd.monitor.dashboard.mapper.DashboardMapper;
import com.mydbd.monitor.dashboard.vo.DashboardSummaryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * F15 监控总览大屏聚合服务（MOD-MON-002 §4.1.1）。
 * 只读聚合、不加事务、不加缓存；在线窗口每次实时读 sys_config（键缺失/非法回落 5）。
 *
 * 数据权限（SYS-DBD-001 §8）：每个聚合均按当前用户 deptScope 裁剪——
 * 全部数据（null 范围）不限制；空范围 fail-closed 全零；非空按部门/车牌过滤。
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final String CONFIG_KEY_ONLINE_WINDOW = "monitor.online.window.minutes";
    private static final int DEFAULT_ONLINE_WINDOW_MINUTES = 5;

    private final DashboardMapper dashboardMapper;

    public DashboardSummaryVO summary() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        int windowMinutes = onlineWindowMinutes();

        // null=不限制；空集合=fail-closed；非空=可见部门
        Collection<Long> deptIds = currentDeptIds();
        if (deptIds != null && deptIds.isEmpty()) {
            return closedSummary(now, windowMinutes);
        }
        Collection<String> plates = deptIds == null ? null
                : dashboardMapper.selectVehicleNosByDeptScope(deptIds);

        DashboardSummaryVO vo = new DashboardSummaryVO();

        // ① 在线率（D-01/D-02）
        DashboardSummaryVO.Online online = dashboardMapper.selectOnline(windowMinutes, deptIds);
        if (online == null) {
            online = new DashboardSummaryVO.Online();
        }
        online.setWindowMinutes(windowMinutes);
        online.setOnlineRate(online.getVehicleTotal() == 0
                ? null
                : BigDecimal.valueOf((double) online.getOnlineCount() / online.getVehicleTotal())
                        .setScale(4, RoundingMode.HALF_UP).doubleValue());
        vo.setOnline(online);

        // ②b 今日里程（D-03）
        DashboardSummaryVO.Mileage mileage = new DashboardSummaryVO.Mileage();
        mileage.setTodayMileage(round1(dashboardMapper.selectTodayMileage(dayStart, plates)));
        vo.setMileage(mileage);

        // ⑤ 工单积压与闭环率（D-06/D-07）
        DashboardSummaryVO.WorkOrder wo = dashboardMapper.selectWorkOrder(plates);
        if (wo == null) {
            wo = new DashboardSummaryVO.WorkOrder();
        }
        wo.setCloseRate(wo.getCreated7d() == 0
                ? null
                : BigDecimal.valueOf((double) wo.getClosed7d() / wo.getCreated7d())
                        .setScale(4, RoundingMode.HALF_UP).doubleValue());
        vo.setWorkOrder(wo);

        // ⑥ 车队分布 TOP 8（D-08）
        vo.setFleetStats(dashboardMapper.selectFleetStats(windowMinutes, deptIds));

        // ⑦ 区域分布：车辆数 + 当日风险数合并，按车辆数降序 TOP 8（D-09）
        vo.setRegionStats(mergeRegions(dayStart, deptIds));

        // ⑩ 风险热力（D-10）
        vo.setRiskHeat(dashboardMapper.selectRiskHeat(plates));

        vo.setServerTime(now);
        return vo;
    }

    /** 当前用户可见部门集合：null=不限制；空集合=无可见数据 */
    private Collection<Long> currentDeptIds() {
        UserInfo user = UserContext.get();
        if (user == null || user.allData()) {
            return null;
        }
        return user.deptScope() == null ? List.of() : user.deptScope();
    }

    /** fail-closed：空数据范围时大屏全部归零 */
    private DashboardSummaryVO closedSummary(LocalDateTime now, int windowMinutes) {
        DashboardSummaryVO vo = new DashboardSummaryVO();
        DashboardSummaryVO.Online online = new DashboardSummaryVO.Online();
        online.setWindowMinutes(windowMinutes);
        vo.setOnline(online);
        vo.setMileage(new DashboardSummaryVO.Mileage());
        vo.setWorkOrder(new DashboardSummaryVO.WorkOrder());
        vo.setFleetStats(List.of());
        vo.setRegionStats(List.of());
        vo.setRiskHeat(List.of());
        vo.setServerTime(now);
        return vo;
    }

    /** 在线窗口分钟数：键缺失、非数字、≤0 或 >1440 一律回落缺省 5 */
    private int onlineWindowMinutes() {
        String raw = dashboardMapper.selectConfigValue(CONFIG_KEY_ONLINE_WINDOW);
        if (raw == null) {
            return DEFAULT_ONLINE_WINDOW_MINUTES;
        }
        try {
            int n = Integer.parseInt(raw.trim());
            return (n >= 1 && n <= 1440) ? n : DEFAULT_ONLINE_WINDOW_MINUTES;
        } catch (NumberFormatException ex) {
            return DEFAULT_ONLINE_WINDOW_MINUTES;
        }
    }

    private List<DashboardSummaryVO.RegionStat> mergeRegions(LocalDateTime dayStart,
                                                             Collection<Long> deptIds) {
        Map<String, DashboardSummaryVO.RegionStat> merged = new LinkedHashMap<>();
        for (Map<String, Object> row : dashboardMapper.selectRegionVehicleCounts(deptIds)) {
            String code = (String) row.get("cityCode");
            merged.computeIfAbsent(code, k -> newRegion(k)).setVehicleCount(((Number) row.get("vehicleCount")).longValue());
        }
        for (Map<String, Object> row : dashboardMapper.selectRegionRiskCounts(dayStart, deptIds)) {
            String code = (String) row.get("cityCode");
            merged.computeIfAbsent(code, k -> newRegion(k)).setRiskCount(((Number) row.get("riskCount")).longValue());
        }
        List<DashboardSummaryVO.RegionStat> list = new ArrayList<>(merged.values());
        list.sort(Comparator.comparingLong(DashboardSummaryVO.RegionStat::getVehicleCount).reversed());
        return list.size() > 8 ? new ArrayList<>(list.subList(0, 8)) : list;
    }

    private DashboardSummaryVO.RegionStat newRegion(String code) {
        DashboardSummaryVO.RegionStat stat = new DashboardSummaryVO.RegionStat();
        stat.setCityCode(code);
        // 库内无城市字典表：空 code 归"未知"，其余直接回显 code 作为名称
        stat.setCityName(code.isEmpty() ? "未知" : code);
        return stat;
    }

    private double round1(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
