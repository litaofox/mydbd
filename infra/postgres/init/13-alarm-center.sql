-- =====================================================================
-- 13-alarm-center.sql  F17 终端报警中心（MOD-MON-004 §2.3/§2.4/§5）
-- 索引补充 + handle_status 存量归一 + alarm_handle_status 字典 + 菜单/授权
-- 全幂等，可重复执行
-- =====================================================================

-- ============ §2.3 默认列表/latest 复合索引 ============
CREATE INDEX IF NOT EXISTS idx_traj_warn_status_time
    ON traj.traj_warn_info (handle_status, start_warn_time DESC);

-- ============ §2.4 存量归一（NULL -> 0）+ 列默认值 ============
UPDATE traj.traj_warn_info SET handle_status = 0 WHERE handle_status IS NULL;
ALTER TABLE traj.traj_warn_info ALTER COLUMN handle_status SET DEFAULT 0;

-- ============ §5.1 字典 alarm_handle_status ============
INSERT INTO traj.sys_dict_type (dict_code, dict_name, status, remark)
VALUES ('alarm_handle_status', '终端报警处置状态', 1, 'F17 报警中心 handle_status')
ON CONFLICT (dict_code) DO NOTHING;

INSERT INTO traj.sys_dict_item (dict_type_id, item_label, item_value, sort)
SELECT id, '待处理', '0', 1 FROM traj.sys_dict_type WHERE dict_code = 'alarm_handle_status'
UNION ALL SELECT id, '已确认', '1', 2 FROM traj.sys_dict_type WHERE dict_code = 'alarm_handle_status'
UNION ALL SELECT id, '已解除', '2', 3 FROM traj.sys_dict_type WHERE dict_code = 'alarm_handle_status'
ON CONFLICT (dict_type_id, item_value) DO NOTHING;

-- ============ §5.2 菜单与授权（RES-DBD-001 §3.2/§3.3 锁定值） ============
INSERT INTO traj.sys_menu (id, parent_id, menu_name, menu_type, perm_code, path, icon, sort_no, visible, status)
VALUES
  (16, 10, '终端报警中心', 2, 'alarm:view',   '/alarms', 'Bell', 9, 1, 1),
  (926, 16, '报警处置',    3, 'alarm:handle', NULL, NULL, 1, 1, 1)
ON CONFLICT (id) DO NOTHING;

SELECT setval(pg_get_serial_sequence('traj.sys_menu', 'id'),
              (SELECT MAX(id) FROM traj.sys_menu), true);

-- role1 admin 全量幂等补齐
INSERT INTO traj.sys_role_menu (role_id, menu_id)
SELECT 1, id FROM traj.sys_menu ON CONFLICT DO NOTHING;

-- role2 SAFE_ADMIN / role3 DISPATCHER：查看+处置；role4 FLEET_CAPTAIN：仅查看
INSERT INTO traj.sys_role_menu (role_id, menu_id) VALUES
  (2, 16), (2, 926), (3, 16), (3, 926), (4, 16)
ON CONFLICT DO NOTHING;
