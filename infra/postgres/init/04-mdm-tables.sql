-- 04 主数据管理（MDM）：组织架构表 + 车-终端/车-司机绑定互斥索引
-- 依据 MOD-MDM-001 主数据管理模块设计

-- 4.1 企业与组织架构表
CREATE TABLE IF NOT EXISTS traj.traj_dept (
    id             bigserial    PRIMARY KEY,
    parent_id      bigint       NOT NULL DEFAULT 0,
    dept_name      varchar(60)  NOT NULL,
    dept_code      varchar(40),
    dept_type      smallint     NOT NULL DEFAULT 2,   -- 1=运输企业 2=车队/部门
    contact_person varchar(30),
    contact_phone  varchar(30),
    province_code  varchar(16),
    city_code      varchar(16),
    county_code    varchar(16),
    address        varchar(120),
    sort_no        integer      NOT NULL DEFAULT 0,
    valid_mark     smallint     NOT NULL DEFAULT 1,
    creator        varchar(40)  NOT NULL,
    create_date    timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater        varchar(40),
    update_date    timestamp
);
COMMENT ON TABLE traj.traj_dept IS '企业与组织架构表';
COMMENT ON COLUMN traj.traj_dept.parent_id IS '上级组织ID，0=根节点';
COMMENT ON COLUMN traj.traj_dept.dept_type IS '1=运输企业 2=车队/部门';
CREATE INDEX IF NOT EXISTS idx_traj_dept_parent ON traj.traj_dept (parent_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_traj_dept_code
    ON traj.traj_dept (dept_code) WHERE valid_mark = 1 AND dept_code IS NOT NULL;

-- 4.2 种子部门（与 02 脚本中车辆 dept_id=1/2 对齐）
INSERT INTO traj.traj_dept (id, parent_id, dept_name, dept_type, sort_no, creator)
VALUES
    (1, 0, '北京物流公司', 1, 1, 'system'),
    (2, 0, '北京配送中心', 1, 2, 'system')
ON CONFLICT (id) DO NOTHING;
SELECT setval(pg_get_serial_sequence('traj.traj_dept', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 1) FROM traj.traj_dept), 2), true);

-- 4.3 绑定关系互斥索引（并发场景下的最终防线）
-- 一辆车同一时刻仅一台有效终端
CREATE UNIQUE INDEX IF NOT EXISTS uk_traj_vt_vehicle_active
    ON traj.traj_vehicle_terminal (vehicle_id) WHERE status = 1 AND valid_mark = 1;
-- 一台终端同一时刻仅绑定一辆车
CREATE UNIQUE INDEX IF NOT EXISTS uk_traj_vt_terminal_active
    ON traj.traj_vehicle_terminal (terminal_id) WHERE status = 1 AND valid_mark = 1;
-- 一辆车同一时刻仅一名有效主班司机
CREATE UNIQUE INDEX IF NOT EXISTS uk_traj_vd_vehicle_main
    ON traj.traj_vehicle_driver (vehicle_id)
    WHERE status = 1 AND valid_mark = 1 AND driver_type = 1;
