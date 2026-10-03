-- 07 F18 风控规则引擎（CEP）：规则配置、电子围栏、围栏车辆状态、风险事件扩展
-- 幂等：可重复执行

-- =====================================================================
-- 7.1 风控规则
-- =====================================================================
CREATE TABLE IF NOT EXISTS mon.risk_rule (
    id           bigserial    PRIMARY KEY,
    rule_code    varchar(40)  NOT NULL,                 -- SPEED_GENERAL / DSM_FATIGUE / COMBO_FATIGUE_SPEED ...
    rule_name    varchar(80)  NOT NULL,                 -- 中文名称，事件 title 的快照来源
    rule_type    varchar(16)  NOT NULL,                 -- SPEED / FATIGUE / SIGNAL / COMBO
    event_code   varchar(40)  NOT NULL,                 -- 产出事件编码；SIGNAL 类即信号码
    risk_level   smallint     NOT NULL DEFAULT 2,       -- 1低 2中 3高
    params       jsonb        NOT NULL DEFAULT '{}'::jsonb,
    cooldown_sec integer      NOT NULL DEFAULT 300,     -- 同车同规则去重窗口（秒）
    status       smallint     NOT NULL DEFAULT 1,       -- 1启用 0停用
    built_in     smallint     NOT NULL DEFAULT 0,       -- 1内置（不可删）
    valid_mark   smallint     NOT NULL DEFAULT 1,
    remark       varchar(255),
    creator      varchar(40),
    create_date  timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      varchar(40),
    update_date  timestamp
);
COMMENT ON TABLE mon.risk_rule IS 'F18 风控规则（CEP）配置';
CREATE UNIQUE INDEX IF NOT EXISTS uk_risk_rule_code
    ON mon.risk_rule (rule_code) WHERE valid_mark = 1;
CREATE INDEX IF NOT EXISTS idx_risk_rule_type
    ON mon.risk_rule (rule_type) WHERE valid_mark = 1;

-- =====================================================================
-- 7.2 电子围栏
-- =====================================================================
CREATE TABLE IF NOT EXISTS mon.risk_geo_fence (
    id           bigserial      PRIMARY KEY,
    fence_name   varchar(80)    NOT NULL,
    fence_type   varchar(10)    NOT NULL,                -- CIRCLE / POLYGON
    center_lng   numeric(10,6),
    center_lat   numeric(10,6),
    radius_m     integer,                               -- 圆形半径（米）
    polygon_geom geometry(Polygon, 4326),                -- 多边形
    trigger_dir  smallint       NOT NULL DEFAULT 2,     -- 1进入报警 2离开报警 3进出都报
    risk_level   smallint       NOT NULL DEFAULT 2,
    cooldown_sec integer        NOT NULL DEFAULT 600,
    status       smallint       NOT NULL DEFAULT 1,
    valid_mark   smallint       NOT NULL DEFAULT 1,
    remark       varchar(255),
    creator      varchar(40),
    create_date  timestamp      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      varchar(40),
    update_date  timestamp
);
COMMENT ON TABLE mon.risk_geo_fence IS 'F18 电子围栏（圆/多边形）';
CREATE UNIQUE INDEX IF NOT EXISTS uk_risk_fence_name
    ON mon.risk_geo_fence (fence_name) WHERE valid_mark = 1;
CREATE INDEX IF NOT EXISTS idx_risk_fence_geom
    ON mon.risk_geo_fence USING gist (polygon_geom);

-- =====================================================================
-- 7.3 围栏-车辆状态（进出状态机 + 围栏事件冷却）
-- =====================================================================
CREATE TABLE IF NOT EXISTS mon.risk_fence_state (
    identity_code   varchar(100) NOT NULL,
    fence_id        bigint       NOT NULL,
    inside          smallint     NOT NULL DEFAULT 0,    -- 最近一个点是否在围栏内
    last_point_time timestamp,
    last_enter_time timestamp,                          -- 最近 ENTER 事件时间（冷却）
    last_exit_time  timestamp,                          -- 最近 EXIT 事件时间（冷却）
    update_date     timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (identity_code, fence_id)
);
COMMENT ON TABLE mon.risk_fence_state IS 'F18 围栏车辆状态机（首见只初始化不补报）';

-- =====================================================================
-- 7.4 风险事件扩展（幂等加列）
-- =====================================================================
ALTER TABLE mon.risk_event ADD COLUMN IF NOT EXISTS rule_id  bigint;
ALTER TABLE mon.risk_event ADD COLUMN IF NOT EXISTS fence_id bigint;
ALTER TABLE mon.risk_event ADD COLUMN IF NOT EXISTS title    varchar(100);
COMMENT ON COLUMN mon.risk_event.rule_id  IS 'F18 命中规则 id（围栏事件为空）';
COMMENT ON COLUMN mon.risk_event.fence_id IS 'F18 命中围栏 id（规则事件为空）';
COMMENT ON COLUMN mon.risk_event.title    IS 'F18 事件中文名快照（规则事后改名不影响历史）';
CREATE INDEX IF NOT EXISTS idx_mon_risk_rule ON mon.risk_event (rule_id);

-- =====================================================================
-- 7.5 内置规则种子（8 条）
-- =====================================================================
INSERT INTO mon.risk_rule
    (rule_code, rule_name, rule_type, event_code, risk_level, params, cooldown_sec, status, built_in, remark, creator)
SELECT * FROM (VALUES
    ('SPEED_GENERAL',         '一般超速',          'SPEED',  'SPEED_GENERAL',         2,
     '{"speedKmh": 100}'::jsonb,                                                                 300,  1, 1, '速度达到阈值即命中；同车冷却窗口内不重复', 'system'),
    ('SPEED_SEVERE',          '严重超速',          'SPEED',  'SPEED_SEVERE',          3,
     '{"speedKmh": 120}'::jsonb,                                                                 180,  1, 1, '严重超速直接高风险',                       'system'),
    ('FATIGUE_DRIVE',         '疲劳驾驶',          'FATIGUE', 'FATIGUE_DRIVE',         3,
     '{"continuousMin": 240, "gapMin": 10}'::jsonb,                                              1800, 1, 1, '连续驾驶满 4 小时；点间隔超 10 分钟视为中断', 'system'),
    ('DSM_FATIGUE',           '终端信号·疲劳',     'SIGNAL', 'DSM_FATIGUE',           3,
     '{}'::jsonb,                                                                                 600,  1, 1, '终端 DSM 疲劳信号分级',                    'system'),
    ('DSM_DISTRACTION',       '终端信号·分心',     'SIGNAL', 'DSM_DISTRACTION',       2,
     '{}'::jsonb,                                                                                 600,  1, 1, '终端 DSM 分心信号分级',                    'system'),
    ('ADAS_FCW',              '终端信号·前向碰撞风险', 'SIGNAL', 'ADAS_FCW',          3,
     '{}'::jsonb,                                                                                 300,  1, 1, '终端 ADAS 前向碰撞信号分级',               'system'),
    ('ADAS_LDW',              '终端信号·车道偏离', 'SIGNAL', 'ADAS_LDW',              2,
     '{}'::jsonb,                                                                                 300,  1, 1, '终端 ADAS 车道偏离信号分级',               'system'),
    ('COMBO_FATIGUE_SPEED',   '疲劳叠加超速',      'COMBO',  'COMBO_FATIGUE_SPEED',   3,
     '{"windowMin": 30, "speedKmh": 100}'::jsonb,                                                1800, 1, 1, '30 分钟窗内有疲劳事件且当前超速',          'system')
) AS v(rule_code, rule_name, rule_type, event_code, risk_level, params, cooldown_sec, status, built_in, remark, creator)
WHERE NOT EXISTS (SELECT 1 FROM mon.risk_rule r WHERE r.rule_code = v.rule_code AND r.valid_mark = 1);

SELECT setval(pg_get_serial_sequence('mon.risk_rule', 'id'),
              (SELECT MAX(id) FROM mon.risk_rule), true);

-- =====================================================================
-- 7.6 演示围栏种子（国贸附近圆形，进出都报；可编辑可删除）
-- =====================================================================
INSERT INTO mon.risk_geo_fence
    (fence_name, fence_type, center_lng, center_lat, radius_m, trigger_dir, risk_level, cooldown_sec,
     status, valid_mark, remark, creator)
SELECT '演示围栏·国贸（可删）', 'CIRCLE', 116.404000, 39.912000, 3000, 3, 2, 600, 1, 1,
       'F18 上线演示用：模拟车辆围绕国贸 2~9km 游走，会自然产生进出事件', 'system'
WHERE NOT EXISTS (
    SELECT 1 FROM mon.risk_geo_fence WHERE fence_name = '演示围栏·国贸（可删）' AND valid_mark = 1
);

SELECT setval(pg_get_serial_sequence('mon.risk_geo_fence', 'id'),
              (SELECT MAX(id) FROM mon.risk_geo_fence), true);

-- =====================================================================
-- 7.7 菜单与权限点（904/905 菜单，913/914 按钮；显式 id）
-- =====================================================================
INSERT INTO traj.sys_menu (id, parent_id, menu_name, menu_type, perm_code, path, icon, sort_no, visible, status)
VALUES
    (904, 900, '风控规则', 2, 'risk:rule:view',  '/system/rules',  'SetUp',       94, 1, 1),
    (905, 900, '电子围栏', 2, 'risk:fence:view', '/system/fences', 'MapLocation', 95, 1, 1),
    (913, 904, '风控规则维护', 3, 'risk:rule:edit',  NULL, NULL, 1, 1, 1),
    (914, 905, '围栏维护',     3, 'risk:fence:edit', NULL, NULL, 1, 1, 1)
ON CONFLICT (id) DO NOTHING;

SELECT setval(pg_get_serial_sequence('traj.sys_menu', 'id'),
              (SELECT MAX(id) FROM traj.sys_menu), true);

-- 超级管理员：全量（幂等）
INSERT INTO traj.sys_role_menu (role_id, menu_id)
SELECT 1, id FROM traj.sys_menu
ON CONFLICT DO NOTHING;

-- 安全管理员：风控规则 + 电子围栏（含维护按钮）
INSERT INTO traj.sys_role_menu (role_id, menu_id)
VALUES
    (2, 904), (2, 905), (2, 913), (2, 914)
ON CONFLICT DO NOTHING;
