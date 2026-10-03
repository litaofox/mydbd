package com.mydbd.analysis.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * F22 每日 01:00 T+1 全量评分（MOD-ANA-001 §5.2）。
 * @EnableScheduling 已在启动类，不重复加。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScoreScheduler {

    private final ScoreCalcService calcService;

    @Scheduled(cron = "0 0 1 * * ?")
    public void dailyT1() {
        // 与手动重算共用互斥锁（MOD §5.3）：抢不到说明有任务在跑，本次跳过
        if (!calcService.tryAcquire()) {
            log.info("F22 定时评分跳过：已有计算任务进行中");
            return;
        }
        try {
            LocalDate d = LocalDate.now().minusDays(1);
            int days = calcService.recalc(d, d, null);
            log.info("F22 定时评分完成，处理 {} 天", days);
        } catch (Exception e) {
            log.warn("F22 定时评分失败：{}", e.getMessage());
        } finally {
            calcService.release();
        }
    }
}
