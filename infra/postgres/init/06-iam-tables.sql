-- 06 用户与权限（IAM）：用户/角色/菜单权限点 + 关系表（F33）
-- 依据 MOD-IAM-001 用户与权限模块设计
-- 说明：admin 超管用户由后端 module-iam 首启 DataInitializer 自动创建（不把密码哈希写死在 SQL）

-- 6.1 用户表
CREATE TABLE IF NOT EXISTS traj.sys_user (
    id                  bigserial    PRIMARY KEY,
    username            varchar(40)  NOT NULL,
    password_hash       varchar(120) NOT NULL,
    real_name           varchar(40)  NOT NULL,
    phone               varchar(20),
    email               varchar(80),
    dept_id             bigint,
    status              smallint     NOT NULL DEFAULT 1,   -- 1=启用 0=停用
    mfa_enabled         smallint     NOT NULL DEFAULT 0,   -- 1=已开通 TOTP
    mfa_secret          varchar(64),                       -- 已确认生效的 Base32 密钥
    mfa_pending_secret  varchar(64),                       -- 绑定流程中待确认密钥
    fail_count          integer      NOT NULL DEFAULT 0,
    locked_until        timestamp,
    pwd_update_time     timestamp,
    last_login_time     timestamp,
    last_login_ip       varchar(45),
    remark              varchar(200),
    valid_mark          smallint     NOT NULL DEFAULT 1,
    creator             varchar(40)  NOT NULL,
    create_date         timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater             varchar(40),
    update_date         timestamp
);
COMMENT ON TABLE traj.sys_user IS '系统用户表（IAM）';
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_user_username
    ON traj.sys_user (username) WHERE valid_mark = 1;
CREATE INDEX IF NOT EXISTS idx_sys_user_dept ON traj.sys_user (dept_id);
CREATE INDEX IF NOT EXISTS idx_sys_user_status ON traj.sys_user (status);

-- 6.2 角色表
CREATE TABLE IF NOT EXISTS traj.sys_role (
    id          bigserial    PRIMARY KEY,
    role_code   varchar(40)  NOT NULL,
    role_name   varchar(40)  NOT NULL,
    data_scope  smallint     NOT NULL DEFAULT 3,  -- 1=全部 2=本企业及以下 3=本部门及以下 4=仅本部门 5=自定义
    built_in    smallint     NOT NULL DEFAULT 0,  -- 1=内置（禁删、禁改编码/数据范围）
    status      smallint     NOT NULL DEFAULT 1,
    remark      varchar(200),
    valid_mark  smallint     NOT NULL DEFAULT 1,
    creator     varchar(40)  NOT NULL,
    create_date timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     varchar(40),
    update_date timestamp
);
COMMENT ON TABLE traj.sys_role IS '系统角色表（IAM）';
COMMENT ON COLUMN traj.sys_role.data_scope IS '1=全部 2=本企业及以下 3=本部门及以下 4=仅本部门 5=自定义';
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_role_code
    ON traj.sys_role (role_code) WHERE valid_mark = 1;

-- 6.3 菜单/权限点表（三级：1目录 2菜单 3按钮；版本种子化，不做软删）
CREATE TABLE IF NOT EXISTS traj.sys_menu (
    id          bigserial    PRIMARY KEY,
    parent_id   bigint       NOT NULL DEFAULT 0,
    menu_name   varchar(40)  NOT NULL,
    menu_type   smallint     NOT NULL,         -- 1=目录 2=菜单 3=按钮
    perm_code   varchar(60),
    path        varchar(120),
    icon        varchar(40),
    sort_no     integer      NOT NULL DEFAULT 0,
    visible     smallint     NOT NULL DEFAULT 1,
    status      smallint     NOT NULL DEFAULT 1,
    create_date timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE traj.sys_menu IS '菜单与按钮权限点表（IAM 种子维护）';
COMMENT ON COLUMN traj.sys_menu.menu_type IS '1=目录 2=菜单 3=按钮';
CREATE INDEX IF NOT EXISTS idx_sys_menu_parent ON traj.sys_menu (parent_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_menu_perm
    ON traj.sys_menu (perm_code) WHERE perm_code IS NOT NULL;

-- 6.4 关系表
CREATE TABLE IF NOT EXISTS traj.sys_user_role (
    user_id bigint NOT NULL,
    role_id bigint NOT NULL,
    PRIMARY KEY (user_id, role_id)
);
COMMENT ON TABLE traj.sys_user_role IS '用户-角色关系表';

CREATE TABLE IF NOT EXISTS traj.sys_role_menu (
    role_id bigint NOT NULL,
    menu_id bigint NOT NULL,
    PRIMARY KEY (role_id, menu_id)
);
COMMENT ON TABLE traj.sys_role_menu IS '角色-菜单/权限点关系表';

CREATE TABLE IF NOT EXISTS traj.sys_role_dept (
    role_id bigint NOT NULL,
    dept_id bigint NOT NULL,
    PRIMARY KEY (role_id, dept_id)
);
COMMENT ON TABLE traj.sys_role_dept IS '角色-自定义数据范围部门关系表（data_scope=5）';

-- =====================================================================
-- 种子数据（幂等）
-- =====================================================================

-- 6.5 菜单/权限点（显式 id）
INSERT INTO traj.sys_menu (id, parent_id, menu_name, menu_type, perm_code, path, icon, sort_no, visible, status)
VALUES
    -- 实时监控（10 组）
    (10,  0,   '实时监控',     1, NULL,               '/monitor-group', 'Monitor',      10, 1, 1),
    (11,  10,  '实时导航监控', 2, 'monitor:view',     '/monitor',       'LocationFilled', 11, 1, 1),
    (12,  10,  '历史轨迹回放', 2, 'playback:view',    '/playback',      'VideoPlay',    12, 1, 1),
    (13,  10,  '风险预警分析', 2, 'risk:view',        '/risk',          'Warning',      13, 1, 1),
    -- 主数据管理（100 组）
    (100, 0,   '主数据管理',   1, NULL,               '/mdm-group',     'FolderOpened', 20, 1, 1),
    (101, 100, '组织架构',     2, 'mdm:org:view',     '/mdm/org',       'OfficeBuilding',21, 1, 1),
    (102, 100, '车辆档案',     2, 'mdm:vehicle:view', '/mdm/vehicles',  'Van',          22, 1, 1),
    (103, 100, '终端档案',     2, 'mdm:terminal:view','/mdm/terminals', 'Cellphone',    23, 1, 1),
    (104, 100, '驾驶员档案',   2, 'mdm:driver:view',  '/mdm/drivers',   'User',         24, 1, 1),
    (201, 101, '组织维护',     3, 'mdm:org:edit',     NULL, NULL, 1, 1, 1),
    (202, 102, '车辆维护',     3, 'mdm:vehicle:edit', NULL, NULL, 1, 1, 1),
    (203, 103, '终端维护',     3, 'mdm:terminal:edit',NULL, NULL, 1, 1, 1),
    (204, 104, '驾驶员维护',   3, 'mdm:driver:edit',  NULL, NULL, 1, 1, 1),
    -- 系统管理（900 组）
    (900, 0,   '系统管理',     1, NULL,               '/system-group',  'Setting',      90, 1, 1),
    (901, 900, '用户管理',     2, 'iam:user:view',   '/system/users',  'UserFilled',   91, 1, 1),
    (902, 900, '角色管理',     2, 'iam:role:view',   '/system/roles',  'Avatar',       92, 1, 1),
    (903, 900, '操作审计',     2, 'audit:view',      '/system/audit',  'DocumentChecked',93,1, 1),
    (911, 901, '用户维护',     3, 'iam:user:edit',   NULL, NULL, 1, 1, 1),
    (912, 902, '角色维护',     3, 'iam:role:edit',   NULL, NULL, 1, 1, 1)
ON CONFLICT (id) DO NOTHING;
SELECT setval(pg_get_serial_sequence('traj.sys_menu', 'id'),
              (SELECT MAX(id) FROM traj.sys_menu), true);

-- 6.6 内置角色（显式 id）
INSERT INTO traj.sys_role (id, role_code, role_name, data_scope, built_in, status, remark, creator)
VALUES
    (1, 'SUPER_ADMIN',    '超级管理员',   1, 1, 1, '系统内置：全部菜单与全部数据，后端硬编码绕过', 'system'),
    (2, 'SAFE_ADMIN',     '安全管理员',   1, 1, 1, '系统内置：用户/角色管理、操作审计，不参与车辆业务', 'system'),
    (3, 'DISPATCHER',     '调度监控员',   3, 1, 1, '系统内置：监控/回放/风险与主数据只读，限本部门及以下', 'system'),
    (4, 'FLEET_CAPTAIN',  '企业车队长',   2, 1, 1, '系统内置：本企业及以下车辆/终端/司机档案维护', 'system')
ON CONFLICT (id) DO NOTHING;
SELECT setval(pg_get_serial_sequence('traj.sys_role', 'id'),
              (SELECT MAX(id) FROM traj.sys_role), true);

-- 6.7 角色-权限点（含祖先目录，便于直接建树）
-- 超级管理员：全部
INSERT INTO traj.sys_role_menu (role_id, menu_id)
SELECT 1, id FROM traj.sys_menu
ON CONFLICT DO NOTHING;

-- 安全管理员：系统管理目录+用户(含维护)+角色查看+审计；主数据目录+组织查看
INSERT INTO traj.sys_role_menu (role_id, menu_id)
VALUES
    (2, 900), (2, 901), (2, 911), (2, 902), (2, 903),
    (2, 100), (2, 101)
ON CONFLICT DO NOTHING;

-- 调度监控员：监控组全部、主数据组全部菜单（只读，无按钮）
INSERT INTO traj.sys_role_menu (role_id, menu_id)
VALUES
    (3, 10), (3, 11), (3, 12), (3, 13),
    (3, 100), (3, 101), (3, 102), (3, 103), (3, 104)
ON CONFLICT DO NOTHING;

-- 企业车队长：监控+风险（不含回放）、主数据全部查看 + 车辆/终端/驾驶员维护（组织仅查看）
INSERT INTO traj.sys_role_menu (role_id, menu_id)
VALUES
    (4, 10), (4, 11), (4, 13),
    (4, 100), (4, 101), (4, 102), (4, 103), (4, 104),
    (4, 202), (4, 203), (4, 204)
ON CONFLICT DO NOTHING;
