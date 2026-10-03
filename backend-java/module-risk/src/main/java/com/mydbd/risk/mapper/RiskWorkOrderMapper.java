package com.mydbd.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mydbd.risk.entity.RiskWorkOrder;
import com.mydbd.risk.vo.AssignableUserVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * F20 处置工单 Mapper
 */
public interface RiskWorkOrderMapper extends BaseMapper<RiskWorkOrder> {

    /**
     * 懒补建工单（order_no 由数据库函数生成；deadline 由 service 按 SLA 算好传入）。
     */
    @Insert("""
            INSERT INTO mon.risk_work_order
                (order_no, event_id, event_title, event_code, event_source, plate_no,
                 identity_code, risk_level, event_time, status, deadline,
                 sla_limit_min, grace_min, creator)
            VALUES (mon.fmt_order_no(), #{eventId}, #{eventTitle}, #{eventCode},
                    #{eventSource}, #{plateNo}, #{identityCode}, #{riskLevel},
                    #{eventTime}, 'PENDING', #{deadline}, #{slaLimitMin}, #{graceMin},
                    #{creator})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertWithNo(RiskWorkOrder order);

    /**
     * 可分派/有处置权限的启用用户：持 915（risk:order:handle）权限点的角色成员；
     * 角色 1（超级管理员）隐式包含。
     */
    @Select("""
            SELECT DISTINCT u.id AS id, u.real_name AS name, u.username AS username
            FROM traj.sys_user u
            JOIN traj.sys_user_role ur ON ur.user_id = u.id
            JOIN traj.sys_role r ON r.id = ur.role_id AND r.status = 1 AND r.valid_mark = 1
            WHERE u.valid_mark = 1 AND u.status = 1
              AND (
                r.id = 1
                OR EXISTS (SELECT 1 FROM traj.sys_role_menu rm
                           WHERE rm.role_id = r.id AND rm.menu_id = 915)
              )
            ORDER BY u.real_name
            """)
    List<AssignableUserVO> selectAssignableUsers();

    /**
     * 工单看板统计（实时口径：以 deadline 现算，不依赖扫描任务）。
     */
    @Select("""
            SELECT
              count(*) FILTER (WHERE status = 'PENDING' AND valid_mark = 1) AS pendingCount,
              count(*) FILTER (WHERE status = 'PROCESSING' AND valid_mark = 1) AS processingCount,
              count(*) FILTER (WHERE status <> 'CLOSED' AND valid_mark = 1
                               AND deadline IS NOT NULL AND deadline < CURRENT_TIMESTAMP) AS overdueCount,
              count(*) FILTER (WHERE status <> 'CLOSED' AND valid_mark = 1
                               AND deadline IS NOT NULL
                               AND deadline + COALESCE(grace_min,0) * interval '1 minute'
                                   < CURRENT_TIMESTAMP) AS escalatedCount,
              count(*) FILTER (WHERE status = 'CLOSED'
                               AND close_time >= CURRENT_DATE) AS closedTodayCount,
              COALESCE(EXTRACT(EPOCH FROM AVG(close_time - create_date)
                       FILTER (WHERE status = 'CLOSED' AND close_time >= CURRENT_DATE)), 0)
                  AS avgCloseSeconds
            FROM mon.risk_work_order
            """)
    Map<String, Object> selectStats();

    /** 物化超时标记（兜底扫描；列表/统计以实时计算为准） */
    @Update("""
            UPDATE mon.risk_work_order
            SET overdue = 1
            WHERE valid_mark = 1 AND status <> 'CLOSED' AND overdue = 0
              AND deadline IS NOT NULL AND deadline < CURRENT_TIMESTAMP
            """)
    int markOverdue();

    /** 物化升级标记 */
    @Update("""
            UPDATE mon.risk_work_order
            SET escalated = 1, escalate_time = CURRENT_TIMESTAMP
            WHERE valid_mark = 1 AND status <> 'CLOSED' AND escalated = 0
              AND deadline IS NOT NULL
              AND deadline + COALESCE(grace_min,0) * interval '1 minute' < CURRENT_TIMESTAMP
            """)
    int markEscalated();

    /** 为本轮新升级的工单补 ESCALATE 系统日志 */
    @Update("""
            INSERT INTO mon.risk_order_log
                (order_id, action, to_status, operator_id, operator_name, remark, create_date)
            SELECT w.id, 'ESCALATE', w.status, NULL, 'SYSTEM',
                   '超过处置时限与升级宽限期，自动升级督办', CURRENT_TIMESTAMP
            FROM mon.risk_work_order w
            WHERE w.escalated = 1 AND w.escalate_time >= CURRENT_TIMESTAMP - interval '2 minute'
              AND NOT EXISTS (
                  SELECT 1 FROM mon.risk_order_log l
                  WHERE l.order_id = w.id AND l.action = 'ESCALATE'
              )
            """)
    int insertEscalateLogs();

    /**
     * 本轮新升级、尚未补 ESCALATE 日志的工单（用于 F19 升级督办通知，单轮上限 200）。
     */
    @Select("""
            SELECT w.*
            FROM mon.risk_work_order w
            WHERE w.escalated = 1 AND w.escalate_time >= CURRENT_TIMESTAMP - interval '2 minute'
              AND NOT EXISTS (
                  SELECT 1 FROM mon.risk_order_log l
                  WHERE l.order_id = w.id AND l.action = 'ESCALATE'
              )
            ORDER BY w.risk_level DESC, w.escalate_time
            LIMIT 200
            """)
    List<RiskWorkOrder> selectNewlyEscalated();

    /** 当前最大工单 id（F19 新建通知游标初始化，避免重启回放历史） */
    @Select("SELECT COALESCE(MAX(id), 0) FROM mon.risk_work_order")
    long selectMaxId();

    /** id 大于游标的新工单（F19 建单通知扫描，单批上限 limit） */
    @Select("SELECT * FROM mon.risk_work_order WHERE id > #{lastId} ORDER BY id ASC LIMIT #{limit}")
    List<RiskWorkOrder> selectCreatedAfter(@Param("lastId") Long lastId, @Param("limit") int limit);
}
