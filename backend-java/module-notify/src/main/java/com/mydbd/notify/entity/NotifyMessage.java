package com.mydbd.notify.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * F19 站内消息（traj.sys_message，F35 预留表扩展）
 */
@Data
@TableName("traj.sys_message")
public class NotifyMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String title;
    private String content;

    /** INBOX（站内） */
    private String msgType;

    /** 0 未读 / 1 已读 */
    private Integer isRead;

    /** WORK_ORDER / SYSTEM */
    private String bizType;

    /** 业务对象 id（如工单 id） */
    private Long bizId;

    /** 1 低 / 2 中 / 3 高 */
    private Integer level;

    /** ORDER_CREATE / ORDER_ASSIGN / ORDER_ESCALATE / ORDER_CLOSE / SYSTEM */
    private String eventType;

    private String creator;

    @TableField("create_date")
    private LocalDateTime createDate;

    private LocalDateTime readDate;
}
