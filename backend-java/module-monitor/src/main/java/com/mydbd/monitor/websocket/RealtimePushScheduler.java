package com.mydbd.monitor.websocket;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mydbd.monitor.entity.RiskEvent;
import com.mydbd.monitor.entity.WarnInfo;
import com.mydbd.monitor.mapper.RiskEventMapper;
import com.mydbd.monitor.mapper.WarnInfoMapper;
import com.mydbd.traj.entity.GpsPoint;
import com.mydbd.traj.service.TrajectoryService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 实时推送调度器：每秒推位置全量，增量推风险事件与终端报警。
 * 三类消息均按各会话用户的部门数据范围过滤后发送（fail-closed）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimePushScheduler {

    private final SessionRegistry sessionRegistry;
    private final TrajectoryService trajectoryService;
    private final RiskEventMapper riskEventMapper;
    private final WarnInfoMapper warnInfoMapper;
    private final WsScopeResolver wsScopeResolver;

    private final AtomicLong lastRiskId = new AtomicLong(0);
    private final AtomicLong lastAlarmId = new AtomicLong(0);

    @PostConstruct
    void initCursor() {
        // 游标初始化为当前最大 id，只推送启动后的新增事件，避免重启时回放全量历史
        Long maxRisk = riskEventMapper.selectCount(null) > 0
                ? riskEventMapper.selectList(new LambdaQueryWrapper<RiskEvent>()
                        .orderByDesc(RiskEvent::getId).last("limit 1"))
                        .stream().findFirst().map(RiskEvent::getId).orElse(0L)
                : 0L;
        lastRiskId.set(maxRisk);
        Long maxAlarm = warnInfoMapper.selectCount(null) > 0
                ? warnInfoMapper.selectList(new LambdaQueryWrapper<WarnInfo>()
                        .orderByDesc(WarnInfo::getId).last("limit 1"))
                        .stream().findFirst().map(WarnInfo::getId).orElse(0L)
                : 0L;
        lastAlarmId.set(maxAlarm);
        log.info("WS push cursor initialized: riskId={}, alarmId={}", maxRisk, maxAlarm);
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 5000)
    public void tick() {
        try {
            pushPoints();
            int riskCnt = pushRisks();
            int alarmCnt = pushAlarms();
            if (riskCnt > 0 || alarmCnt > 0) {
                log.info("WS push tick: risks={}, alarms={}, sessions={}", riskCnt, alarmCnt, sessionRegistry.size());
            }
        } catch (Exception e) {
            log.error("WS push tick failed", e);
        }
    }

    /** 全量位置快照：按各会话数据范围过滤后发送 */
    public void pushPoints() {
        if (sessionRegistry.size() == 0) {
            return;
        }
        List<GpsPoint> points = trajectoryService.listLatestPointsForPush();
        sessionRegistry.broadcastScoped("POINTS", points,
                GpsPoint::getPlateNo, wsScopeResolver::allowedPlates);
    }

    /** 新连接首帧位置：按该会话数据范围过滤 */
    public void pushPointsOnce(WebSocketSession session) {
        List<GpsPoint> points = trajectoryService.listLatestPointsForPush();
        Set<String> scope = wsScopeResolver.allowedPlates(session);
        sessionRegistry.sendScoped(session, "POINTS", points, GpsPoint::getPlateNo, scope);
    }

    /** 增量风险事件（按 id 游标）：全局游标不变，按各会话范围过滤发送 */
    private int pushRisks() {
        long since = lastRiskId.get();
        List<RiskEvent> list = riskEventMapper.selectList(
                new LambdaQueryWrapper<RiskEvent>()
                        .gt(RiskEvent::getId, since)
                        .orderByAsc(RiskEvent::getId)
                        .last("limit 50"));
        if (list.isEmpty()) {
            return 0;
        }
        for (RiskEvent e : list) {
            if (e.getId() > lastRiskId.get()) {
                lastRiskId.set(e.getId());
            }
        }
        sessionRegistry.broadcastScoped("RISK", list,
                RiskEvent::getPlateNo, wsScopeResolver::allowedPlates);
        return list.size();
    }

    /** 增量终端报警（按 id 游标）：全局游标不变，按各会话范围过滤发送 */
    private int pushAlarms() {
        long since = lastAlarmId.get();
        List<WarnInfo> list = warnInfoMapper.selectList(
                new LambdaQueryWrapper<WarnInfo>()
                        .gt(WarnInfo::getId, since)
                        .orderByAsc(WarnInfo::getId)
                        .last("limit 50"));
        if (list.isEmpty()) {
            return 0;
        }
        for (WarnInfo w : list) {
            if (w.getId() > lastAlarmId.get()) {
                lastAlarmId.set(w.getId());
            }
        }
        sessionRegistry.broadcastScoped("ALARM", list,
                WarnInfo::getPlateNo, wsScopeResolver::allowedPlates);
        return list.size();
    }
}
