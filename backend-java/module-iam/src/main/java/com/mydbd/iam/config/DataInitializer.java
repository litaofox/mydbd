package com.mydbd.iam.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mydbd.iam.entity.SysRole;
import com.mydbd.iam.entity.SysUser;
import com.mydbd.iam.mapper.SysRoleMapper;
import com.mydbd.iam.mapper.SysUserMapper;
import com.mydbd.iam.mapper.SysUserRoleMapper;
import com.mydbd.iam.service.IamConstants;
import com.mydbd.iam.service.PasswordCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 首启引导：确保内置超级管理员 admin 存在（密码不写死在 SQL 中）。
 */
@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordCodec passwordCodec;

    @Value("${mydbd.bootstrap.admin-password:admin123}")
    private String adminPassword;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        Long adminCount = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, IamConstants.ADMIN_USERNAME)
                .eq(SysUser::getValidMark, 1));
        if (adminCount > 0) {
            return;
        }
        SysRole superRole = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, IamConstants.SUPER_ADMIN_CODE)
                .eq(SysRole::getValidMark, 1)
                .last("limit 1"));
        if (superRole == null) {
            log.warn("超级管理员角色种子缺失，跳过 admin 引导（请确认 06-iam-tables.sql 已执行）");
            return;
        }
        SysUser admin = new SysUser();
        admin.setUsername(IamConstants.ADMIN_USERNAME);
        admin.setPasswordHash(passwordCodec.encode(adminPassword));
        admin.setRealName("超级管理员");
        admin.setStatus(1);
        admin.setMfaEnabled(0);
        admin.setFailCount(0);
        admin.setPwdUpdateTime(LocalDateTime.now());
        admin.setRemark("系统内置超级管理员");
        admin.setValidMark(1);
        userMapper.insert(admin);
        userRoleMapper.insert(admin.getId(), superRole.getId());
        log.info("内置超级管理员 admin 引导完成（id={}），初始密码来自配置 mydbd.bootstrap.admin-password", admin.getId());
    }
}
