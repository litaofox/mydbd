package com.mydbd.analysis.profile.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * F23 对象画像只读：工单漏斗与 Top 工单（mon.risk_work_order / mon.risk_intervention）。
 * 口径同 F21 funnel，但限定对象车牌集。
 */
@Mapper
public interface ProfileOrderMapper {

    /** ② 工单数 */
    @Select("""
            <script>
            SELECT count(*) FROM mon.risk_work_order w
            WHERE w.valid_mark = 1
              AND w.plate_no IN <foreach item="p" collection="plateNos" open="(" separator="," close=")">#{p}</foreach>
              AND COALESCE(w.event_time, w.create_date) &gt;= #{start}
              AND COALESCE(w.event_time, w.create_date) &lt;  #{end}
            </script>
            """)
    long orderTotal(@Param("start") LocalDateTime start,
                    @Param("end") LocalDateTime end,
                    @Param("plateNos") List<String> plateNos);

    /** ③ 有干预工单数（distinct order_id，口径同 F21） */
    @Select("""
            <script>
            SELECT count(DISTINCT i.order_id)
            FROM mon.risk_intervention i
            JOIN mon.risk_work_order w ON w.id = i.order_id AND w.valid_mark = 1
            WHERE i.create_date &gt;= #{start} AND i.create_date &lt; #{end}
              AND w.plate_no IN <foreach item="p" collection="plateNos" open="(" separator="," close=")">#{p}</foreach>
            </script>
            """)
    long interventionTotal(@Param("start") LocalDateTime start,
                           @Param("end") LocalDateTime end,
                           @Param("plateNos") List<String> plateNos);

    /** ④ 闭环工单数（status=CLOSED 且 close_time 在范围） */
    @Select("""
            <script>
            SELECT count(*) FROM mon.risk_work_order w
            WHERE w.valid_mark = 1 AND w.status = 'CLOSED'
              AND w.close_time &gt;= #{start} AND w.close_time &lt; #{end}
              AND w.plate_no IN <foreach item="p" collection="plateNos" open="(" separator="," close=")">#{p}</foreach>
            </script>
            """)
    long closedTotal(@Param("start") LocalDateTime start,
                     @Param("end") LocalDateTime end,
                     @Param("plateNos") List<String> plateNos);

    /** 工单 Top5：等级降序、超时优先、事件时间降序 */
    @Select("""
            <script>
            SELECT w.id            AS "id",
                   w.order_no      AS "orderNo",
                   COALESCE(NULLIF(w.event_title, ''), w.event_code) AS "title",
                   w.risk_level    AS "riskLevel",
                   w.status        AS "status",
                   w.overdue       AS "overdue",
                   to_char(w.event_time, 'YYYY-MM-DD HH24:MI:SS') AS "eventTime"
            FROM mon.risk_work_order w
            WHERE w.valid_mark = 1
              AND w.plate_no IN <foreach item="p" collection="plateNos" open="(" separator="," close=")">#{p}</foreach>
              AND COALESCE(w.event_time, w.create_date) &gt;= #{start}
              AND COALESCE(w.event_time, w.create_date) &lt;  #{end}
            ORDER BY w.risk_level DESC, w.overdue DESC, w.event_time DESC
            LIMIT 5
            </script>
            """)
    List<Map<String, Object>> topOrders(@Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end,
                                        @Param("plateNos") List<String> plateNos);
}
