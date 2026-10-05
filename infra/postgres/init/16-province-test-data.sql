-- 16 省份测试数据：上海/山东/江苏/安徽 4 个省级企业节点
-- 用途：作为"每省 2 个测试用户"的部门锚点，支撑部门数据范围测试
-- 说明：用户与角色关系由 module-iam 的 ProvinceTestUserInitializer 创建（密码不写死在 SQL）
-- 2026-10-05：由 31 省精简为 4 省（其余 27 省级节点与用户已按确认从环境删除）

INSERT INTO traj.traj_dept (parent_id, dept_name, dept_code, dept_type, province_code, sort_no, creator)
VALUES
    (0, '上海市', 'PROV_310000', 1, '310000', 109, 'system'),
    (0, '江苏省', 'PROV_320000', 1, '320000', 110, 'system'),
    (0, '安徽省', 'PROV_340000', 1, '340000', 112, 'system'),
    (0, '山东省', 'PROV_370000', 1, '370000', 115, 'system')
ON CONFLICT DO NOTHING;

-- 演示数据中的 4 个企业（10 山东/20 江苏/30 上海/40 安徽）挂到对应省级节点
UPDATE traj.traj_dept c SET parent_id = p.id
  FROM traj.traj_dept p
 WHERE c.parent_id = 0
   AND ((c.id = 10 AND p.dept_code = 'PROV_370000')
     OR (c.id = 20 AND p.dept_code = 'PROV_320000')
     OR (c.id = 30 AND p.dept_code = 'PROV_310000')
     OR (c.id = 40 AND p.dept_code = 'PROV_340000'));
