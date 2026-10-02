package com.mydbd.monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 驾驶员监控视频分析任务（对应 mon.video_analysis）
 */
@Data
@TableName("mon.video_analysis")
public class VideoAnalysis {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskId;

    private String plateNo;

    /** 通道：DSM（驾驶员）/ ADAS（前向道路） */
    private String channel;

    private String clipUrl;

    /** PENDING / RUNNING / SUCCESS / FAILED */
    private String status;

    private String resultSummary;

    private Integer eventCount;

    @TableField("create_date")
    private LocalDateTime createDate;

    @TableField("update_date")
    private LocalDateTime updateDate;
}
