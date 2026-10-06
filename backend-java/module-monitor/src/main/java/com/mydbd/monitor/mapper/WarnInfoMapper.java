package com.mydbd.monitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mydbd.monitor.entity.WarnInfo;
import com.mydbd.monitor.vo.AlarmVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface WarnInfoMapper extends BaseMapper<WarnInfo> {

    /**
     * 今日终端报警数。
     * @param plates 可见车牌：null=不限制；空集合调用方提前返回 0
     */
    @Select("""
            <script>
            SELECT count(*) FROM traj.traj_warn_info
             WHERE start_warn_time::date = CURRENT_DATE
            <if test="plates != null">
              AND plate_no IN
              <foreach collection="plates" item="x" open="(" separator="," close=")">#{x}</foreach>
            </if>
            </script>
            """)
    long countTodayWarnings(@Param("plates") java.util.Collection<String> plates);

    /**
     * 在线/在途车辆数（监控域直接读取 traj schema，避免跨模块依赖）。
     * 在线口径（GATEWAY-PLAN-001）：经车辆-终端绑定 join，绑定终端 online_status=1 优先；
     * 无绑定终端或状态未知（NULL）时回退"有轨迹点"原口径（EXISTS 索引探测）。
     * 性能：终端表驱动 + EXISTS 索引探测，避免 count(DISTINCT) 全量扫描轨迹表。
     * @param plates 可见车牌：null=不限制；空集合调用方提前返回 0
     */
    @Select("""
            <script>
            SELECT count(*)
              FROM traj.traj_terminal t
              LEFT JOIN traj.traj_vehicle_terminal vt
                     ON vt.terminal_id = t.id AND vt.status = 1 AND vt.valid_mark = 1
              LEFT JOIN traj.traj_vehicle v ON v.id = vt.vehicle_id AND v.valid_mark = 1
             WHERE t.valid_mark = 1
               AND ((vt.id IS NOT NULL AND t.online_status = 1
            <if test="plates != null">
                     AND v.vehicle_no IN
                     <foreach collection="plates" item="x" open="(" separator="," close=")">#{x}</foreach>
            </if>
                    )
                 OR ((vt.id IS NULL OR t.online_status IS NULL)
                     AND EXISTS (SELECT 1 FROM traj.traj_gps_point g
                                  WHERE g.identity_code = t.identity_code
            <if test="plates != null">
                                    AND g.plate_no IN
                                    <foreach collection="plates" item="x" open="(" separator="," close=")">#{x}</foreach>
            </if>
                                  LIMIT 1)))
            </script>
            """)
    long countActiveVehicles(@Param("plates") java.util.Collection<String> plates);

    /**
     * F17 分页列表（MOD-MON-004 §4.1）：LEFT JOIN base_warn_type 取名称/等级，
     * 动态条件；排序 start_warn_time DESC, id DESC。
     * handleStatus 用 COALESCE 归一（历史 NULL 视为 0）。
     */
    @Select("""
            <script>
            SELECT w.id, w.plate_no AS "plateNo", w.identity_code AS "identityCode",
                   w.type_id AS "typeId",
                   COALESCE(t.name, '类型 ' || w.type_id) AS "typeName",
                   t.grade_level AS "gradeLevel",
                   w.start_warn_time AS "startWarnTime", w.end_warn_time AS "endWarnTime",
                   w.start_lng AS "startLng", w.start_lat AS "startLat",
                   w.end_lng AS "endLng", w.end_lat AS "endLat",
                   w.start_speed AS "startSpeed", w.end_speed AS "endSpeed",
                   w.warn_continue_mark AS "warnContinueMark",
                   COALESCE(w.handle_status, 0) AS "handleStatus",
                   w.handle_result_code AS "handleResultCode",
                   w.handle_result_msg AS "handleResultMsg",
                   w.handler, w.update_date AS "updateDate"
              FROM traj.traj_warn_info w
              LEFT JOIN traj.base_warn_type t ON t.id = w.type_id
             <where>
               <if test="plateNo != null and plateNo != ''">
                 AND w.plate_no LIKE '%' || #{plateNo} || '%'
               </if>
               <if test="typeId != null">
                 AND w.type_id = #{typeId}
               </if>
               <if test="handleStatus != null">
                 AND COALESCE(w.handle_status, 0) = #{handleStatus}
               </if>
               <if test="beginTime != null">
                 AND w.start_warn_time &gt;= #{beginTime}
               </if>
               <if test="endTime != null">
                 AND w.start_warn_time &lt; #{endTime}
               </if>
               <if test="plates != null">
                 AND w.plate_no IN
                 <foreach collection="plates" item="x" open="(" separator="," close=")">#{x}</foreach>
               </if>
             </where>
             ORDER BY w.start_warn_time DESC NULLS LAST, w.id DESC
            </script>
            """)
    Page<AlarmVO> pageAlarms(Page<AlarmVO> page,
                             @Param("plateNo") String plateNo,
                             @Param("typeId") Integer typeId,
                             @Param("handleStatus") Integer handleStatus,
                             @Param("beginTime") LocalDateTime beginTime,
                             @Param("endTime") LocalDateTime endTime,
                             @Param("plates") java.util.Collection<String> plates);

    /**
     * F17 大屏/面板待处理滚动（§4.2）：只取 handle_status=0（COALESCE 归一）。
     * @param plates 可见车牌：null=不限制；空集合调用方提前返回
     */
    @Select("""
            <script>
            SELECT w.id, w.plate_no AS "plateNo", w.identity_code AS "identityCode",
                   w.type_id AS "typeId",
                   COALESCE(t.name, '类型 ' || w.type_id) AS "typeName",
                   t.grade_level AS "gradeLevel",
                   w.start_warn_time AS "startWarnTime", w.end_warn_time AS "endWarnTime",
                   w.start_lng AS "startLng", w.start_lat AS "startLat",
                   w.end_lng AS "endLng", w.end_lat AS "endLat",
                   w.start_speed AS "startSpeed", w.end_speed AS "endSpeed",
                   w.warn_continue_mark AS "warnContinueMark",
                   COALESCE(w.handle_status, 0) AS "handleStatus",
                   w.handle_result_code AS "handleResultCode",
                   w.handle_result_msg AS "handleResultMsg",
                   w.handler, w.update_date AS "updateDate"
              FROM traj.traj_warn_info w
              LEFT JOIN traj.base_warn_type t ON t.id = w.type_id
             WHERE COALESCE(w.handle_status, 0) = 0
            <if test="plates != null">
              AND w.plate_no IN
              <foreach collection="plates" item="x" open="(" separator="," close=")">#{x}</foreach>
            </if>
             ORDER BY w.start_warn_time DESC NULLS LAST, w.id DESC
             LIMIT #{limit}
            </script>
            """)
    List<AlarmVO> selectLatestPending(@Param("limit") int limit,
                                      @Param("plates") java.util.Collection<String> plates);

    /** 详情单条（§4.5）：含 source_id、rule_id、create_date 等展示列，不存在返回 null */
    @Select("""
            SELECT w.id, w.plate_no AS "plateNo", w.identity_code AS "identityCode",
                   w.type_id AS "typeId",
                   COALESCE(t.name, '类型 ' || w.type_id) AS "typeName",
                   t.grade_level AS "gradeLevel",
                   w.start_warn_time AS "startWarnTime", w.end_warn_time AS "endWarnTime",
                   w.start_lng AS "startLng", w.start_lat AS "startLat",
                   w.end_lng AS "endLng", w.end_lat AS "endLat",
                   w.start_speed AS "startSpeed", w.end_speed AS "endSpeed",
                   w.warn_continue_mark AS "warnContinueMark",
                   COALESCE(w.handle_status, 0) AS "handleStatus",
                   w.handle_result_code AS "handleResultCode",
                   w.handle_result_msg AS "handleResultMsg",
                   w.handler, w.update_date AS "updateDate"
              FROM traj.traj_warn_info w
              LEFT JOIN traj.base_warn_type t ON t.id = w.type_id
             WHERE w.id = #{id}
            """)
    AlarmVO selectDetail(@Param("id") Long id);

    /**
     * 按类型统计（§4.3）：时间窗口 [begin,end)，JOIN 名称/等级。
     * @param plates 可见车牌：null=不限制；空集合调用方提前返回
     */
    @Select("""
            <script>
            SELECT w.type_id AS "typeId",
                   COALESCE(t.name, '类型 ' || w.type_id) AS "typeName",
                   t.grade_level AS "gradeLevel",
                   count(*) AS "count"
              FROM traj.traj_warn_info w
              LEFT JOIN traj.base_warn_type t ON t.id = w.type_id
             WHERE w.start_warn_time &gt;= #{begin} AND w.start_warn_time &lt; #{end}
            <if test="plates != null">
              AND w.plate_no IN
              <foreach collection="plates" item="x" open="(" separator="," close=")">#{x}</foreach>
            </if>
             GROUP BY w.type_id, t.name, t.grade_level
             ORDER BY count(*) DESC
            </script>
            """)
    List<Map<String, Object>> countByType(@Param("begin") LocalDateTime begin,
                                          @Param("end") LocalDateTime end,
                                          @Param("plates") java.util.Collection<String> plates);

    /**
     * 按状态统计（§4.3）：COALESCE 归一。
     * @param plates 可见车牌：null=不限制；空集合调用方提前返回
     */
    @Select("""
            <script>
            SELECT COALESCE(w.handle_status, 0) AS "handleStatus", count(*) AS "count"
              FROM traj.traj_warn_info w
             WHERE w.start_warn_time &gt;= #{begin} AND w.start_warn_time &lt; #{end}
            <if test="plates != null">
              AND w.plate_no IN
              <foreach collection="plates" item="x" open="(" separator="," close=")">#{x}</foreach>
            </if>
             GROUP BY COALESCE(w.handle_status, 0)
             ORDER BY 1
            </script>
            """)
    List<Map<String, Object>> countByStatus(@Param("begin") LocalDateTime begin,
                                            @Param("end") LocalDateTime end,
                                            @Param("plates") java.util.Collection<String> plates);

    /**
     * 窗口内总数（§4.3）。
     * @param plates 可见车牌：null=不限制；空集合调用方提前返回 0
     */
    @Select("""
            <script>
            SELECT count(*) FROM traj.traj_warn_info
             WHERE start_warn_time &gt;= #{begin} AND start_warn_time &lt; #{end}
            <if test="plates != null">
              AND plate_no IN
              <foreach collection="plates" item="x" open="(" separator="," close=")">#{x}</foreach>
            </if>
            </script>
            """)
    long countInRange(@Param("begin") LocalDateTime begin, @Param("end") LocalDateTime end,
                      @Param("plates") java.util.Collection<String> plates);

    /** 报警类型下拉（§4.5）：base_warn_type 有效行 */
    @Select("""
            SELECT id AS "typeId", name AS "typeName", grade_level AS "gradeLevel"
              FROM traj.base_warn_type
             WHERE valid_mark = 1
             ORDER BY id
            """)
    List<Map<String, Object>> selectAlarmTypes();

    /** 确认（§3.2）：0→1 条件更新，返回影响行数（0=冲突） */
    @Update("""
            UPDATE traj.traj_warn_info
               SET handle_status = 1, handler = #{name},
                   updater = #{name}, update_date = now()
             WHERE id = #{id} AND COALESCE(handle_status, 0) = 0
            """)
    int markConfirmed(@Param("id") Long id, @Param("name") String name);

    /** 解除（§3.2）：0/1→2 条件更新，返回影响行数（0=冲突） */
    @Update("""
            UPDATE traj.traj_warn_info
               SET handle_status = 2, handler = #{name},
                   handle_result_code = #{code}, handle_result_msg = #{msg},
                   updater = #{name}, update_date = now()
             WHERE id = #{id} AND COALESCE(handle_status, 0) IN (0, 1)
            """)
    int markResolved(@Param("id") Long id, @Param("name") String name,
                     @Param("code") String code, @Param("msg") String msg);
}
