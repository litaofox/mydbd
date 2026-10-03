-- 05 操作审计日志（F34 / MOD-AUDIT-001）
-- 只追加表：应用层不提供 UPDATE/DELETE 接口；不继承 BaseEntity 公共字段（无 updater/update_date/valid_mark）

CREATE TABLE IF NOT EXISTS traj.sys_audit_log (
    id              bigint       PRIMARY KEY,                 -- 应用雪花ID
    trace_id        varchar(48),                              -- 单次请求追踪ID
    user_name       varchar(64),                              -- 操作人；登录失败时为请求中用户名；匿名为 NULL
    module          varchar(32)   NOT NULL,                   -- 模块编码 AUTH/MDM/MONITOR/CEP/WORK_ORDER/VIDEO/AUDIT...
    action          varchar(32)   NOT NULL,                   -- 动作编码 LOGIN/LOGIN_FAIL/CREATE/UPDATE/DELETE/EXPORT/VIDEO_VIEW/HANDLE/QUERY
    action_name     varchar(64),                              -- 动作中文名快照
    object_type     varchar(32),                              -- 对象类型 VEHICLE/TERMINAL/DRIVER/DEPT/USER...
    object_id       varchar(64),                              -- 对象主键（SpEL 提取，字符串存储）
    request_method  varchar(8),                               -- GET/POST/PUT/DELETE
    request_uri     varchar(256),                             -- 不含 query 的路径
    query_string    varchar(512),                             -- 查询串（截断）
    request_body    text,                                     -- 脱敏并截断 2000 字符后的 JSON
    status          smallint      NOT NULL,                   -- 1=成功 0=失败
    result_code     integer,                                  -- 业务 code（0/40901/40101...）
    error_msg       varchar(500),                             -- 失败消息（截断）
    cost_ms         integer,                                  -- 接口耗时毫秒
    client_ip       varchar(45),                              -- X-Forwarded-For 首段，回落 remoteAddr
    user_agent      varchar(256),                             -- User-Agent（截断）
    content_hash    varchar(64),                              -- 本行规范化内容 SHA-256 行级指纹
    create_time     timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP  -- 事件产生时刻
);

COMMENT ON TABLE  traj.sys_audit_log IS '操作审计日志（只追加，F34）';
COMMENT ON COLUMN traj.sys_audit_log.trace_id IS '一次请求一个，关联同请求产生的多条记录';
COMMENT ON COLUMN traj.sys_audit_log.status IS '1=成功，0=失败（HTTP>=400 或业务 code 非 0）';
COMMENT ON COLUMN traj.sys_audit_log.content_hash IS '本行规范化字段串 SHA-256，行级防篡改指纹（本期不做哈希链）';

CREATE INDEX IF NOT EXISTS idx_audit_time       ON traj.sys_audit_log (create_time DESC);
CREATE INDEX IF NOT EXISTS idx_audit_user_time  ON traj.sys_audit_log (user_name, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_audit_module_act ON traj.sys_audit_log (module, action, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_audit_object     ON traj.sys_audit_log (object_type, object_id);
