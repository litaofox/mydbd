-- ============================================================
-- 12 F15 监控总览大屏：菜单 15 + 角色授权（RES-DBD-001 §3.2/§3.3 锁定）
-- 幂等：ON CONFLICT DO NOTHING；可重复执行
-- 注意：monitor.online.window.minutes 不在本脚本种子（RES 锁定本脚本仅菜单+授权），
--       后端读取缺省回落 5，需要调整时由运维在 F35 系统参数页新增该键。
-- ============================================================

INSERT INTO traj.sys_menu (id, parent_id, menu_name, menu_type, perm_code, path, icon, sort_no, visible, status)
VALUES (15, 10, '监控总览大屏', 2, 'monitor:dashboard:view', '/dashboard', 'DataBoard', 8, 1, 1)
ON CONFLICT (id) DO NOTHING;

SELECT setval(pg_get_serial_sequence('traj.sys_menu', 'id'),
              (SELECT MAX(id) FROM traj.sys_menu), true);

-- 授权 role1~4 全员（RES-DBD-001 §3.3）
INSERT INTO traj.sys_role_menu (role_id, menu_id)
VALUES (1, 15), (2, 15), (3, 15), (4, 15)
ON CONFLICT DO NOTHING;
