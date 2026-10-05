-- =====================================================================
-- 17-analysis-menu.sql
-- 查询分析功能菜单占位（对标 FUNC-REF-001：报表查询 13 项 + 统计分析 6 项）
-- 点击菜单跳转 /analysis/coming/<code>，前端渲染"功能建设中"占位页。
-- 已实现的 301 驾驶评分 / 302 风险趋势与画像保持不动。
-- 幂等：ON CONFLICT (id) DO UPDATE，可重复执行。
-- =====================================================================

-- 1. 菜单节点 ----------------------------------------------------------
INSERT INTO traj.sys_menu (id, parent_id, menu_name, menu_type, perm_code, path, icon, sort_no, visible, status) VALUES
-- 分组目录（挂在 300 分析决策下）
(310, 300, '报表查询', 1, NULL, '', 'Document',    33, 1, 1),
(340, 300, '统计分析', 1, NULL, '', 'DataAnalysis', 34, 1, 1),
-- 报表查询 13 项
(311, 310, '轨迹报表',     2, NULL, '/analysis/coming/track-report',      NULL,  1, 1, 1),
(312, 310, '里程报表',     2, NULL, '/analysis/coming/mileage',           NULL,  2, 1, 1),
(313, 310, '行驶报表',     2, NULL, '/analysis/coming/driving',           NULL,  3, 1, 1),
(314, 310, '停车报表',     2, NULL, '/analysis/coming/parking',           NULL,  4, 1, 1),
(315, 310, '怠速报表',     2, NULL, '/analysis/coming/idling',            NULL,  5, 1, 1),
(316, 310, '司机驾驶报表', 2, NULL, '/analysis/coming/driver-driving',    NULL,  6, 1, 1),
(317, 310, '离线报表',     2, NULL, '/analysis/coming/offline',           NULL,  7, 1, 1),
(318, 310, '故障车报表',   2, NULL, '/analysis/coming/faulty-vehicle',    NULL,  8, 1, 1),
(319, 310, '油耗报表',     2, NULL, '/analysis/coming/fuel-consumption',  NULL,  9, 1, 1),
(320, 310, '加油报表',     2, NULL, '/analysis/coming/refuel',            NULL, 10, 1, 1),
(321, 310, '实时在线率',   2, NULL, '/analysis/coming/online-rate',       NULL, 11, 1, 1),
(322, 310, '车辆工作情况', 2, NULL, '/analysis/coming/vehicle-work',      NULL, 12, 1, 1),
(323, 310, '服务费报表',   2, NULL, '/analysis/coming/service-charge',    NULL, 13, 1, 1),
-- 统计分析 6 项
(341, 340, '车辆分析',     2, NULL, '/analysis/coming/vehicle-analysis',      NULL, 1, 1, 1),
(342, 340, '企业分析',     2, NULL, '/analysis/coming/enterprise-analysis',   NULL, 2, 1, 1),
(343, 340, '企业安全分析', 2, NULL, '/analysis/coming/ent-security-analysis', NULL, 3, 1, 1),
(344, 340, '司机分析',     2, NULL, '/analysis/coming/driver-analysis',       NULL, 4, 1, 1),
(345, 340, '车辆商机分析', 2, NULL, '/analysis/coming/business-opportunity',  NULL, 5, 1, 1),
(346, 340, '轨迹完整率',   2, NULL, '/analysis/coming/track-integrity',       NULL, 6, 1, 1)
ON CONFLICT (id) DO UPDATE SET
    parent_id = EXCLUDED.parent_id,
    menu_name = EXCLUDED.menu_name,
    menu_type = EXCLUDED.menu_type,
    perm_code = EXCLUDED.perm_code,
    path      = EXCLUDED.path,
    icon      = EXCLUDED.icon,
    sort_no   = EXCLUDED.sort_no,
    visible   = EXCLUDED.visible,
    status    = EXCLUDED.status;

-- 2. 序列对齐 ----------------------------------------------------------
SELECT setval(pg_get_serial_sequence('traj.sys_menu', 'id'),
              (SELECT MAX(id) FROM traj.sys_menu), true);

-- 3. 角色授权 ----------------------------------------------------------
-- 注意：用户导航查询不自动补全祖先，300/310/340 目录节点须一并授权。
-- 全部 4 个角色可见（只读占位，无数据暴露），方便省份账号后续测试。
-- 301/302 既有授权不变（仅 SUPER_ADMIN/SAFE_ADMIN）。
INSERT INTO traj.sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
  FROM (VALUES (1), (2), (3), (4)) AS r(id)
 CROSS JOIN (
        SELECT unnest(ARRAY[310, 340,
                           311, 312, 313, 314, 315, 316, 317, 318, 319, 320, 321, 322, 323,
                           341, 342, 343, 344, 345, 346]) AS id
       ) AS m
ON CONFLICT DO NOTHING;

-- 分析决策根目录：补授调度监控员 / 企业车队长
INSERT INTO traj.sys_role_menu (role_id, menu_id) VALUES
    (3, 300),
    (4, 300)
ON CONFLICT DO NOTHING;
