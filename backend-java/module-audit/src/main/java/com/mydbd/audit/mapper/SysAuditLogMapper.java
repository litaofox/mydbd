package com.mydbd.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mydbd.audit.entity.SysAuditLog;
import com.mydbd.audit.vo.NameCount;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计日志 Mapper：只追加，无 update/delete 方法
 */
public interface SysAuditLogMapper extends BaseMapper<SysAuditLog> {

    @Select("SELECT action AS name, COUNT(*) AS \"count\" FROM traj.sys_audit_log "
            + "WHERE create_time >= #{startTime} AND create_time <= #{endTime} "
            + "GROUP BY action ORDER BY \"count\" DESC")
    List<NameCount> actionDist(@Param("startTime") LocalDateTime startTime,
                               @Param("endTime") LocalDateTime endTime);

    @Select("SELECT user_name AS name, COUNT(*) AS \"count\" FROM traj.sys_audit_log "
            + "WHERE create_time >= #{startTime} AND create_time <= #{endTime} "
            + "AND user_name IS NOT NULL "
            + "GROUP BY user_name ORDER BY \"count\" DESC LIMIT 10")
    List<NameCount> topUsers(@Param("startTime") LocalDateTime startTime,
                             @Param("endTime") LocalDateTime endTime);
}
