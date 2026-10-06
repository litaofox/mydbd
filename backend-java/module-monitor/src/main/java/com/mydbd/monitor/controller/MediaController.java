package com.mydbd.monitor.controller;

import com.mydbd.common.api.ErrorCode;
import com.mydbd.common.exception.BizException;
import com.mydbd.common.security.RequiresPerm;
import com.mydbd.monitor.gateway.ProcessingClient;
import com.mydbd.monitor.mapper.WarnMediaMapper;
import com.mydbd.monitor.service.AlarmService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 网关报警附件中转下载（GATEWAY-PLAN-001）。
 * 权限与报警查看同码；归属校验复用报警详情的 canSee(plateNo) 口径，不新增放行。
 * 前端直接引用 GET /api/monitor/alarm/media/{id} 展示图片。
 */
@RestController
@RequestMapping("/api/monitor/alarm/media")
@RequiredArgsConstructor
@RequiresPerm("alarm:view")
public class MediaController {

    private final WarnMediaMapper warnMediaMapper;
    private final AlarmService alarmService;
    private final ProcessingClient processingClient;

    /** 附件字节流中转：归属校验 → 服务令牌调 processing → 流式透传 */
    @GetMapping("/{id}")
    public void download(@PathVariable Long id, HttpServletResponse response) {
        String plateNo = warnMediaMapper.selectPlateNoByMediaId(id);
        if (plateNo == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "附件不存在");
        }
        if (!alarmService.canSee(plateNo)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权查看该附件");
        }
        processingClient.stream("/api/gateway/media/" + id, response);
    }
}
