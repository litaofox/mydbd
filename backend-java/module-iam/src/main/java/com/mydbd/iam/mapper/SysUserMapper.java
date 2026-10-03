package com.mydbd.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mydbd.iam.entity.SysUser;
import com.mydbd.iam.vo.UserDetailVO;
import com.mydbd.iam.vo.UserListVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户 Mapper：基础 CRUD + 联表分页/详情
 */
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("""
            <script>
            SELECT u.id, u.username, u.real_name AS "realName", u.phone, u.email,
                   u.dept_id AS "deptId", d.dept_name AS "deptName",
                   u.status, u.mfa_enabled AS "mfaEnabled",
                   u.fail_count AS "failCount", u.locked_until AS "lockedUntil",
                   u.last_login_time AS "lastLoginTime", u.remark,
                   u.create_date AS "createDate",
                   (SELECT string_agg(r.role_name, ',')
                      FROM traj.sys_user_role ur
                      JOIN traj.sys_role r ON r.id = ur.role_id AND r.valid_mark = 1
                     WHERE ur.user_id = u.id) AS "roleNames"
              FROM traj.sys_user u
              LEFT JOIN traj.traj_dept d ON d.id = u.dept_id
             WHERE u.valid_mark = 1
              <if test="username != null and username != ''">
                  AND u.username LIKE CONCAT('%', #{username}, '%')
              </if>
              <if test="realName != null and realName != ''">
                  AND u.real_name LIKE CONCAT('%', #{realName}, '%')
              </if>
              <if test="status != null">AND u.status = #{status}</if>
              <if test="deptId != null">AND u.dept_id = #{deptId}</if>
              <if test="roleId != null">
                  AND EXISTS (SELECT 1 FROM traj.sys_user_role ur2
                               WHERE ur2.user_id = u.id AND ur2.role_id = #{roleId})
              </if>
             ORDER BY u.id
            </script>
            """)
    Page<UserListVO> selectPageVo(Page<?> page,
                                  @Param("username") String username,
                                  @Param("realName") String realName,
                                  @Param("status") Integer status,
                                  @Param("deptId") Long deptId,
                                  @Param("roleId") Long roleId);

    @Select("""
            SELECT u.id, u.username, u.real_name AS "realName", u.phone, u.email,
                   u.dept_id AS "deptId", d.dept_name AS "deptName",
                   u.status, u.mfa_enabled AS "mfaEnabled", u.remark,
                   u.pwd_update_time AS "pwdUpdateTime",
                   u.last_login_time AS "lastLoginTime", u.last_login_ip AS "lastLoginIp",
                   u.create_date AS "createDate"
              FROM traj.sys_user u
              LEFT JOIN traj.traj_dept d ON d.id = u.dept_id
             WHERE u.id = #{id} AND u.valid_mark = 1
            """)
    UserDetailVO selectDetailVo(@Param("id") Long id);
}
