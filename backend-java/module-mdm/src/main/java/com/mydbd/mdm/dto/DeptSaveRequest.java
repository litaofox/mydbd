package com.mydbd.mdm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 组织新增/编辑请求（parentId 为空或 0 表示根组织）
 */
public record DeptSaveRequest(
        Long parentId,

        @NotBlank(message = "不能为空")
        @Size(max = 60, message = "长度不能超过60")
        String deptName,

        @Size(max = 40, message = "长度不能超过40")
        String deptCode,

        @NotNull(message = "不能为空")
        Integer deptType,

        @Size(max = 30)
        String contactPerson,

        @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "格式不正确")
        String contactPhone,

        String provinceCode,
        String cityCode,
        String countyCode,

        @Size(max = 120)
        String address,

        Integer sortNo) {
}
