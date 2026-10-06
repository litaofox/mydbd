package com.mydbd.monitor.mapper;

import com.mydbd.monitor.vo.AlarmVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 网关报警附件（traj.traj_warn_media，GATEWAY-PLAN-001）：
 * 全 @Select 注解 SQL，同库跨 schema 只读直查 traj.*，零跨模块 import。
 * 关联锚点：traj_warn_media.warn_id = traj_warn_info.source_id（网关 warnId）。
 */
public interface WarnMediaMapper {

    /** 按报警主键取附件列表（warn_info.source_id 关联；无附件返回空列表） */
    @Select("""
            SELECT m.id, m.file_name AS "fileName", m.file_type AS "fileType",
                   m.file_size AS "fileSize", m.file_status AS "fileStatus",
                   (m.local_path IS NOT NULL AND m.local_path <> '') AS "hasLocal"
              FROM traj.traj_warn_media m
             WHERE m.warn_id = (SELECT w.source_id FROM traj.traj_warn_info w WHERE w.id = #{id})
             ORDER BY m.id
            """)
    List<AlarmVO.MediaAttachment> selectByWarnInfoId(@Param("id") Long id);

    /** 按附件主键取所属报警车牌（归属校验用；附件或报警不存在返回 null） */
    @Select("""
            SELECT w.plate_no
              FROM traj.traj_warn_media m
              JOIN traj.traj_warn_info w ON w.source_id = m.warn_id
             WHERE m.id = #{id}
             LIMIT 1
            """)
    String selectPlateNoByMediaId(@Param("id") Long id);
}
