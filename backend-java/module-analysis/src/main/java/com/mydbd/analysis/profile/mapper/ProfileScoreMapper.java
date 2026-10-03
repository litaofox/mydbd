package com.mydbd.analysis.profile.mapper;

import com.mydbd.analysis.profile.dto.ScoreCurveRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * F23 只读 mon.driver_score（F22 产出）。表缺失时由 Service 捕获 PSQLException 降级。
 */
@Mapper
public interface ProfileScoreMapper {

    /** 评分曲线：driverIds 集合（vehicle 画像=当前绑定司机；driver 画像=本人） */
    @Select("""
            <script>
            SELECT score_date AS scoreDate, score AS score, level AS level
            FROM mon.driver_score
            WHERE driver_id IN <foreach item="d" collection="driverIds" open="(" separator="," close=")">#{d}</foreach>
              AND score_date BETWEEN #{startDate} AND #{endDate}
            ORDER BY score_date ASC
            </script>
            """)
    List<ScoreCurveRow> curveByDrivers(@Param("driverIds") List<Long> driverIds,
                                       @Param("startDate") LocalDate startDate,
                                       @Param("endDate") LocalDate endDate);

    /** 评分曲线：企业均值（dept 画像） */
    @Select("""
            SELECT score_date AS scoreDate, ROUND(AVG(score)::numeric, 1) AS score, NULL AS level
            FROM mon.driver_score
            WHERE dept_id = #{deptId} AND score_date BETWEEN #{startDate} AND #{endDate}
            GROUP BY score_date ORDER BY score_date ASC
            """)
    List<ScoreCurveRow> curveByDept(@Param("deptId") Long deptId,
                                    @Param("startDate") LocalDate startDate,
                                    @Param("endDate") LocalDate endDate);

    /** 周期末最新评分（单对象；level 取该日行） */
    @Select("""
            <script>
            SELECT score_date AS scoreDate, score AS score, level AS level
            FROM mon.driver_score
            WHERE driver_id IN <foreach item="d" collection="driverIds" open="(" separator="," close=")">#{d}</foreach>
              AND score_date BETWEEN #{startDate} AND #{endDate}
            ORDER BY score_date DESC, score ASC
            LIMIT 1
            </script>
            """)
    ScoreCurveRow latestByDrivers(@Param("driverIds") List<Long> driverIds,
                                  @Param("startDate") LocalDate startDate,
                                  @Param("endDate") LocalDate endDate);

    /** 排行批量：driverIds 各自周期内最新评分（Java 侧按 driverId 对齐） */
    @Select("""
            <script>
            SELECT DISTINCT ON (driver_id) driver_id AS "driverId", score AS "score", level AS "level"
            FROM mon.driver_score
            WHERE driver_id IN <foreach item="d" collection="driverIds" open="(" separator="," close=")">#{d}</foreach>
              AND score_date BETWEEN #{startDate} AND #{endDate}
            ORDER BY driver_id, score_date DESC, score ASC
            </script>
            """)
    List<Map<String, Object>> latestByDriverIds(@Param("driverIds") List<Long> driverIds,
                                                @Param("startDate") LocalDate startDate,
                                                @Param("endDate") LocalDate endDate);
}
