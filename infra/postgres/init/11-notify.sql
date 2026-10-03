-- F19 报警分级与多渠道通知
-- 站内消息扩展 + 通知发送日志 + 通知参数 + 消息中心菜单

-- ============ 扩展 sys_message（F35 预留表） ============
ALTER TABLE traj.sys_message ADD COLUMN IF NOT EXISTS biz_type   varchar(32);
ALTER TABLE traj.sys_message ADD COLUMN IF NOT EXISTS biz_id     bigint;
ALTER TABLE traj.sys_message ADD COLUMN IF NOT EXISTS level      smallint NOT NULL DEFAULT 1;
ALTER TABLE traj.sys_message ADD COLUMN IF NOT EXISTS event_type varchar(32);
ALTER TABLE traj.sys_message ADD COLUMN IF NOT EXISTS read_date  timestamp;
CREATE INDEX IF NOT EXISTS idx_message_user_read ON traj.sys_message(user_id, is_read, id DESC);
CREATE INDEX IF NOT EXISTS idx_message_biz ON traj.sys_message(biz_type, biz_id);

-- ============ 通知发送日志 ============
CREATE TABLE IF NOT EXISTS traj.notify_send_log (
    id           bigserial PRIMARY KEY,
    event_type   varchar(32),
    biz_type     varchar(32),
    biz_id       bigint,
    channel      varchar(20),
    receiver_id  bigint,
    title        varchar(255),
    level        smallint,
    status       varchar(16),
    detail       varchar(500),
    create_date  timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_notify_log_biz ON traj.notify_send_log(biz_id, event_type);
CREATE INDEX IF NOT EXISTS idx_notify_log_time ON traj.notify_send_log(create_date DESC);

-- ============ 通知参数种子 ============
INSERT INTO traj.sys_config (config_key, config_value, config_name, value_type, is_system, remark) VALUES
 ('notify.popup.min_level','3','弹窗最低风险等级','INT',0,'等级≥该值的站内通知在坐席端弹出卡片并响铃'),
 ('notify.sound.enabled','true','通知提示音','BOOL',0,'坐席端高风险通知是否播放提示音'),
 ('notify.sms.enabled','false','短信通知渠道','BOOL',0,'对接短信厂商后开启'),
 ('notify.voice.enabled','false','语音外呼渠道','BOOL',0,'对接语音网关后开启'),
 ('notify.push.enabled','false','App 推送渠道','BOOL',0,'对接推送服务后开启')
ON CONFLICT (config_key) DO NOTHING;

-- ============ 字典：通知事件类型 ============
INSERT INTO traj.sys_dict_type (dict_code, dict_name, status, remark) VALUES
 ('notify_event_type','通知事件类型',1,'F19 通知触发场景')
ON CONFLICT (dict_code) DO NOTHING;

INSERT INTO traj.sys_dict_item (dict_type_id, item_label, item_value, sort)
SELECT id,'工单生成','ORDER_CREATE',1 FROM traj.sys_dict_type WHERE dict_code='notify_event_type'
UNION ALL SELECT id,'工单分派','ORDER_ASSIGN',2 FROM traj.sys_dict_type WHERE dict_code='notify_event_type'
UNION ALL SELECT id,'超时升级','ORDER_ESCALATE',3 FROM traj.sys_dict_type WHERE dict_code='notify_event_type'
UNION ALL SELECT id,'工单闭环','ORDER_CLOSE',4 FROM traj.sys_dict_type WHERE dict_code='notify_event_type'
UNION ALL SELECT id,'系统通知','SYSTEM',9 FROM traj.sys_dict_type WHERE dict_code='notify_event_type'
ON CONFLICT (dict_type_id, item_value) DO NOTHING;

-- ============ 菜单：消息中心（全员可见） ============
INSERT INTO traj.sys_menu (id, parent_id, menu_name, menu_type, perm_code, path, icon, sort_no, visible, status)
VALUES (924, 0, '消息中心', 2, 'system:message:view', '/system/messages', 'ChatDotRound', 920, 1, 1)
ON CONFLICT (id) DO NOTHING;

INSERT INTO traj.sys_role_menu (role_id, menu_id)
SELECT r.id, 924 FROM traj.sys_role r WHERE r.id IN (1,2,3,4)
ON CONFLICT (role_id, menu_id) DO NOTHING;
