package com.mydbd.risk.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * F20 处置工单请求体
 */
public class OrderRequests {

    /** 分派（主管） */
    public record AssignRequest(
            @NotNull(message = "负责人不能为空")
            Long userId,
            @Size(max = 255, message = "分派备注最长 255 字")
            String remark) {
    }

    /** 转派（坐席） */
    public record TransferRequest(
            @NotNull(message = "新负责人不能为空")
            Long userId,
            @NotBlank(message = "转派原因不能为空")
            @Size(min = 5, max = 255, message = "转派原因需 5~255 字")
            String remark) {
    }

    /** 闭环 */
    public record CloseRequest(
            @NotBlank(message = "处置结果不能为空")
            String result,
            @NotBlank(message = "闭环说明不能为空")
            @Size(min = 10, max = 255, message = "闭环说明需 10~255 字")
            String remark) {
    }

    /** 重新打开（主管） */
    public record ReopenRequest(
            @NotBlank(message = "重开原因不能为空")
            @Size(min = 5, max = 255, message = "重开原因需 5~255 字")
            String remark) {
    }

    /** SLA 配置项 */
    public record SlaItem(
            @NotNull @Min(1) @Max(3)
            Integer riskLevel,
            @NotNull(message = "处置时限不能为空")
            @Min(value = 1, message = "处置时限至少 1 分钟")
            @Max(value = 10080, message = "处置时限最长 7 天")
            Integer limitMin,
            @NotNull(message = "升级宽限不能为空")
            @Min(value = 0, message = "升级宽限非法")
            @Max(value = 1440, message = "升级宽限最长 1 天")
            Integer graceMin) {
    }

    public record SlaUpdateRequest(
            @NotNull(message = "SLA 配置不能为空")
            List<@Valid SlaItem> items) {
    }
}
