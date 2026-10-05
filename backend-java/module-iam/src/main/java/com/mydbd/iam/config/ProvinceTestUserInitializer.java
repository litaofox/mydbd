package com.mydbd.iam.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mydbd.iam.entity.SysRole;
import com.mydbd.iam.entity.SysUser;
import com.mydbd.iam.mapper.ProvDeptMapper;
import com.mydbd.iam.mapper.SysRoleMapper;
import com.mydbd.iam.mapper.SysUserMapper;
import com.mydbd.iam.mapper.SysUserRoleMapper;
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
 * 省份测试用户引导：为 31 个大陆省级行政区各创建 2 个用户（共 62 个）。
 * <ul>
 *   <li>{@code <拼音>_disp}：调度监控员 DISPATCHER（数据范围=本部门及以下）</li>
 *   <li>{@code <拼音>_capt}：企业车队长 FLEET_CAPTAIN（数据范围=本企业及以下）</li>
 * </ul>
 * 用户挂到 16-province-test-data.sql 创建的省级企业节点（dept_code=PROV_&lt;省码&gt;）。
 * 幂等：用户已存在则跳过。
 */
@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class ProvinceTestUserInitializer implements ApplicationRunner {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final ProvDeptMapper provDeptMapper;
    private final PasswordCodec passwordCodec;

    @Value("${mydbd.bootstrap.test-password:test123456}")
    private String testPassword;

    /** 省码 / 省名 / 用户名拼音前缀 */
    private static final List<String[]> PROVINCES = List.of(
            new String[]{"110000", "北京市", "beijing"},
            new String[]{"120000", "天津市", "tianjin"},
            new String[]{"130000", "河北省", "hebei"},
            new String[]{"140000", "山西省", "shanxi"},
            new String[]{"150000", "内蒙古自治区", "neimenggu"},
            new String[]{"210000", "辽宁省", "liaoning"},
            new String[]{"220000", "吉林省", "jilin"},
            new String[]{"230000", "黑龙江省", "heilongjiang"},
            new String[]{"310000", "上海市", "shanghai"},
            new String[]{"320000", "江苏省", "jiangsu"},
            new String[]{"330000", "浙江省", "zhejiang"},
            new String[]{"340000", "安徽省", "anhui"},
            new String[]{"350000", "福建省", "fujian"},
            new String[]{"360000", "江西省", "jiangxi"},
            new String[]{"370000", "山东省", "shandong"},
            new String[]{"410000", "河南省", "henan"},
            new String[]{"420000", "湖北省", "hubei"},
            new String[]{"430000", "湖南省", "hunan"},
            new String[]{"440000", "广东省", "guangdong"},
            new String[]{"450000", "广西壮族自治区", "guangxi"},
            new String[]{"460000", "海南省", "hainan"},
            new String[]{"500000", "重庆市", "chongqing"},
            new String[]{"510000", "四川省", "sichuan"},
            new String[]{"520000", "贵州省", "guizhou"},
            new String[]{"530000", "云南省", "yunnan"},
            new String[]{"540000", "西藏自治区", "xizang"},
            new String[]{"610000", "陕西省", "shaanxi"},
            new String[]{"620000", "甘肃省", "gansu"},
            new String[]{"630000", "青海省", "qinghai"},
            new String[]{"640000", "宁夏回族自治区", "ningxia"},
            new String[]{"650000", "新疆维吾尔自治区", "xinjiang"}
    );

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        SysRole dispatcher = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, "DISPATCHER")
                .eq(SysRole::getValidMark, 1).last("limit 1"));
        SysRole captain = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, "FLEET_CAPTAIN")
                .eq(SysRole::getValidMark, 1).last("limit 1"));
        if (dispatcher == null || captain == null) {
            log.warn("内置角色 DISPATCHER/FLEET_CAPTAIN 缺失，跳过省份测试用户引导");
            return;
        }
        int created = 0;
        for (String[] province : PROVINCES) {
            String code = province[0];
            String name = province[1];
            String pinyin = province[2];
            Long deptId = provDeptMapper.selectIdByCode("PROV_" + code);
            if (deptId == null) {
                log.warn("省级部门 PROV_{} 缺失（请确认 16-province-test-data.sql 已执行），跳过 {}", code, pinyin);
                continue;
            }
            if (createIfAbsent(pinyin + "_disp", name + "-调度测试员", deptId, dispatcher.getId())) {
                created++;
            }
            if (createIfAbsent(pinyin + "_capt", name + "-车队长测试员", deptId, captain.getId())) {
                created++;
            }
        }
        log.info("省份测试用户引导完成，新建 {} 个（初始密码来自配置 mydbd.bootstrap.test-password）", created);
    }

    private boolean createIfAbsent(String username, String realName, Long deptId, Long roleId) {
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .eq(SysUser::getValidMark, 1));
        if (exists > 0) {
            return false;
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordCodec.encode(testPassword));
        user.setRealName(realName);
        user.setDeptId(deptId);
        user.setStatus(1);
        user.setMfaEnabled(0);
        user.setFailCount(0);
        user.setPwdUpdateTime(LocalDateTime.now());
        user.setRemark("省份数据范围测试账号");
        user.setValidMark(1);
        userMapper.insert(user);
        userRoleMapper.insert(user.getId(), roleId);
        return true;
    }
}
