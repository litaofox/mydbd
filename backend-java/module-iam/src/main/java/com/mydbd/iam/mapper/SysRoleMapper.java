package com.mydbd.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mydbd.iam.entity.SysRole;
import com.mydbd.iam.vo.RoleListVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色 Mapper
 */
public interface SysRoleMapper extends BaseMapper<SysRole> {

    @Select("""
            <script>
            SELECT r.id, r.role_code AS "roleCode", r.role_name AS "roleName",
                   r.data_scope AS "dataScope", r.built_in AS "builtIn",
                   r.status, r.remark, r.create_date AS "createDate",
                   (SELECT COUNT(1) FROM traj.sys_user_role ur
                      JOIN traj.sys_user u ON u.id = ur.user_id AND u.valid_mark = 1
                     WHERE ur.role_id = r.id) AS "userCount"
              FROM traj.sys_role r
             WHERE r.valid_mark = 1
              <if test="keyword != null and keyword != ''">
                  AND (r.role_name LIKE CONCAT('%', #{keyword}, '%')
                       OR r.role_code LIKE CONCAT('%', #{keyword}, '%'))
              </if>
              <if test="status != null">AND r.status = #{status}</if>
             ORDER BY r.id
            </script>
            """)
    Page<RoleListVO> selectPageVo(Page<?> page,
                                  @Param("keyword") String keyword,
                                  @Param("status") Integer status);

    @Select("""
            SELECT r.role_code
              FROM traj.sys_user_role ur
              JOIN traj.sys_role r ON r.id = ur.role_id
             WHERE ur.user_id = #{userId} AND r.valid_mark = 1 AND r.status = 1
            """)
    List<String> selectActiveCodesByUserId(@Param("userId") Long userId);
}
