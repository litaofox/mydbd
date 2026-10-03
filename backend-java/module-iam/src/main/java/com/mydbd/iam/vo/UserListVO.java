package com.mydbd.iam.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户列表行（不含任何密码/密钥字段；id 为雪花大数，字符串输出）
 */
@Data
public class UserListVO {

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
    private Integer failCount;
    private LocalDateTime lockedUntil;
    private LocalDateTime lastLoginTime;
    private String remark;
    private LocalDateTime createDate;
    private String roleNames;

    /** service 根据 lockedUntil 派生 */
    private Boolean locked;
}
