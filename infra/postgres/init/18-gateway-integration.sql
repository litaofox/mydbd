-- =====================================================================
-- 18-gateway-integration.sql
-- vps 车载网关对接（方案 GATEWAY-PLAN-001，一期 P0+P1 数据面）
-- 全部幂等：ADD COLUMN IF NOT EXISTS / CREATE ... IF NOT EXISTS /
-- ON CONFLICT DO NOTHING，可对已部署库重复执行。
-- =====================================================================

-- 1. 轨迹点：alarmFlag 为 32 位位图 Long，smallint 存不下 → bigint ----------
ALTER TABLE traj.traj_gps_point ALTER COLUMN alarm_flag TYPE bigint;
-- 网关原始扩展字段（trackId/gpsType/delay/status/recordSpeed/additions 等）
ALTER TABLE traj.traj_gps_point ADD COLUMN IF NOT EXISTS ext jsonb;
-- 幂等：同终端同 trackId 去重（仅网关数据带 trackId，存量/模拟器不受影响）
CREATE UNIQUE INDEX IF NOT EXISTS uk_traj_gps_point_track
    ON traj.traj_gps_point (identity_code, (ext ->> 'trackId'))
    WHERE ext ? 'trackId';

-- 2. 终端：网关双锚点标识 + 在线态 -----------------------------------------
ALTER TABLE traj.traj_terminal ADD COLUMN IF NOT EXISTS gateway_truck_id bigint;
ALTER TABLE traj.traj_terminal ADD COLUMN IF NOT EXISTS online_status smallint NOT NULL DEFAULT 0;
ALTER TABLE traj.traj_terminal ADD COLUMN IF NOT EXISTS last_heartbeat_time timestamp;
ALTER TABLE traj.traj_terminal ADD COLUMN IF NOT EXISTS last_online_time timestamp;
ALTER TABLE traj.traj_terminal ADD COLUMN IF NOT EXISTS last_offline_time timestamp;
COMMENT ON COLUMN traj.traj_terminal.gateway_truck_id IS 'vps 网关车辆主键 truckId（离线事件仅携带该标识）';
COMMENT ON COLUMN traj.traj_terminal.online_status IS '在线状态：0 离线 1 在线（由网关心跳/鉴权/离线事件维护）';
CREATE INDEX IF NOT EXISTS idx_traj_terminal_truck ON traj.traj_terminal (gateway_truck_id);

-- 3. 抓拍图片表：补网关来源关联列 -------------------------------------------
ALTER TABLE traj.traj_gps_photo ADD COLUMN IF NOT EXISTS warn_id varchar(32);
ALTER TABLE traj.traj_gps_photo ADD COLUMN IF NOT EXISTS media_type smallint NOT NULL DEFAULT 0;
ALTER TABLE traj.traj_gps_photo ADD COLUMN IF NOT EXISTS file_status smallint;
ALTER TABLE traj.traj_gps_photo ADD COLUMN IF NOT EXISTS source varchar(20) NOT NULL DEFAULT 'gateway';
CREATE INDEX IF NOT EXISTS idx_traj_photo_warn ON traj.traj_gps_photo (warn_id);

-- 4. 报警附件表（网关 warn_media 转存记录，一报警多附件） -------------------
CREATE TABLE IF NOT EXISTS traj.traj_warn_media (
    id           bigserial    PRIMARY KEY,
    warn_id      varchar(32)  NOT NULL,
    identity_code varchar(100),
    truck_id     bigint,
    file_name    varchar(200) NOT NULL,
    file_type    smallint     NOT NULL DEFAULT 0,
    file_size    bigint,
    url          varchar(500),
    local_path   varchar(500),
    file_status  smallint,
    receive_time timestamp,
    create_time  timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE traj.traj_warn_media IS '网关报警附件表（图片/音频/视频转存记录）';
COMMENT ON COLUMN traj.traj_warn_media.file_type IS '0 图片 1 音频 2 视频';
CREATE UNIQUE INDEX IF NOT EXISTS uk_traj_warn_media_file
    ON traj.traj_warn_media (warn_id, file_name);
CREATE INDEX IF NOT EXISTS idx_traj_warn_media_warn ON traj.traj_warn_media (warn_id);

-- 5. 网关事件码映射字典（苏标 typeId → 平台 event_code/默认级别） -----------
CREATE TABLE IF NOT EXISTS traj.gateway_event_map (
    type_id       integer     PRIMARY KEY,
    raw_type      varchar(16),
    event_code    varchar(40) NOT NULL,
    event_source  varchar(16) NOT NULL DEFAULT 'jt808',
    default_level smallint    NOT NULL DEFAULT 2,
    name          varchar(50) NOT NULL,
    valid_mark    smallint    NOT NULL DEFAULT 1
);
COMMENT ON TABLE traj.gateway_event_map IS 'vps 网关报警类型映射（苏标 typeId → 平台事件码，级别已按平台口径：1低 2中 3高）';

-- 种子：ADAS 101001-101011 / DSM 102001-102011 / 盲区 104001-104003 / 激烈驾驶 105001-105007
INSERT INTO traj.gateway_event_map (type_id, raw_type, event_code, default_level, name) VALUES
(101001, '0x64_0x01', 'ADAS_FCW',          3, '前向碰撞报警'),
(101002, '0x64_0x02', 'ADAS_LDW',          2, '车道偏离报警'),
(101003, '0x64_0x03', 'ADAS_HMW',          2, '车距过近报警'),
(101004, '0x64_0x04', 'ADAS_PCW',          3, '行人碰撞报警'),
(101005, '0x64_0x05', 'ADAS_FREQ_LANE',    2, '频繁变道报警'),
(101006, '0x64_0x06', 'ADAS_RSI',          2, '道路标识超限报警'),
(101007, '0x64_0x07', 'ADAS_OBSTACLE',     2, '障碍物报警'),
(101008, '0x64_0x08', 'ADAS_FAIL',         2, '驾驶辅助功能失效'),
(101009, '0x64_0x09', 'ADAS_SOLID_LANE',   2, '实线变道报警'),
(101010, '0x64_0x0A', 'ADAS_AEB',          3, '自动紧急制动'),
(101011, '0x64_0x0B', 'ADAS_OTHER',        2, '其他驾驶辅助报警'),
(102001, '0x65_0x01', 'DSM_FATIGUE',       3, '疲劳驾驶报警'),
(102002, '0x65_0x02', 'DSM_PHONE',         2, '接打电话报警'),
(102003, '0x65_0x03', 'DSM_SMOKE',         2, '抽烟报警'),
(102004, '0x65_0x04', 'DSM_DISTRACTION',   2, '长时间不目视前方报警'),
(102005, '0x65_0x05', 'DSM_DRIVER_ABN',    2, '驾驶员异常报警'),
(102006, '0x65_0x06', 'DSM_HANDS_OFF',     2, '双手同时脱离方向盘报警'),
(102007, '0x65_0x07', 'DSM_SEATBELT',      2, '未系安全带报警'),
(102008, '0x65_0x08', 'DSM_OCCLUSION',     2, '驾驶员面部遮挡报警'),
(102009, '0x65_0x09', 'DSM_IR_GLASSES',    2, '红外阻断型墨镜失效报警'),
(102010, '0x65_0x0A', 'DSM_PHOTO',         1, 'DSM 定时拍照事件'),
(102011, '0x65_0x0B', 'DSM_OTHER',         2, '其他 DSM 报警'),
(104001, NULL,        'BSD_RIGHT',         2, '右侧盲区接近报警'),
(104002, NULL,        'BSD_LEFT',          2, '左侧盲区接近报警'),
(104003, NULL,        'BSD_REAR',          2, '后方盲区接近报警'),
(105001, NULL,        'AGGR_RAPID_ACCEL',  1, '急加速'),
(105002, NULL,        'AGGR_RAPID_DECEL',  1, '急减速'),
(105003, NULL,        'AGGR_SHARP_TURN',   1, '急转弯'),
(105004, NULL,        'AGGR_IDLE',         1, '怠速过长'),
(105005, NULL,        'AGGR_COASTING',     1, '熄火滑行'),
(105006, NULL,        'AGGR_OVER_REV',     1, '发动机超转'),
(105007, NULL,        'AGGR_SPEEDING',     2, '激烈超速')
ON CONFLICT (type_id) DO UPDATE SET
    raw_type      = EXCLUDED.raw_type,
    event_code    = EXCLUDED.event_code,
    default_level = EXCLUDED.default_level,
    name          = EXCLUDED.name;

-- 6. 报警类型字典：扩 29 类（id=网关 typeId；报警中心 SQL JOIN 直接生效） --
-- grade_level 沿用既有种子口径（1 高 / 2 中 / 3 低，与 1-5 历史行一致）
INSERT INTO traj.base_warn_type (id, pid, name, grade_level) VALUES
(101001, -1, '前向碰撞报警', 1),
(101002, -1, '车道偏离报警', 2),
(101003, -1, '车距过近报警', 2),
(101004, -1, '行人碰撞报警', 1),
(101005, -1, '频繁变道报警', 2),
(101006, -1, '道路标识超限报警', 2),
(101007, -1, '障碍物报警', 2),
(101008, -1, '驾驶辅助功能失效', 2),
(101009, -1, '实线变道报警', 2),
(101010, -1, '自动紧急制动', 1),
(101011, -1, '其他驾驶辅助报警', 2),
(102001, -1, '疲劳驾驶报警', 1),
(102002, -1, '接打电话报警', 2),
(102003, -1, '抽烟报警', 2),
(102004, -1, '长时间不目视前方报警', 2),
(102005, -1, '驾驶员异常报警', 2),
(102006, -1, '双手脱离方向盘报警', 2),
(102007, -1, '未系安全带报警', 2),
(102008, -1, '驾驶员面部遮挡报警', 2),
(102009, -1, '红外阻断墨镜失效报警', 2),
(102010, -1, 'DSM 定时拍照事件', 3),
(102011, -1, '其他 DSM 报警', 2),
(104001, -1, '右侧盲区接近报警', 2),
(104002, -1, '左侧盲区接近报警', 2),
(104003, -1, '后方盲区接近报警', 2),
(105001, -1, '急加速', 3),
(105002, -1, '急减速', 3),
(105003, -1, '急转弯', 3),
(105004, -1, '怠速过长', 3),
(105005, -1, '熄火滑行', 3),
(105006, -1, '发动机超转', 3),
(105007, -1, '激烈超速', 2)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    grade_level = EXCLUDED.grade_level;

-- 7. 风险事件：来源锚点（warnId）防重复建单 --------------------------------
ALTER TABLE mon.risk_event ADD COLUMN IF NOT EXISTS source_id varchar(50);
COMMENT ON COLUMN mon.risk_event.source_id IS '外部来源唯一标识（网关 warnId），用于幂等去重';
CREATE UNIQUE INDEX IF NOT EXISTS uk_risk_event_source
    ON mon.risk_event (source_id) WHERE source_id IS NOT NULL;

-- 8. 死信表（毒消息隔离，不阻塞消费） ---------------------------------------
CREATE TABLE IF NOT EXISTS traj.gateway_dlq (
    id           bigserial PRIMARY KEY,
    topic        varchar(64)  NOT NULL,
    partition_id integer,
    offset_val   bigint,
    payload      text,
    error        text,
    create_time  timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE traj.gateway_dlq IS 'vps 网关消息死信表';
CREATE INDEX IF NOT EXISTS idx_gateway_dlq_time ON traj.gateway_dlq (create_time DESC);

-- 9. 消费统计表（各 topic 处理量/失败量，供接入状态页展示） ----------------
CREATE TABLE IF NOT EXISTS traj.gateway_ingest_stat (
    topic        varchar(64) PRIMARY KEY,
    msg_count    bigint NOT NULL DEFAULT 0,
    err_count    bigint NOT NULL DEFAULT 0,
    last_time    timestamp,
    update_time  timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE traj.gateway_ingest_stat IS 'vps 网关各 topic 消费统计';

-- 10. 未登记终端隔离区（查不到归属的终端数据不丢，待补建档） -----------------
CREATE TABLE IF NOT EXISTS traj.gateway_unknown_terminal (
    id            bigserial PRIMARY KEY,
    phone_number  varchar(32),
    truck_id      bigint,
    plate_no      varchar(50),
    msg_count     bigint NOT NULL DEFAULT 1,
    first_seen    timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen     timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE traj.gateway_unknown_terminal IS 'vps 网关未登记终端隔离区';
CREATE UNIQUE INDEX IF NOT EXISTS uk_gateway_unknown_phone
    ON traj.gateway_unknown_terminal (COALESCE(phone_number, ''), COALESCE(truck_id, -1));

-- 11. 菜单与权限：运行模式页（挂系统管理分组 900；2026-10-06 由「网关接入状态」更名） -----
INSERT INTO traj.sys_menu (id, parent_id, menu_name, menu_type, perm_code, path, icon, sort_no, visible, status)
VALUES (925, 900, '运行模式', 2, 'system:gateway:view', '/system/gateway', 'Connection', 92, 1, 1)
ON CONFLICT (id) DO UPDATE SET
    menu_name = EXCLUDED.menu_name,
    perm_code = EXCLUDED.perm_code,
    path      = EXCLUDED.path;

SELECT setval(pg_get_serial_sequence('traj.sys_menu', 'id'),
              (SELECT MAX(id) FROM traj.sys_menu), true);

-- 授权：超管 + 安全管理员可见（运维向页面）
INSERT INTO traj.sys_role_menu (role_id, menu_id) VALUES
    (1, 925),
    (2, 925)
ON CONFLICT DO NOTHING;

-- 12. 网关运行参数（系统参数页/网关状态页可改，内置不可删；重启 processing 生效） ---
-- 优先级：sys_config 非空值 > 环境变量 > 代码默认值；空值不覆盖环境变量。
INSERT INTO traj.sys_config (config_key, config_value, config_name, value_type, is_system, remark, creator) VALUES
('gateway.mode',                    'simulator',   '网关运行模式（simulator演示 / mock测试 / gateway正式）', 'STRING', 1, '修改后需重启 processing 服务生效', 'system'),
('gateway.kafka.bootstrap-servers', '',            'Kafka 地址（多个 broker 逗号分隔；mock 模式填 kafka:9092）', 'STRING', 1, '留空则回退环境变量 KAFKA_BOOTSTRAP_SERVERS；修改后需重启生效', 'system'),
('gateway.kafka.group-id',          'mydbd-ingest', 'Kafka 消费组', 'STRING', 1, '独立只读消费组；修改后需重启生效', 'system'),
('gateway.file-base-url',           '',            '网关附件服务地址（18009，如 http://10.0.0.1:18009）', 'STRING', 1, '留空则回退环境变量 GATEWAY_FILE_BASE_URL；修改后需重启生效', 'system'),
('gateway.media-strategy',          'local',       '附件策略（local转存平台卷 / proxy仅记录原始URL）', 'STRING', 1, '修改后需重启 processing 服务生效', 'system'),
('gateway.mock-delivery-enabled',   'on',          '仿真投递开关（on投递 / off停止，仅测试模式）', 'STRING', 1, '运行时启停即时生效并写回本参数；重启 processing 后保持', 'system')
ON CONFLICT (config_key) DO NOTHING;
