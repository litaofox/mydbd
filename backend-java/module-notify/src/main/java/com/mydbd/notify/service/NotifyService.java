package com.mydbd.notify.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mydbd.common.api.ErrorCode;
import com.mydbd.common.api.PageData;
import com.mydbd.common.exception.BizException;
import com.mydbd.notify.api.NotifyEvent;
import com.mydbd.notify.api.RealtimePusher;
import com.mydbd.notify.channel.NotifyChannel;
import com.mydbd.notify.entity.NotifyMessage;
import com.mydbd.notify.entity.NotifySendLog;
import com.mydbd.notify.mapper.NotifyMessageMapper;
import com.mydbd.notify.mapper.NotifySendLogMapper;
import com.mydbd.notify.vo.MessageVO;
import com.mydbd.notify.vo.NotifySettingsVO;
import com.mydbd.system.service.ConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * F19 通知派发服务：站内消息落库 + WebSocket 定向推送 + 外部渠道预留。
 * 异步执行且全程吞异常，保证不影响业务主事务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyService {

    /** 坐席（持工单处置权限 915） */
    public static final Long MENU_OPERATOR = 915L;
    /** 主管（持分派/督办权限 916） */
    public static final Long MENU_SUPERVISOR = 916L;

    private final NotifyMessageMapper messageMapper;
    private final NotifySendLogMapper sendLogMapper;
    private final ConfigService configService;
    private final List<NotifyChannel> channels;
    /** module-monitor 提供实现；单独启动 notify 时可为空 */
    private final ObjectProvider<RealtimePusher> pusherProvider;

    /** 按角色菜单解析接收人 */
    public List<Long> receiversByMenu(Long menuId) {
        return messageMapper.selectUserIdsByMenu(menuId);
    }

    @Async("notifyExecutor")
    public void dispatchAsync(NotifyEvent event, List<Long> receiverIds) {
        try {
            dispatch(event, receiverIds);
        } catch (Exception e) {
            log.warn("notify dispatch failed, event={}, bizId={}", event.eventType(), event.bizId(), e);
        }
    }

    /** 同步派发（供测试与内部调用） */
    public void dispatch(NotifyEvent event, List<Long> receiverIds) {
        if (event == null || receiverIds == null || receiverIds.isEmpty()) {
            return;
        }
        Set<Long> receivers = new LinkedHashSet<>(receiverIds);
        List<NotifyMessage> saved = new ArrayList<>();
        for (Long uid : receivers) {
            if (uid == null) {
                continue;
            }
            NotifyMessage msg = new NotifyMessage();
            msg.setUserId(uid);
            msg.setTitle(event.title());
            msg.setContent(event.content());
            msg.setMsgType("INBOX");
            msg.setIsRead(0);
            msg.setBizType(event.bizType());
            msg.setBizId(event.bizId());
            msg.setLevel(event.level());
            msg.setEventType(event.eventType());
            msg.setCreator("system");
            msg.setCreateDate(LocalDateTime.now());
            try {
                messageMapper.insert(msg);
                saved.add(msg);
            } catch (Exception e) {
                log.warn("insert sys_message failed, user={}, event={}", uid, event.eventType(), e);
            }
        }

        pushWebsocket(event, saved, receivers);
        dispatchExternalChannels(event, receivers);
    }

    private void pushWebsocket(NotifyEvent event, List<NotifyMessage> saved, Set<Long> receivers) {
        RealtimePusher pusher = pusherProvider.getIfAvailable();
        if (pusher == null) {
            return;
        }
        for (NotifyMessage msg : saved) {
            Map<String, Object> payload = Map.of(
                    "id", String.valueOf(msg.getId()),
                    "title", nullToEmpty(msg.getTitle()),
                    "content", nullToEmpty(msg.getContent()),
                    "level", msg.getLevel(),
                    "eventType", nullToEmpty(msg.getEventType()),
                    "bizType", nullToEmpty(msg.getBizType()),
                    "bizId", msg.getBizId() == null ? "" : String.valueOf(msg.getBizId()),
                    "orderNo", nullToEmpty(event.orderNo()),
                    "plateNo", nullToEmpty(event.plateNo()),
                    "createDate", msg.getCreateDate() == null ? "" : msg.getCreateDate().toString());
            int delivered = 0;
            try {
                delivered = pusher.sendToUsers(List.of(msg.getUserId()), "NOTIFY", payload);
            } catch (Exception e) {
                log.warn("ws notify push failed, user={}", msg.getUserId(), e);
            }
            writeLog(event, "WEBSOCKET", msg.getUserId(), delivered > 0 ? "SENT" : "SKIPPED",
                    delivered > 0 ? "在线会话 " + delivered : "接收人不在线");
        }
    }

    private void dispatchExternalChannels(NotifyEvent event, Set<Long> receivers) {
        for (NotifyChannel channel : channels) {
            if (!channel.enabled()) {
                writeLog(event, channel.name(), null, "SKIPPED", "渠道未启用");
                continue;
            }
            try {
                channel.send(event, new ArrayList<>(receivers));
                writeLog(event, channel.name(), null, "SENT", null);
            } catch (Exception e) {
                writeLog(event, channel.name(), null, "FAILED", trim(e.getMessage()));
                log.warn("channel {} send failed, event={}", channel.name(), event.eventType(), e);
            }
        }
    }

    private void writeLog(NotifyEvent event, String channel, Long receiverId,
                          String status, String detail) {
        try {
            NotifySendLog row = new NotifySendLog();
            row.setEventType(event.eventType());
            row.setBizType(event.bizType());
            row.setBizId(event.bizId());
            row.setChannel(channel);
            row.setReceiverId(receiverId);
            row.setTitle(event.title());
            row.setLevel(event.level());
            row.setStatus(status);
            row.setDetail(detail);
            row.setCreateDate(LocalDateTime.now());
            sendLogMapper.insert(row);
        } catch (Exception e) {
            log.warn("write notify_send_log failed", e);
        }
    }

    /** 弹窗最低级别（系统参数 notify.popup.min_level，默认 3） */
    public int popupMinLevel() {
        Integer v = configService.getInt("notify.popup.min_level", 3);
        return v == null || v < 1 || v > 3 ? 3 : v;
    }

    // ============================== 消息中心查询 ==============================

    public PageData<MessageVO> pageMine(Long userId, long page, long size, boolean unreadOnly) {
        LambdaQueryWrapper<NotifyMessage> qw = new LambdaQueryWrapper<NotifyMessage>()
                .eq(NotifyMessage::getUserId, userId)
                .eq(unreadOnly, NotifyMessage::getIsRead, 0)
                .orderByDesc(NotifyMessage::getId);
        Page<NotifyMessage> result = messageMapper.selectPage(new Page<>(page, size), qw);
        List<MessageVO> vos = result.getRecords().stream().map(this::toVO).toList();
        return new PageData<>(result.getTotal(), result.getCurrent(), result.getSize(), vos);
    }

    public List<MessageVO> latestMine(Long userId, int limit) {
        LambdaQueryWrapper<NotifyMessage> qw = new LambdaQueryWrapper<NotifyMessage>()
                .eq(NotifyMessage::getUserId, userId)
                .orderByDesc(NotifyMessage::getId)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 50));
        return messageMapper.selectList(qw).stream().map(this::toVO).toList();
    }

    public long unreadCount(Long userId) {
        return messageMapper.countUnread(userId);
    }

    public void markRead(Long userId, Long id) {
        NotifyMessage msg = messageMapper.selectById(id);
        if (msg == null || !userId.equals(msg.getUserId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "消息不存在");
        }
        if (msg.getIsRead() != null && msg.getIsRead() == 1) {
            return;
        }
        msg.setIsRead(1);
        msg.setReadDate(LocalDateTime.now());
        messageMapper.updateById(msg);
    }

    public int readAll(Long userId) {
        NotifyMessage set = new NotifyMessage();
        set.setIsRead(1);
        set.setReadDate(LocalDateTime.now());
        return messageMapper.update(set, new LambdaUpdateWrapper<NotifyMessage>()
                .eq(NotifyMessage::getUserId, userId)
                .eq(NotifyMessage::getIsRead, 0));
    }

    public void deleteMine(Long userId, Long id) {
        NotifyMessage msg = messageMapper.selectById(id);
        if (msg == null || !userId.equals(msg.getUserId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "消息不存在");
        }
        messageMapper.deleteById(id);
    }

    /** 坐席端通知设置（弹窗阈值/提示音） */
    public NotifySettingsVO settings() {
        NotifySettingsVO vo = new NotifySettingsVO();
        vo.setPopupMinLevel(popupMinLevel());
        vo.setSoundEnabled(Boolean.TRUE.equals(configService.getBool("notify.sound.enabled", true)));
        return vo;
    }

    private MessageVO toVO(NotifyMessage m) {
        MessageVO vo = new MessageVO();
        vo.setId(String.valueOf(m.getId()));
        vo.setTitle(m.getTitle());
        vo.setContent(m.getContent());
        vo.setLevel(m.getLevel());
        vo.setEventType(m.getEventType());
        vo.setBizType(m.getBizType());
        vo.setBizId(m.getBizId() == null ? null : String.valueOf(m.getBizId()));
        vo.setIsRead(m.getIsRead());
        vo.setCreateDate(m.getCreateDate());
        vo.setReadDate(m.getReadDate());
        return vo;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String trim(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 500 ? s.substring(0, 500) : s;
    }
}
