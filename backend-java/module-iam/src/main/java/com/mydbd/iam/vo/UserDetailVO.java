package com.mydbd.iam.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户详情（不含密码哈希/密钥；含角色ID集合；雪花 ID 字符串输出）
 */
@Data
public class UserDetailVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String username;
    private String realName;
    private String phone;
    private String email;
    private Long deptId;
    private String deptName;
    private Integer status;
    private Integer mfaEnabled;
    private String remark;
    private LocalDateTime pwdUpdateTime;
    private LocalDateTime lastLoginTime;
    private String lastLoginIp;
    private LocalDateTime createDate;


    @JsonSerialize(contentUsing = ToStringSerializer.class)
    private List<Long> roleIds;
    private List<String> roleCodes;
}
