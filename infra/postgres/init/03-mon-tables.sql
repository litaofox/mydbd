-- 03 mon 监控分析域表结构

-- 3.1 风险预警事件
CREATE TABLE IF NOT EXISTS mon.risk_event (
    id            bigserial      PRIMARY KEY,
    event_code    varchar(40),                  -- ADAS/DSM/北斗事件码
    event_source  varchar(16),                  -- ADAS / DSM / national / vendor ...
    plate_no      varchar(40),
    identity_code varchar(100),
    event_time    timestamp,
    lng           numeric(10, 6),
    lat           numeric(10, 6),
    speed         integer,
    risk_level    smallint      DEFAULT 2,      -- 1低 2中 3高
    confidence    numeric(5, 2),
    media_url     varchar(255),
    handle_status smallint      NOT NULL DEFAULT 0,
    handle_remark varchar(255),
    create_date   timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE mon.risk_event IS '风险预警事件（ADAS/DSM/北斗）';
CREATE INDEX IF NOT EXISTS idx_mon_risk_time ON mon.risk_event (event_time DESC);
CREATE INDEX IF NOT EXISTS idx_mon_risk_level ON mon.risk_event (risk_level);
CREATE INDEX IF NOT EXISTS idx_mon_risk_status ON mon.risk_event (handle_status);

-- 3.2 驾驶员监控视频分析任务
CREATE TABLE IF NOT EXISTS mon.video_analysis (
    id             bigserial    PRIMARY KEY,
    task_id        varchar(64)  NOT NULL,
    plate_no       varchar(40),
    channel        varchar(16),                 -- DSM / ADAS
    clip_url       varchar(255),
    status         varchar(16)  NOT NULL DEFAULT 'PENDING',
    result_summary varchar(500),
    event_count    integer      NOT NULL DEFAULT 0,
    create_date    timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_date    timestamp
);
COMMENT ON TABLE mon.video_analysis IS '驾驶员监控视频分析任务';
CREATE UNIQUE INDEX IF NOT EXISTS uk_mon_video_task ON mon.video_analysis (task_id);

-- ===== 种子事件（页面首屏演示；加载样例后会追加样例事件） =====
INSERT INTO mon.risk_event
    (event_code, event_source, plate_no, event_time, lng, lat, speed, risk_level, confidence, handle_status)
VALUES
('DSM_FATIGUE',     'DSM', '京A12345', CURRENT_TIMESTAMP - interval '15 minute', 116.4210, 39.9135, 62, 3, 0.91, 0),
('ADAS_FCW',        'ADAS','京A23456', CURRENT_TIMESTAMP - interval '42 minute', 116.3882, 39.9017, 78, 3, 0.86, 0),
('DSM_DISTRACTION', 'DSM', '京B45678', CURRENT_TIMESTAMP - interval '70 minute', 116.4521, 39.9280, 35, 2, 0.79, 1),
('ADAS_LDW',        'ADAS','京A34567', CURRENT_TIMESTAMP - interval '2 hour',   116.3670, 39.8950, 54, 2, 0.83, 0),
('BEIDOU_SPEED',    '北斗','京B56789', CURRENT_TIMESTAMP - interval '3 hour',   116.4900, 39.9420, 96, 2, 1.00, 1);
