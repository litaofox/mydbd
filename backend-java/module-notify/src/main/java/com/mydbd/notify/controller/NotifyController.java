package com.mydbd.notify.controller;

import com.mydbd.common.api.PageData;
import com.mydbd.common.api.Result;
import com.mydbd.common.security.UserContext;
import com.mydbd.common.security.UserInfo;
import com.mydbd.notify.service.NotifyService;
import com.mydbd.notify.vo.MessageVO;
import com.mydbd.notify.vo.NotifySettingsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * F19 站内消息中心接口（所有查询强制按当前登录人隔离，无越权入口）
 */
@RestController
@RequestMapping("/api/notify")
@RequiredArgsConstructor
public class NotifyController {

    private final NotifyService notifyService;

    @GetMapping("/messages")
    public Result<PageData<MessageVO>> messages(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        return Result.ok(notifyService.pageMine(currentUserId(), page, Math.min(size, 100), unreadOnly));
    }

    /** 铃铛下拉：最近消息（默认 10 条） */
    @GetMapping("/latest")
    public Result<List<MessageVO>> latest(@RequestParam(defaultValue = "10") int limit) {
        return Result.ok(notifyService.latestMine(currentUserId(), limit));
    }

    @GetMapping("/unread-count")
    public Result<Map<String, Long>> unreadCount() {
        return Result.ok(Map.of("count", notifyService.unreadCount(currentUserId())));
    }

    @PostMapping("/messages/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        notifyService.markRead(currentUserId(), id);
        return Result.ok();
    }

    @PostMapping("/messages/read-all")
    public Result<Map<String, Integer>> readAll() {
        return Result.ok(Map.of("updated", notifyService.readAll(currentUserId())));
    }

    @DeleteMapping("/messages/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        notifyService.deleteMine(currentUserId(), id);
        return Result.ok();
    }

    /** 坐席端通知设置（弹窗阈值/提示音） */
    @GetMapping("/settings")
    public Result<NotifySettingsVO> settings() {
        return Result.ok(notifyService.settings());
    }

    private Long currentUserId() {
        UserInfo user = UserContext.get();
        if (user == null) {
            throw new com.mydbd.common.exception.BizException(
                    com.mydbd.common.api.ErrorCode.UNAUTHORIZED, "未登录");
        }
        return user.userId();
    }
}
