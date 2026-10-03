-- F35 系统配置与通知中心
-- 数据字典 + 系统参数 + 预留（站内消息/文件元数据）

-- ============ 数据字典 ============
CREATE TABLE IF NOT EXISTS traj.sys_dict_type (
    id           bigserial PRIMARY KEY,
    dict_code    varchar(64) NOT NULL,
    dict_name    varchar(128) NOT NULL,
    status       smallint NOT NULL DEFAULT 1,
    remark       varchar(255),
    creator      varchar(64),
    create_date  timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      varchar(64),
    update_date  timestamp,
    CONSTRAINT uk_dict_type_code UNIQUE (dict_code)
);

CREATE TABLE IF NOT EXISTS traj.sys_dict_item (
    id           bigserial PRIMARY KEY,
    dict_type_id bigint NOT NULL REFERENCES traj.sys_dict_type(id) ON DELETE CASCADE,
    item_label   varchar(128) NOT NULL,
    item_value   varchar(128) NOT NULL,
    sort         int NOT NULL DEFAULT 0,
    status       smallint NOT NULL DEFAULT 1,
    css_class    varchar(64),
    remark       varchar(255),
    creator      varchar(64),
    create_date  timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      varchar(64),
    update_date  timestamp,
    CONSTRAINT uk_dict_item_type_value UNIQUE (dict_type_id, item_value)
);
CREATE INDEX IF NOT EXISTS idx_dict_item_type ON traj.sys_dict_item(dict_type_id);

-- ============ 系统参数 ============
CREATE TABLE IF NOT EXISTS traj.sys_config (
    id           bigserial PRIMARY KEY,
    config_key   varchar(128) NOT NULL,
    config_value text,
    config_name  varchar(128) NOT NULL,
    value_type   varchar(16) NOT NULL DEFAULT 'STRING',
    is_system    smallint NOT NULL DEFAULT 0,
    remark       varchar(255),
    creator      varchar(64),
    create_date  timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      varchar(64),
    update_date  timestamp,
    CONSTRAINT uk_config_key UNIQUE (config_key)
);

-- ============ 预留：站内消息 ============
CREATE TABLE IF NOT EXISTS traj.sys_message (
    id           bigserial PRIMARY KEY,
    user_id      bigint,
    title        varchar(255) NOT NULL,
    content      text,
    msg_type     varchar(32),
    is_read      smallint NOT NULL DEFAULT 0,
    creator      varchar(64),
    create_date  timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============ 预留：文件元数据 ============
CREATE TABLE IF NOT EXISTS traj.sys_file (
    id           bigserial PRIMARY KEY,
    file_name    varchar(255) NOT NULL,
    file_path    varchar(512) NOT NULL,
    file_size    bigint,
    content_type varchar(128),
    biz_type     varchar(64),
    creator      varchar(64),
    create_date  timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============ 字典类型种子 ============
INSERT INTO traj.sys_dict_type (dict_code, dict_name, status, remark) VALUES
 ('plate_color','车牌颜色',1,'车牌底色'),
 ('protocol_type','协议类型',1,'终端通信协议'),
 ('equipment_type','设备类型',1,'车载设备类型'),
 ('terminal_status','终端状态',1,'终端在线状态'),
 ('driver_sex','驾驶员性别',1,''),
 ('driver_status','驾驶员状态',1,''),
 ('risk_event_code','风险事件码',1,'CEP 事件编码'),
 ('risk_level','风险等级',1,'1低 2中 3高'),
 ('close_result','闭环结果',1,'工单闭环方式'),
 ('order_status','工单状态',1,''),
 ('alarm_type','终端报警类型',1,'')
ON CONFLICT (dict_code) DO NOTHING;

-- ============ 字典项种子 ============
-- 车牌颜色
INSERT INTO traj.sys_dict_item (dict_type_id, item_label, item_value, sort)
SELECT id,'蓝色','BLUE',1 FROM traj.sys_dict_type WHERE dict_code='plate_color'
UNION ALL SELECT id,'黄色','YELLOW',2 FROM traj.sys_dict_type WHERE dict_code='plate_color'
UNION ALL SELECT id,'绿色','GREEN',3 FROM traj.sys_dict_type WHERE dict_code='plate_color'
UNION ALL SELECT id,'白色','WHITE',4 FROM traj.sys_dict_type WHERE dict_code='plate_color'
UNION ALL SELECT id,'黑色','BLACK',5 FROM traj.sys_dict_type WHERE dict_code='plate_color'
ON CONFLICT (dict_type_id, item_value) DO NOTHING;

-- 协议类型
INSERT INTO traj.sys_dict_item (dict_type_id, item_label, item_value, sort)
SELECT id,'JT808','JT808',1 FROM traj.sys_dict_type WHERE dict_code='protocol_type'
UNION ALL SELECT id,'JT1078','JT1078',2 FROM traj.sys_dict_type WHERE dict_code='protocol_type'
UNION ALL SELECT id,'其他','OTHER',9 FROM traj.sys_dict_type WHERE dict_code='protocol_type'
ON CONFLICT (dict_type_id, item_value) DO NOTHING;

-- 设备类型
INSERT INTO traj.sys_dict_item (dict_type_id, item_label, item_value, sort)
SELECT id,'北斗终端','GNSS',1 FROM traj.sys_dict_type WHERE dict_code='equipment_type'
UNION ALL SELECT id,'DSM','DSM',2 FROM traj.sys_dict_type WHERE dict_code='equipment_type'
UNION ALL SELECT id,'ADAS','ADAS',3 FROM traj.sys_dict_type WHERE dict_code='equipment_type'
UNION ALL SELECT id,'屏机一体','ALL_IN_ONE',4 FROM traj.sys_dict_type WHERE dict_code='equipment_type'
ON CONFLICT (dict_type_id, item_value) DO NOTHING;

-- 终端状态
INSERT INTO traj.sys_dict_item (dict_type_id, item_label, item_value, sort)
SELECT id,'在线','ONLINE',1 FROM traj.sys_dict_type WHERE dict_code='terminal_status'
UNION ALL SELECT id,'离线','OFFLINE',2 FROM traj.sys_dict_type WHERE dict_code='terminal_status'
UNION ALL SELECT id,'停用','DISABLED',9 FROM traj.sys_dict_type WHERE dict_code='terminal_status'
ON CONFLICT (dict_type_id, item_value) DO NOTHING;

-- 驾驶员性别
INSERT INTO traj.sys_dict_item (dict_type_id, item_label, item_value, sort)
SELECT id,'男','MALE',1 FROM traj.sys_dict_type WHERE dict_code='driver_sex'
UNION ALL SELECT id,'女','FEMALE',2 FROM traj.sys_dict_type WHERE dict_code='driver_sex'
ON CONFLICT (dict_type_id, item_value) DO NOTHING;

-- 驾驶员状态
INSERT INTO traj.sys_dict_item (dict_type_id, item_label, item_value, sort)
SELECT id,'在岗','ACTIVE',1 FROM traj.sys_dict_type WHERE dict_code='driver_status'
UNION ALL SELECT id,'离岗','INACTIVE',2 FROM traj.sys_dict_type WHERE dict_code='driver_status'
UNION ALL SELECT id,'停用','DISABLED',9 FROM traj.sys_dict_type WHERE dict_code='driver_status'
ON CONFLICT (dict_type_id, item_value) DO NOTHING;

-- 风险事件码
INSERT INTO traj.sys_dict_item (dict_type_id, item_label, item_value, sort)
SELECT id,'疲劳驾驶','DSM_FATIGUE',1 FROM traj.sys_dict_type WHERE dict_code='risk_event_code'
UNION ALL SELECT id,'分心驾驶','DSM_DISTRACTION',2 FROM traj.sys_dict_type WHERE dict_code='risk_event_code'
UNION ALL SELECT id,'前向碰撞','ADAS_FCW',3 FROM traj.sys_dict_type WHERE dict_code='risk_event_code'
UNION ALL SELECT id,'车道偏离','ADAS_LDW',4 FROM traj.sys_dict_type WHERE dict_code='risk_event_code'
UNION ALL SELECT id,'偏离路线','GEO_EXIT',5 FROM traj.sys_dict_type WHERE dict_code='risk_event_code'
UNION ALL SELECT id,'未系安全带','V_PCW',6 FROM traj.sys_dict_type WHERE dict_code='risk_event_code'
ON CONFLICT (dict_type_id, item_value) DO NOTHING;

-- 风险等级
INSERT INTO traj.sys_dict_item (dict_type_id, item_label, item_value, sort)
SELECT id,'低','1',1 FROM traj.sys_dict_type WHERE dict_code='risk_level'
UNION ALL SELECT id,'中','2',2 FROM traj.sys_dict_type WHERE dict_code='risk_level'
UNION ALL SELECT id,'高','3',3 FROM traj.sys_dict_type WHERE dict_code='risk_level'
ON CONFLICT (dict_type_id, item_value) DO NOTHING;

-- 闭环结果
INSERT INTO traj.sys_dict_item (dict_type_id, item_label, item_value, sort)
SELECT id,'电话提醒','PHONE_REMIND',1 FROM traj.sys_dict_type WHERE dict_code='close_result'
UNION ALL SELECT id,'安全教育','EDUCATION',2 FROM traj.sys_dict_type WHERE dict_code='close_result'
UNION ALL SELECT id,'通报处罚','REPORT_PENALTY',3 FROM traj.sys_dict_type WHERE dict_code='close_result'
UNION ALL SELECT id,'交通违法','TRAFFIC_VIOLATION',4 FROM traj.sys_dict_type WHERE dict_code='close_result'
UNION ALL SELECT id,'误报','FALSE_ALARM',9 FROM traj.sys_dict_type WHERE dict_code='close_result'
ON CONFLICT (dict_type_id, item_value) DO NOTHING;

-- 工单状态
INSERT INTO traj.sys_dict_item (dict_type_id, item_label, item_value, sort)
SELECT id,'待处理','PENDING',1 FROM traj.sys_dict_type WHERE dict_code='order_status'
UNION ALL SELECT id,'处理中','PROCESSING',2 FROM traj.sys_dict_type WHERE dict_code='order_status'
UNION ALL SELECT id,'已闭环','CLOSED',3 FROM traj.sys_dict_type WHERE dict_code='order_status'
ON CONFLICT (dict_type_id, item_value) DO NOTHING;

-- 终端报警类型
INSERT INTO traj.sys_dict_item (dict_type_id, item_label, item_value, sort)
SELECT id,'紧急报警','EMERGENCY',1 FROM traj.sys_dict_type WHERE dict_code='alarm_type'
UNION ALL SELECT id,'超速','OVER_SPEED',2 FROM traj.sys_dict_type WHERE dict_code='alarm_type'
UNION ALL SELECT id,'疲劳','FATIGUE',3 FROM traj.sys_dict_type WHERE dict_code='alarm_type'
UNION ALL SELECT id,'偏离路线','ROUTE_DEVIATION',4 FROM traj.sys_dict_type WHERE dict_code='alarm_type'
ON CONFLICT (dict_type_id, item_value) DO NOTHING;

-- ============ 系统参数种子 ============
INSERT INTO traj.sys_config (config_key, config_value, config_name, value_type, is_system, remark) VALUES
 ('password.min_length','8','密码最小长度','INT',1,''),
 ('password.max_length','20','密码最大长度','INT',1,''),
 ('login.max_fail','5','登录失败锁定次数','INT',1,''),
 ('login.lock_minutes','15','锁定时长(分钟)','INT',1,''),
 ('sla.grace_ratio','0.2','临期阈值比例','STRING',1,'工单剩余时限低于该比例视为临期')
ON CONFLICT (config_key) DO NOTHING;

-- ============ 菜单 ============
-- 挂在系统管理分组下（parent_id=0 即根菜单）
INSERT INTO traj.sys_menu (id, parent_id, menu_name, menu_type, perm_code, path, icon, sort_no, visible, status)
VALUES
 (920, 0, '数据字典', 2, 'system:dict:view', '/system/dict', 'Grid', 918, 1, 1),
 (921, 0, '系统参数', 2, 'system:config:view', '/system/config', 'Setting', 919, 1, 1),
 (922, 920, '字典编辑', 3, 'system:dict:edit', NULL, NULL, 1, 1, 1),
 (923, 921, '参数编辑', 3, 'system:config:edit', NULL, NULL, 1, 1, 1)
ON CONFLICT (id) DO NOTHING;

-- 授权：admin 全量
INSERT INTO traj.sys_role_menu (role_id, menu_id)
SELECT 1, m.id FROM traj.sys_menu m WHERE m.id IN (920,921,922,923)
ON CONFLICT (role_id, menu_id) DO NOTHING;

-- SAFE_ADMIN (role 2) 仅 view
INSERT INTO traj.sys_role_menu (role_id, menu_id)
SELECT 2, m.id FROM traj.sys_menu m WHERE m.id IN (920,921)
ON CONFLICT (role_id, menu_id) DO NOTHING;
