package com.mydbd.mdm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 车辆新增/编辑请求
 */
public record VehicleSaveRequest(
        Long deptId,

        @NotBlank(message = "不能为空")
        @Size(max = 40)
        String vehicleNo,

        @NotBlank(message = "不能为空")
        String vehiclePlateColor,

        @Pattern(regexp = "^$|^[A-Za-z0-9]{17}$", message = "应为17位字母数字")
        String vin,

        @Size(max = 40)
        String vehicleType,

        Integer operationType,

        @Size(max = 40)
        String vehicleIndustry,

        @Size(max = 64)
        String roadLicenseNo,

        String provinceCode,
        String cityCode,
        String countyCode,

        @Size(max = 10)
        String vehicleColor,

        @Size(max = 20)
        String vehicleBrand,

        @Size(max = 20)
        String ownerName,

        @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "格式不正确")
        String ownerPhone,

        @Size(max = 100)
        String remark) {
}
