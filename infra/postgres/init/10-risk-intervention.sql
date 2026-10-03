-- F21 坐席干预记录
CREATE TABLE IF NOT EXISTS mon.risk_intervention (
    id            bigserial PRIMARY KEY,
    order_id      bigint NOT NULL,
    event_id      bigint,
    plate_no      varchar(40),
    identity_code varchar(100),
    action_type   varchar(20) NOT NULL,
    action_result varchar(20) NOT NULL DEFAULT 'SUCCESS',
    operator_id   bigint,
    operator_name varchar(64),
    source        varchar(16) NOT NULL DEFAULT 'MANUAL',
    remark        varchar(500),
    create_date   timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_intervention_order ON mon.risk_intervention(order_id);
CREATE INDEX IF NOT EXISTS idx_intervention_event ON mon.risk_intervention(event_id);
CREATE INDEX IF NOT EXISTS idx_intervention_time ON mon.risk_intervention(create_date);
