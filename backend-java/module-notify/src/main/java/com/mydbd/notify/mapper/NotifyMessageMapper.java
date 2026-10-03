package com.mydbd.notify.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mydbd.notify.entity.NotifyMessage;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * F19 站内消息 Mapper
 */
public interface NotifyMessageMapper extends BaseMapper<NotifyMessage> {

    /** 未读数 */
    @Select("SELECT count(*) FROM traj.sys_message WHERE user_id = #{userId} AND is_read = 0")
    long countUnread(Long userId);

    /**
     * 持指定权限点（菜单 id）的启用用户 id 列表；role 1（超级管理员）隐式包含。
     * 与 module-risk 可分派用户口径一致（915 处置 / 916 分派督办）。
     */
    @Select("""
            SELECT DISTINCT u.id
            FROM traj.sys_user u
            JOIN traj.sys_user_role ur ON ur.user_id = u.id
            JOIN traj.sys_role r ON r.id = ur.role_id AND r.status = 1 AND r.valid_mark = 1
            WHERE u.valid_mark = 1 AND u.status = 1
              AND (
                r.id = 1
                OR EXISTS (SELECT 1 FROM traj.sys_role_menu rm
                           WHERE rm.role_id = r.id AND rm.menu_id = #{menuId})
              )
            """)
    List<Long> selectUserIdsByMenu(Long menuId);
}
