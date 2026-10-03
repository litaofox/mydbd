package com.mydbd.mdm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 驾驶员新增/编辑请求
 */
public record DriverSaveRequest(
        @NotBlank(message = "不能为空")
        @Size(max = 40)
        String driverName,

        @NotNull(message = "不能为空")
        Integer sex,

        @Size(max = 30)
        String idcard,

        @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "格式不正确")
        String contactPhone,

        @NotBlank(message = "不能为空")
        @Size(max = 40)
        String licenseCode,

        @Size(max = 32)
        String licenceCategory,

        @Size(max = 128)
        String driverImg,

        @NotNull(message = "不能为空")
        Integer status,

        @Size(max = 100)
        String remark) {
}
