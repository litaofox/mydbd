package com.mydbd.mdm.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 绑定相关请求体
 */
public class BindRequests {

    /** 车辆绑定终端（已绑定时按"换绑"处理） */
    public record BindTerminalRequest(
            @NotNull(message = "不能为空")
            Long terminalId,

            Integer bindType,

            @Size(max = 40)
            String installer,

            @Size(max = 255)
            String remark) {
    }

    /** 车辆解绑终端 */
    public record UnbindTerminalRequest(
            @Size(max = 255)
            String remark) {
    }

    /** 车辆绑定司机 */
    public record BindDriverRequest(
            @NotNull(message = "不能为空")
            Long driverId,

            Integer driverType,

            @Size(max = 100)
            String remark) {
    }

    /** 车辆解绑司机 */
    public record UnbindDriverRequest(
            @NotNull(message = "不能为空")
            Long driverId,

            @Size(max = 100)
            String remark) {
    }
}
