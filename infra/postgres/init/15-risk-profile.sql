-- F23 风险趋势与画像
-- 菜单 302（分析决策目录下）+ role1/2 授权 + risk_event 聚合索引补齐
-- 注：300 目录与 301 驾驶评分由 14-driving-score.sql 负责，本脚本不重复插入。

-- ============ 菜单：风险趋势与画像 ============
INSERT INTO traj.sys_menu (id, parent_id, menu_name, menu_type, perm_code, path, icon, sort_no, visible, status) VALUES
  (302, 300, '风险趋势与画像', 2, 'analysis:profile:view', '/analysis/profiles', 'TrendCharts', 32, 1, 1)
ON CONFLICT (id) DO NOTHING;
SELECT setval(pg_get_serial_sequence('traj.sys_menu','id'), (SELECT MAX(id) FROM traj.sys_menu), true);

-- 超级管理员：全量（幂等补齐新菜单）
INSERT INTO traj.sys_role_menu (role_id, menu_id)
SELECT 1, id FROM traj.sys_menu
ON CONFLICT DO NOTHING;

-- 安全管理员：目录 300 + 画像 302（调度员/车队长不授权）
INSERT INTO traj.sys_role_menu (role_id, menu_id)
VALUES (2,300),(2,302)
ON CONFLICT DO NOTHING;

-- ============ 聚合查询索引补齐 ============
CREATE INDEX IF NOT EXISTS idx_mon_risk_identity_time
    ON mon.risk_event (identity_code, event_time DESC);
CREATE INDEX IF NOT EXISTS idx_mon_risk_plate_time
    ON mon.risk_event (plate_no, event_time DESC);
