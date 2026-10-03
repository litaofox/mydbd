package com.mydbd.analysis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mydbd.analysis.entity.DriverScore;
import com.mydbd.analysis.vo.ScoreVO;
import com.mydbd.analysis.vo.SummaryVO;
import com.mydbd.analysis.vo.TrendVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/**
 * F22 评分快照读写
 */
@Mapper
public interface DriverScoreMapper extends BaseMapper<DriverScore> {

    /** 重算幂等 upsert（ON CONFLICT 覆盖派生列，create_date 保留首写值） */
    @Insert("""
            INSERT INTO mon.driver_score
                (score_date, driver_id, identity_code, plate_no, dept_id,
                 score, level, features, sample_points, event_count, create_date)
            VALUES
                (#{e.scoreDate}, #{e.driverId}, #{e.identityCode}, #{e.plateNo}, #{e.deptId},
                 #{e.score}, #{e.level}, #{e.features,typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
                 #{e.samplePoints}, #{e.eventCount}, CURRENT_TIMESTAMP)
            ON CONFLICT (score_date, driver_id) DO UPDATE SET
                identity_code = EXCLUDED.identity_code,
                plate_no      = EXCLUDED.plate_no,
                dept_id       = EXCLUDED.dept_id,
                score         = EXCLUDED.score,
                level         = EXCLUDED.level,
                features      = EXCLUDED.features,
                sample_points = EXCLUDED.sample_points,
                event_count   = EXCLUDED.event_count
            """)
    int upsert(@Param("e") DriverScore e);

    @Delete("""
            DELETE FROM mon.driver_score
            WHERE score_date = #{date} AND driver_id = #{driverId}
            """)
    int deleteDayDriver(@Param("date") LocalDate date, @Param("driverId") Long driverId);

    /** 全日重算后清扫：删除该日不在保留集合中的行（解绑/归属变更一致性） */
    @Delete("""
            <script>
            DELETE FROM mon.driver_score WHERE score_date = #{date}
            <if test="keepIds != null and keepIds.size() > 0">
              AND driver_id NOT IN <foreach item="k" collection="keepIds" open="(" separator="," close=")">#{k}</foreach>
            </if>
            </script>
            """)
    int deleteDayExcept(@Param("date") LocalDate date, @Param("keepIds") List<Long> keepIds);

    /** 分页（LEFT JOIN 司机名带出 driverName；deptScope 由 Service 传入集合，null=不限） */
    @Results(@Result(column = "features", property = "features",
            typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class))
    @Select("""
            <script>
            SELECT s.id AS id, s.score_date AS scoreDate, s.driver_id AS driverId,
                   d.driver_name AS driverName, s.identity_code AS identityCode,
                   s.plate_no AS plateNo, s.dept_id AS deptId, s.score AS score,
                   s.level AS level, s.sample_points AS samplePoints,
                   s.event_count AS eventCount, s.features AS features
            FROM mon.driver_score s
            LEFT JOIN traj.traj_driver d ON d.id = s.driver_id
            WHERE s.score_date BETWEEN #{startDate} AND #{endDate}
            <if test="driverId != null"> AND s.driver_id = #{driverId} </if>
            <if test="plateNo != null and plateNo != ''"> AND s.plate_no ILIKE '%' || #{plateNo} || '%' </if>
            <if test="level != null and level != ''"> AND s.level = #{level} </if>
            <if test="deptScope != null">
              AND s.dept_id IN <foreach item="d" collection="deptScope" open="(" separator="," close=")">#{d}</foreach>
            </if>
            ORDER BY s.score_date DESC, s.score ASC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<ScoreVO> pageQuery(@Param("startDate") LocalDate startDate,
                            @Param("endDate") LocalDate endDate,
                            @Param("driverId") Long driverId,
                            @Param("plateNo") String plateNo,
                            @Param("level") String level,
                            @Param("deptScope") List<Long> deptScope,
                            @Param("limit") long limit,
                            @Param("offset") long offset);

    @Select("""
            <script>
            SELECT count(*) FROM mon.driver_score s
            WHERE s.score_date BETWEEN #{startDate} AND #{endDate}
            <if test="driverId != null"> AND s.driver_id = #{driverId} </if>
            <if test="plateNo != null and plateNo != ''"> AND s.plate_no ILIKE '%' || #{plateNo} || '%' </if>
            <if test="level != null and level != ''"> AND s.level = #{level} </if>
            <if test="deptScope != null">
              AND s.dept_id IN <foreach item="d" collection="deptScope" open="(" separator="," close=")">#{d}</foreach>
            </if>
            </script>
            """)
    long pageCount(@Param("startDate") LocalDate startDate,
                   @Param("endDate") LocalDate endDate,
                   @Param("driverId") Long driverId,
                   @Param("plateNo") String plateNo,
                   @Param("level") String level,
                   @Param("deptScope") List<Long> deptScope);

    @Select("""
            <script>
            SELECT score_date AS scoreDate, score AS score, level AS level
            FROM mon.driver_score
            WHERE driver_id = #{driverId} AND score_date BETWEEN #{startDate} AND #{endDate}
            <if test="deptScope != null">
              AND dept_id IN <foreach item="d" collection="deptScope" open="(" separator="," close=")">#{d}</foreach>
            </if>
            ORDER BY score_date ASC
            </script>
            """)
    List<TrendVO> trend(@Param("driverId") Long driverId,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        @Param("deptScope") List<Long> deptScope);

    @Select("""
            <script>
            SELECT COALESCE(ROUND(AVG(score)::numeric, 1), 0) AS avgScore,
                   count(*) AS recordCount,
                   count(DISTINCT driver_id) AS driverCount
            FROM mon.driver_score
            WHERE score_date BETWEEN #{startDate} AND #{endDate}
            <if test="deptScope != null">
              AND dept_id IN <foreach item="d" collection="deptScope" open="(" separator="," close=")">#{d}</foreach>
            </if>
            </script>
            """)
    SummaryVO summaryBase(@Param("startDate") LocalDate startDate,
                          @Param("endDate") LocalDate endDate,
                          @Param("deptScope") List<Long> deptScope);

    @Select("""
            <script>
            SELECT level AS level, count(*) AS count
            FROM mon.driver_score
            WHERE score_date BETWEEN #{startDate} AND #{endDate}
            <if test="deptScope != null">
              AND dept_id IN <foreach item="d" collection="deptScope" open="(" separator="," close=")">#{d}</foreach>
            </if>
            GROUP BY level ORDER BY level ASC
            </script>
            """)
    List<SummaryVO.LevelCount> summaryLevelDist(@Param("startDate") LocalDate startDate,
                                                @Param("endDate") LocalDate endDate,
                                                @Param("deptScope") List<Long> deptScope);
}
