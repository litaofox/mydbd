-- F22 驾驶行为评分模型
-- mon.driver_score 日快照表 + score_level 字典 + 分析决策菜单 300/301

-- ============ 评分日快照表 ============
CREATE TABLE IF NOT EXISTS mon.driver_score (
    id            bigserial    PRIMARY KEY,
    score_date    date         NOT NULL,
    driver_id     bigint       NOT NULL,
    identity_code varchar(100),
    plate_no      varchar(50),
    dept_id       bigint,
    score         numeric(5,1) NOT NULL,               -- 0~100，1 位小数
    level         char(1)      NOT NULL,               -- A~E（字典 score_level）
    features      jsonb        NOT NULL DEFAULT '{}'::jsonb,
    sample_points integer,
    event_count   integer,
    create_date   timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_driver_score_date_driver UNIQUE (score_date, driver_id)
);
COMMENT ON TABLE mon.driver_score IS 'F22 驾驶行为日评分快照（重算=upsert，无逻辑删除）';
CREATE INDEX IF NOT EXISTS idx_driver_score_date   ON mon.driver_score (score_date DESC);
CREATE INDEX IF NOT EXISTS idx_driver_score_driver ON mon.driver_score (driver_id, score_date DESC);
CREATE INDEX IF NOT EXISTS idx_driver_score_dept   ON mon.driver_score (dept_id, score_date DESC);

-- ============ 字典：评分等级 ============
INSERT INTO traj.sys_dict_type (dict_code, dict_name, status, remark)
VALUES ('score_level','驾驶行为评分等级',1,'A≥90 B≥75 C≥60 D≥40 E<40')
ON CONFLICT (dict_code) DO NOTHING;

INSERT INTO traj.sys_dict_item (dict_type_id, item_label, item_value, sort)
SELECT id,'优秀','A',1 FROM traj.sys_dict_type WHERE dict_code='score_level'
UNION ALL SELECT id,'良好','B',2 FROM traj.sys_dict_type WHERE dict_code='score_level'
UNION ALL SELECT id,'一般','C',3 FROM traj.sys_dict_type WHERE dict_code='score_level'
UNION ALL SELECT id,'较差','D',4 FROM traj.sys_dict_type WHERE dict_code='score_level'
UNION ALL SELECT id,'危险','E',5 FROM traj.sys_dict_type WHERE dict_code='score_level'
ON CONFLICT (dict_type_id, item_value) DO NOTHING;

-- ============ 菜单：分析决策 / 驾驶评分 ============
INSERT INTO traj.sys_menu (id, parent_id, menu_name, menu_type, perm_code, path, icon, sort_no, visible, status) VALUES
  (300, 0,   '分析决策', 1, NULL,                  NULL,               'DataAnalysis', 30, 1, 1),
  (301, 300, '驾驶评分', 2, 'analysis:score:view', '/analysis/scores', NULL,           31, 1, 1)
ON CONFLICT (id) DO NOTHING;
SELECT setval(pg_get_serial_sequence('traj.sys_menu','id'), (SELECT MAX(id) FROM traj.sys_menu), true);

-- 超级管理员：全量（幂等补齐新菜单）
INSERT INTO traj.sys_role_menu (role_id, menu_id)
SELECT 1, id FROM traj.sys_menu
ON CONFLICT DO NOTHING;

-- 安全管理员：分析决策目录 + 驾驶评分（调度员/车队长不授权）
INSERT INTO traj.sys_role_menu (role_id, menu_id)
VALUES (2,300),(2,301)
ON CONFLICT DO NOTHING;
