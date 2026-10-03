package com.mydbd.iam.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * 角色下拉选项（id 字符串输出）
 */
public record OptionRoleVO(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String roleCode,
        String roleName,
        Integer dataScope) {
}
