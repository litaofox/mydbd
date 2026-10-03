package com.mydbd.analysis.profile.mapper;

import com.mydbd.analysis.profile.dto.CodeBucketRow;
import com.mydbd.analysis.profile.dto.CodeCntRow;
import com.mydbd.analysis.profile.dto.CompositionRow;
import com.mydbd.analysis.profile.dto.EventTotalRow;
import com.mydbd.analysis.profile.dto.HotspotRow;
import com.mydbd.analysis.profile.dto.RankRow;
import com.mydbd.analysis.profile.dto.TrendBucketRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * F23 风险事件只读聚合（mon.risk_event）。
 * 通用口径（MOD-ANA-002 §3.0）：时间基准 COALESCE(event_time, create_date)，区间 [start,end)；
 * plateNos=null 表示全网（不加车牌条件），非空表示限定对象车牌集；
 * deptScope=null 不限制，非空追加车辆归属 EXISTS 过滤。
 * granularity 走 &lt;choose&gt; 字面量拼接（白名单已在 Service 校验，杜绝注入且保执行计划稳定）。
 */
@Mapper
public interface ProfileEventMapper {

    /** 趋势：分桶 + 总量 + 分等级（FILTER 紧跟聚合函数） */
    @Select("""
            <script>
            SELECT
            <choose>
              <when test="gran == 'week'">date_trunc('week', COALESCE(e.event_time, e.create_date))</when>
              <when test="gran == 'month'">date_trunc('month', COALESCE(e.event_time, e.create_date))</when>
              <otherwise>date_trunc('day', COALESCE(e.event_time, e.create_date))</otherwise>
            </choose> AS bucket,
                   count(*)                                 AS total,
                   count(*) FILTER (WHERE e.risk_level = 3) AS highCnt,
                   count(*) FILTER (WHERE e.risk_level = 2) AS midCnt,
                   count(*) FILTER (WHERE e.risk_level = 1) AS lowCnt
            FROM mon.risk_event e
            WHERE COALESCE(e.event_time, e.create_date) &gt;= #{start}
              AND COALESCE(e.event_time, e.create_date) &lt;  #{end}
            <if test="plateNos != null">
              AND e.plate_no IN <foreach item="p" collection="plateNos" open="(" separator="," close=")">#{p}</foreach>
            </if>
            <if test="deptScope != null">
              AND EXISTS (SELECT 1 FROM traj.traj_vehicle v
                          WHERE v.vehicle_no = e.plate_no AND v.valid_mark = 1
                            AND v.dept_id IN <foreach item="d" collection="deptScope" open="(" separator="," close=")">#{d}</foreach>)
            </if>
            GROUP BY 1 ORDER BY 1
            </script>
            """)
    List<TrendBucketRow> trendBuckets(@Param("gran") String gran,
                                      @Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end,
                                      @Param("plateNos") List<String> plateNos,
                                      @Param("deptScope") List<Long> deptScope);

    /** 趋势：Top5 事件码（先选码） */
    @Select("""
            <script>
            SELECT e.event_code AS eventCode, count(*) AS cnt
            FROM mon.risk_event e
            WHERE COALESCE(e.event_time, e.create_date) &gt;= #{start}
              AND COALESCE(e.event_time, e.create_date) &lt;  #{end}
              AND e.event_code IS NOT NULL
            <if test="plateNos != null">
              AND e.plate_no IN <foreach item="p" collection="plateNos" open="(" separator="," close=")">#{p}</foreach>
            </if>
            <if test="deptScope != null">
              AND EXISTS (SELECT 1 FROM traj.traj_vehicle v
                          WHERE v.vehicle_no = e.plate_no AND v.valid_mark = 1
                            AND v.dept_id IN <foreach item="d" collection="deptScope" open="(" separator="," close=")">#{d}</foreach>)
            </if>
            GROUP BY e.event_code ORDER BY count(*) DESC LIMIT 5
            </script>
            """)
    List<CodeCntRow> topCodes(@Param("start") LocalDateTime start,
                              @Param("end") LocalDateTime end,
                              @Param("plateNos") List<String> plateNos,
                              @Param("deptScope") List<Long> deptScope);

    /** 趋势：Top5 事件码分桶序列（再取数） */
    @Select("""
            <script>
            SELECT
            <choose>
              <when test="gran == 'week'">date_trunc('week', COALESCE(e.event_time, e.create_date))</when>
              <when test="gran == 'month'">date_trunc('month', COALESCE(e.event_time, e.create_date))</when>
              <otherwise>date_trunc('day', COALESCE(e.event_time, e.create_date))</otherwise>
            </choose> AS bucket,
                   e.event_code AS eventCode, count(*) AS cnt
            FROM mon.risk_event e
            WHERE COALESCE(e.event_time, e.create_date) &gt;= #{start}
              AND COALESCE(e.event_time, e.create_date) &lt;  #{end}
              AND e.event_code = ANY(string_to_array(#{codesCsv}, ','))
            <if test="plateNos != null">
              AND e.plate_no IN <foreach item="p" collection="plateNos" open="(" separator="," close=")">#{p}</foreach>
            </if>
            <if test="deptScope != null">
              AND EXISTS (SELECT 1 FROM traj.traj_vehicle v
                          WHERE v.vehicle_no = e.plate_no AND v.valid_mark = 1
                            AND v.dept_id IN <foreach item="d" collection="deptScope" open="(" separator="," close=")">#{d}</foreach>)
            </if>
            GROUP BY 1, 2 ORDER BY 1
            </script>
            """)
    List<CodeBucketRow> codeBuckets(@Param("gran") String gran,
                                    @Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end,
                                    @Param("codesCsv") String codesCsv,
                                    @Param("plateNos") List<String> plateNos,
                                    @Param("deptScope") List<Long> deptScope);

    /** 事件构成（画像：按码 + 分等级计数） */
    @Select("""
            <script>
            SELECT e.event_code AS eventCode,
                   count(*)                                 AS cnt,
                   count(*) FILTER (WHERE e.risk_level = 3) AS high,
                   count(*) FILTER (WHERE e.risk_level = 2) AS mid,
                   count(*) FILTER (WHERE e.risk_level = 1) AS low
            FROM mon.risk_event e
            WHERE COALESCE(e.event_time, e.create_date) &gt;= #{start}
              AND COALESCE(e.event_time, e.create_date) &lt;  #{end}
              AND e.plate_no IN <foreach item="p" collection="plateNos" open="(" separator="," close=")">#{p}</foreach>
            <if test="deptScope != null">
              AND EXISTS (SELECT 1 FROM traj.traj_vehicle v
                          WHERE v.vehicle_no = e.plate_no AND v.valid_mark = 1
                            AND v.dept_id IN <foreach item="d" collection="deptScope" open="(" separator="," close=")">#{d}</foreach>)
            </if>
            GROUP BY e.event_code ORDER BY count(*) DESC
            </script>
            """)
    List<CompositionRow> composition(@Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end,
                                     @Param("plateNos") List<String> plateNos,
                                     @Param("deptScope") List<Long> deptScope);

    /** 对象事件总数 + 高危数（画像概览头卡） */
    @Select("""
            <script>
            SELECT count(*) AS cnt,
                   count(*) FILTER (WHERE e.risk_level = 3) AS high
            FROM mon.risk_event e
            WHERE COALESCE(e.event_time, e.create_date) &gt;= #{start}
              AND COALESCE(e.event_time, e.create_date) &lt;  #{end}
              AND e.plate_no IN <foreach item="p" collection="plateNos" open="(" separator="," close=")">#{p}</foreach>
            </script>
            """)
    EventTotalRow eventTotal(@Param("start") LocalDateTime start,
                                                         @Param("end") LocalDateTime end,
                                                         @Param("plateNos") List<String> plateNos);

    /** 排行：按车牌聚合 Top N（weightedScore=SUM(risk_level)） */
    @Select("""
            <script>
            SELECT e.plate_no                               AS plateNo,
                   max(e.identity_code)                     AS identityCode,
                   count(*)                                 AS eventCnt,
                   count(*) FILTER (WHERE e.risk_level = 3) AS highCnt,
                   COALESCE(SUM(COALESCE(e.risk_level, 2)), 0) AS weightedScore
            FROM mon.risk_event e
            WHERE COALESCE(e.event_time, e.create_date) &gt;= #{start}
              AND COALESCE(e.event_time, e.create_date) &lt;  #{end}
              AND e.plate_no IS NOT NULL
            <if test="deptScope != null">
              AND EXISTS (SELECT 1 FROM traj.traj_vehicle v
                          WHERE v.vehicle_no = e.plate_no AND v.valid_mark = 1
                            AND v.dept_id IN <foreach item="d" collection="deptScope" open="(" separator="," close=")">#{d}</foreach>)
            </if>
            GROUP BY e.plate_no
            ORDER BY weightedScore DESC, eventCnt DESC
            LIMIT #{limit}
            </script>
            """)
    List<RankRow> rankingPlates(@Param("start") LocalDateTime start,
                                @Param("end") LocalDateTime end,
                                @Param("deptScope") List<Long> deptScope,
                                @Param("limit") int limit);

    /** 黑点路段：3857 网格吸附聚合 Top N（近似口径） */
    @Select("""
            <script>
            WITH pts AS (
                SELECT ST_SnapToGrid(ST_Transform(ST_SetSRID(ST_MakePoint(e.lng, e.lat), 4326), 3857), #{radiusM}) AS cell,
                       COALESCE(e.risk_level, 2) AS lvl, e.plate_no, e.event_code
                FROM mon.risk_event e
                WHERE e.lng IS NOT NULL AND e.lat IS NOT NULL
                  AND COALESCE(e.event_time, e.create_date) &gt;= #{start}
                  AND COALESCE(e.event_time, e.create_date) &lt;  #{end}
                <if test="deptScope != null">
                  AND EXISTS (SELECT 1 FROM traj.traj_vehicle v
                              WHERE v.vehicle_no = e.plate_no AND v.valid_mark = 1
                                AND v.dept_id IN <foreach item="d" collection="deptScope" open="(" separator="," close=")">#{d}</foreach>)
                </if>
            ),
            agg AS (
                SELECT cell,
                       count(*)                                   AS event_cnt,
                       SUM(lvl)                                   AS weighted_score,
                       count(DISTINCT plate_no)                   AS plate_cnt,
                       (array_agg(plate_no ORDER BY lvl DESC))[1]    AS top_plate,
                       (array_agg(event_code ORDER BY lvl DESC))[1]  AS top_code
                FROM pts GROUP BY cell
                ORDER BY weighted_score DESC, event_cnt DESC
                LIMIT #{limit}
            )
            SELECT round(ST_X(ST_Transform(ST_Centroid(cell), 4326))::numeric, 6) AS lng,
                   round(ST_Y(ST_Transform(ST_Centroid(cell), 4326))::numeric, 6) AS lat,
                   event_cnt AS eventCnt, weighted_score AS weightedScore,
                   plate_cnt AS plateCnt, top_plate AS topPlate, top_code AS topCode
            FROM agg ORDER BY weighted_score DESC, event_cnt DESC
            </script>
            """)
    List<HotspotRow> hotspots(@Param("start") LocalDateTime start,
                              @Param("end") LocalDateTime end,
                              @Param("radiusM") int radiusM,
                              @Param("limit") int limit,
                              @Param("deptScope") List<Long> deptScope);
}
