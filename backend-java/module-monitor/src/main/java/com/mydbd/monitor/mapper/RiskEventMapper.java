package com.mydbd.monitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mydbd.monitor.entity.RiskEvent;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface RiskEventMapper extends BaseMapper<RiskEvent> {

    /**
     * F17 报警详情关联风险（MOD-MON-004 §3.3）：
     * 同车牌、报警开始时间 ±30 分钟窗口内的 CEP 事件前 5 条（同库跨 schema 只读）。
     */
    @Select("""
            SELECT id, event_code AS "eventCode", title, risk_level AS "riskLevel",
                   event_time AS "eventTime"
              FROM mon.risk_event
             WHERE plate_no = #{plateNo}
               AND event_time >= #{center} - INTERVAL '30 minutes'
               AND event_time <= #{center} + INTERVAL '30 minutes'
             ORDER BY event_time DESC
             LIMIT 5
            """)
    List<Map<String, Object>> relatedRisks(@Param("plateNo") String plateNo,
                                           @Param("center") LocalDateTime center);
}
