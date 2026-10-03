package com.mydbd.iam.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色列表行（id 为雪花大数，字符串输出；内置角色为小号 id 也统一字符串）
 */
@Data
public class RoleListVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String roleCode;
    private String roleName;
    private Integer dataScope;
    private Integer builtIn;
    private Integer status;
    private String remark;
    private LocalDateTime createDate;
    private Long userCount;
}
