package com.mydbd.mdm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 终端新增/编辑请求
 */
public record TerminalSaveRequest(
        @NotBlank(message = "不能为空")
        @Size(max = 100)
        String identityCode,

        @Size(max = 16)
        String tlMac,

        @Size(max = 40)
        String oemCode,

        @Size(max = 64)
        String tlModel,

        @Size(max = 32)
        String simAccount,

        /** vps 网关车辆主键 truckId（可空） */
        Long gatewayTruckId,

        @Size(max = 8)
        String protocolType,

        @Size(max = 8)
        String equipmentType,

        Integer videoChannel,

        @NotNull(message = "不能为空")
        Integer status,

        @Size(max = 100)
        String remark) {
}
