-- 08 F20 处置工单流转：工单 / 流转日志 / SLA / 菜单权限 / 存量迁移
-- 依赖：03-mon-tables.sql（mon.risk_event）、06-iam-tables.sql（traj.sys_*）、07-risk-tables.sql

-- =====================================================================
-- 8.1 工单号流水与格式化函数
-- =====================================================================
CREATE SEQUENCE IF NOT EXISTS mon.seq_risk_order_no;

CREATE OR REPLACE FUNCTION mon.fmt_order_no() RETURNS text AS $$
    SELECT 'WO' || to_char(CURRENT_TIMESTAMP, 'YYYYMMDD') ||
           lpad((nextval('mon.seq_risk_order_no') % 1000000)::text, 6, '0');
$$ LANGUAGE sql;

-- =====================================================================
-- 8.2 处置工单（与 risk_event 1:1）
-- =====================================================================
CREATE TABLE IF NOT EXISTS mon.risk_work_order (
    id             bigserial    PRIMARY KEY,
    order_no       varchar(24)  NOT NULL,
    event_id       bigint       NOT NULL,
    -- 事件快照（列表免 join）
    event_title    varchar(100),
    event_code     varchar(40),
    event_source   varchar(16),
    plate_no       varchar(40),
    identity_code  varchar(100),
    risk_level     smallint     NOT NULL DEFAULT 2,   -- 1低 2中 3高
    event_time     timestamp,
    -- 状态机
    status         varchar(16)  NOT NULL DEFAULT 'PENDING', -- PENDING/PROCESSING/CLOSED
    assignee_id    bigint,
    assignee_name  varchar(64),
    assign_time    timestamp,
    claim_time     timestamp,
    close_time     timestamp,
    close_result   varchar(20),
    close_remark   varchar(255),
    -- SLA
    deadline       timestamp,
    sla_limit_min  integer,
    grace_min      integer,
    overdue        smallint     NOT NULL DEFAULT 0,
    escalated      smallint     NOT NULL DEFAULT 0,
    escalate_time  timestamp,
    reopen_count   smallint     NOT NULL DEFAULT 0,
    valid_mark     smallint     NOT NULL DEFAULT 1,
    creator        varchar(64),
    create_date    timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater        varchar(64),
    update_date    timestamp,
    CONSTRAINT uk_risk_order_event UNIQUE (event_id)
);
COMMENT ON TABLE mon.risk_work_order IS 'F20 处置工单（与 risk_event 1:1）';
COMMENT ON COLUMN mon.risk_work_order.status IS 'PENDING 待处理 / PROCESSING 处理中 / CLOSED 已闭环';
COMMENT ON COLUMN mon.risk_work_order.close_result IS 'PHONE_REMIND/EDUCATION/SUSPEND/FALSE_ALARM/OTHER';

CREATE UNIQUE INDEX IF NOT EXISTS uk_risk_order_no ON mon.risk_work_order (order_no);
CREATE INDEX IF NOT EXISTS idx_risk_order_status    ON mon.risk_work_order (status, valid_mark);
CREATE INDEX IF NOT EXISTS idx_risk_order_assignee  ON mon.risk_work_order (assignee_id);
CREATE INDEX IF NOT EXISTS idx_risk_order_deadline  ON mon.risk_work_order (deadline);
CREATE INDEX IF NOT EXISTS idx_risk_order_escal     ON mon.risk_work_order (escalated, overdue);
CREATE INDEX IF NOT EXISTS idx_risk_order_eventtime ON mon.risk_work_order (event_time DESC);

SELECT setval(pg_get_serial_sequence('mon.risk_work_order', 'id'),
              COALESCE((SELECT MAX(id) FROM mon.risk_work_order), 1), true);

-- =====================================================================
-- 8.3 工单流转日志
-- =====================================================================
CREATE TABLE IF NOT EXISTS mon.risk_order_log (
    id             bigserial   PRIMARY KEY,
    order_id       bigint      NOT NULL,
    action         varchar(16) NOT NULL,  -- CREATE/ASSIGN/CLAIM/TRANSFER/CLOSE/REOPEN/ESCALATE
    from_status    varchar(16),
    to_status      varchar(16),
    from_user_id   bigint,
    to_user_id     bigint,
    from_user_name varchar(64),
    to_user_name   varchar(64),
    remark         varchar(255),
    operator_id    bigint,
    operator_name  varchar(64),
    create_date    timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE mon.risk_order_log IS 'F20 工单流转日志';
CREATE INDEX IF NOT EXISTS idx_risk_order_log_order ON mon.risk_order_log (order_id, id);

-- =====================================================================
-- 8.4 SLA 配置（按风险等级：处置时限分钟 / 升级宽限分钟）
-- =====================================================================
CREATE TABLE IF NOT EXISTS mon.risk_order_sla (
    risk_level  smallint PRIMARY KEY,
    limit_min   integer NOT NULL,
    grace_min   integer NOT NULL,
    update_date timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE mon.risk_order_sla IS 'F20 工单处置时限（高 15/中 60/低 240，宽限 10/30/60）';
INSERT INTO mon.risk_order_sla (risk_level, limit_min, grace_min) VALUES
    (3, 15, 10),
    (2, 60, 30),
    (1, 240, 60)
ON CONFLICT (risk_level) DO NOTHING;

-- =====================================================================
-- 8.5 存量未处置事件补单（幂等；已处置历史事件不补单）
-- =====================================================================
INSERT INTO mon.risk_work_order
    (order_no, event_id, event_title, event_code, event_source, plate_no, identity_code,
     risk_level, event_time, status, deadline, sla_limit_min, grace_min, creator, create_date)
SELECT mon.fmt_order_no(), e.id, e.title, e.event_code, e.event_source, e.plate_no, e.identity_code,
       e.risk_level, COALESCE(e.event_time, e.create_date), 'PENDING',
       COALESCE(e.event_time, e.create_date) + s.limit_min * interval '1 minute',
       s.limit_min, s.grace_min, 'system', CURRENT_TIMESTAMP
FROM mon.risk_event e
JOIN mon.risk_order_sla s ON s.risk_level = COALESCE(e.risk_level, 2)
WHERE e.handle_status = 0
ON CONFLICT (event_id) DO NOTHING;

INSERT INTO mon.risk_order_log (order_id, action, to_status, operator_name, remark)
SELECT w.id, 'CREATE', 'PENDING', 'SYSTEM', '存量未处置事件迁移补单'
FROM mon.risk_work_order w
WHERE NOT EXISTS (
    SELECT 1 FROM mon.risk_order_log l WHERE l.order_id = w.id AND l.action = 'CREATE'
);

-- =====================================================================
-- 8.6 菜单与权限点（14 菜单；915 工单处置；916 分派/督办）
-- =====================================================================
INSERT INTO traj.sys_menu (id, parent_id, menu_name, menu_type, perm_code, path, icon, sort_no, visible, status)
VALUES
    (14,  10, '处置工单',       2, 'risk:order:view',   '/risk/orders', 'Tickets', 14, 1, 1),
    (915, 14, '工单处置',       3, 'risk:order:handle', NULL, NULL, 1, 1, 1),
    (916, 14, '工单分派/督办',  3, 'risk:order:assign', NULL, NULL, 2, 1, 1)
ON CONFLICT (id) DO NOTHING;

SELECT setval(pg_get_serial_sequence('traj.sys_menu', 'id'),
              (SELECT MAX(id) FROM traj.sys_menu), true);

-- 超级管理员：全量幂等补齐
INSERT INTO traj.sys_role_menu (role_id, menu_id)
SELECT 1, id FROM traj.sys_menu
ON CONFLICT DO NOTHING;

-- 安全管理员：查看+处置+分派督办
INSERT INTO traj.sys_role_menu (role_id, menu_id)
VALUES
    (2, 14), (2, 915), (2, 916)
ON CONFLICT DO NOTHING;

-- 调度监控员 / 企业车队长：查看+处置
INSERT INTO traj.sys_role_menu (role_id, menu_id)
VALUES
    (3, 14), (3, 915),
    (4, 14), (4, 915)
ON CONFLICT DO NOTHING;
