package com.mydbd.risk.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * F18 引擎运行统计（聚合 mon.risk_event / 配置表）
 */
@Mapper
public interface RiskStatsMapper {

    @Select("SELECT count(*) FROM mon.risk_event WHERE event_time >= CURRENT_DATE")
    long countTodayEvents();

    @Select("SELECT COALESCE(risk_level, 2) AS level, count(*) AS cnt "
            + "FROM mon.risk_event WHERE event_time >= CURRENT_DATE GROUP BY risk_level")
    List<Map<String, Object>> countTodayByLevel();

    /** 今日事件 TOP5 规则 */
    @Select("SELECT COALESCE(rule_id, 0) AS rule_id, COALESCE(MAX(title), '') AS title, "
            + "count(*) AS cnt FROM mon.risk_event "
            + "WHERE event_time >= CURRENT_DATE AND rule_id IS NOT NULL "
            + "GROUP BY rule_id ORDER BY cnt DESC LIMIT 5")
    List<Map<String, Object>> todayTopRules();
}
