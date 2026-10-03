package com.mydbd.notify.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * F19 站内消息 VO（id/bizId 字符串化防 JS 精度丢失）
 */
@Data
public class MessageVO {

    private String id;
    private String title;
    private String content;
    private Integer level;
    private String eventType;
    private String bizType;
    private String bizId;
    private Integer isRead;
    private LocalDateTime createDate;
    private LocalDateTime readDate;
}
