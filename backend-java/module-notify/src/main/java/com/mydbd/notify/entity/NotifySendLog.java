package com.mydbd.notify.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * F19 通知发送日志（traj.notify_send_log）
 */
@Data
@TableName("traj.notify_send_log")
public class NotifySendLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventType;
    private String bizType;
    private Long bizId;

    /** INBOX / WEBSOCKET / SMS / VOICE / PUSH */
    private String channel;

    private Long receiverId;
    private String title;
    private Integer level;

    /** SENT / SKIPPED / FAILED */
    private String status;

    private String detail;

    @TableField("create_date")
    private LocalDateTime createDate;
}
