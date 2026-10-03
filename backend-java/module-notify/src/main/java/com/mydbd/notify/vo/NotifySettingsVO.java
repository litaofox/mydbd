package com.mydbd.notify.vo;

import lombok.Data;

/**
 * F19 坐席端通知设置（弹窗阈值与提示音）
 */
@Data
public class NotifySettingsVO {

    /** 弹窗最低风险等级 */
    private int popupMinLevel;

    /** 是否播放提示音 */
    private boolean soundEnabled;
}
