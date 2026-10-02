-- 02 traj 域表结构（依据 DDL-TRAJ-001）

-- 2.1 轨迹点
CREATE TABLE IF NOT EXISTS traj.traj_gps_point (
    id            bigserial      PRIMARY KEY,
    identity_code varchar(100)   NOT NULL,
    plate_no      varchar(50),
    gps_time      timestamp      NOT NULL,
    lng           numeric(10, 6) NOT NULL,
    lat           numeric(10, 6) NOT NULL,
    speed         integer,
    direction     integer,
    altitude      integer,
    location      geometry(Point, 4326) NOT NULL,
    alarm_flag    smallint       NOT NULL DEFAULT 0,
    mileage       numeric(12, 2),
    receive_time  timestamp      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_date   timestamp      NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE traj.traj_gps_point IS '轨迹点表';
CREATE INDEX IF NOT EXISTS idx_traj_gps_point_identity_time
    ON traj.traj_gps_point (identity_code, gps_time DESC);
CREATE INDEX IF NOT EXISTS idx_traj_gps_point_plate_time
    ON traj.traj_gps_point (plate_no, gps_time DESC);
CREATE INDEX IF NOT EXISTS idx_traj_gps_point_time
    ON traj.traj_gps_point (gps_time DESC);
CREATE INDEX IF NOT EXISTS idx_traj_gps_point_location
    ON traj.traj_gps_point USING GIST (location);

-- 2.2 车辆
CREATE TABLE IF NOT EXISTS traj.traj_vehicle (
    id                   bigserial    PRIMARY KEY,
    dept_id              bigint,
    vehicle_no           varchar(40)  NOT NULL,
    vehicle_plate_color  varchar(10)  NOT NULL,
    vin                  varchar(40),
    vehicle_type         varchar(40),
    operation_type       smallint     DEFAULT 1,
    vehicle_industry     varchar(40),
    road_license_no      varchar(64),
    province_code        varchar(16),
    city_code            varchar(16),
    county_code          varchar(16),
    vehicle_color        varchar(10),
    vehicle_brand        varchar(20),
    owner_name           varchar(20),
    owner_phone          varchar(30),
    remark               varchar(100),
    valid_mark           smallint     NOT NULL DEFAULT 1,
    creator              varchar(40)  NOT NULL,
    create_date          timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater              varchar(40),
    update_date          timestamp
);
COMMENT ON TABLE traj.traj_vehicle IS '车辆基础信息表';
CREATE INDEX IF NOT EXISTS idx_traj_vehicle_dept ON traj.traj_vehicle (dept_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_traj_vehicle_no_color
    ON traj.traj_vehicle (vehicle_no, vehicle_plate_color) WHERE valid_mark = 1;

-- 2.3 终端
CREATE TABLE IF NOT EXISTS traj.traj_terminal (
    id             bigserial    PRIMARY KEY,
    identity_code  varchar(100) NOT NULL,
    tl_mac         varchar(16),
    oem_code       varchar(40),
    tl_model       varchar(64),
    sim_account    varchar(32),
    protocol_type  varchar(8),
    equipment_type varchar(8),
    video_channel  smallint,
    status         smallint     NOT NULL DEFAULT 1,
    remark         varchar(100),
    valid_mark     smallint     NOT NULL DEFAULT 1,
    creator        varchar(40)  NOT NULL,
    create_date    timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater        varchar(40),
    update_date    timestamp
);
COMMENT ON TABLE traj.traj_terminal IS '终端信息表';
CREATE UNIQUE INDEX IF NOT EXISTS uk_traj_terminal_identity
    ON traj.traj_terminal (identity_code) WHERE valid_mark = 1;

-- 2.4 驾驶员
CREATE TABLE IF NOT EXISTS traj.traj_driver (
    id                bigserial    PRIMARY KEY,
    driver_name       varchar(40)  NOT NULL,
    sex               smallint     NOT NULL DEFAULT 1,
    idcard            varchar(30),
    contact_phone     varchar(30),
    license_code      varchar(40)  NOT NULL,
    licence_category  varchar(32),
    driver_img        varchar(128),
    status            smallint     NOT NULL DEFAULT 1,
    remark            varchar(100),
    valid_mark        smallint     NOT NULL DEFAULT 1,
    creator           varchar(40)  NOT NULL,
    create_date       timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater           varchar(40),
    update_date       timestamp
);
COMMENT ON TABLE traj.traj_driver IS '驾驶员信息表';
CREATE UNIQUE INDEX IF NOT EXISTS uk_traj_driver_license
    ON traj.traj_driver (license_code) WHERE valid_mark = 1;

-- 2.5 车辆-司机
CREATE TABLE IF NOT EXISTS traj.traj_vehicle_driver (
    id          bigserial    PRIMARY KEY,
    vehicle_id  bigint       NOT NULL,
    driver_id   bigint       NOT NULL,
    driver_type smallint     DEFAULT 1,
    bind_time   timestamp,
    unbind_time timestamp,
    status      smallint     NOT NULL DEFAULT 1,
    remark      varchar(100),
    valid_mark  smallint     NOT NULL DEFAULT 1,
    creator     varchar(40)  NOT NULL,
    create_date timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     varchar(40),
    update_date timestamp
);
COMMENT ON TABLE traj.traj_vehicle_driver IS '车辆-司机关联表';
CREATE INDEX IF NOT EXISTS idx_traj_vd_vehicle ON traj.traj_vehicle_driver (vehicle_id);
CREATE INDEX IF NOT EXISTS idx_traj_vd_driver ON traj.traj_vehicle_driver (driver_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_traj_vd_vehicle_driver
    ON traj.traj_vehicle_driver (vehicle_id, driver_id) WHERE valid_mark = 1;

-- 2.6 车辆-终端
CREATE TABLE IF NOT EXISTS traj.traj_vehicle_terminal (
    id           bigserial    PRIMARY KEY,
    vehicle_id   bigint       NOT NULL,
    terminal_id  bigint       NOT NULL,
    bind_type    smallint     NOT NULL DEFAULT 1,
    bind_time    timestamp,
    install_time timestamp,
    installer    varchar(40),
    unbind_time  timestamp,
    status       smallint     NOT NULL DEFAULT 1,
    remark       varchar(255),
    valid_mark   smallint     NOT NULL DEFAULT 1,
    creator      varchar(40)  NOT NULL,
    create_date  timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      varchar(40),
    update_date  timestamp
);
COMMENT ON TABLE traj.traj_vehicle_terminal IS '车辆-终端关联表';
CREATE INDEX IF NOT EXISTS idx_traj_vt_vehicle ON traj.traj_vehicle_terminal (vehicle_id);
CREATE INDEX IF NOT EXISTS idx_traj_vt_terminal ON traj.traj_vehicle_terminal (terminal_id);

-- 2.7 报警信息
CREATE TABLE IF NOT EXISTS traj.traj_warn_info (
    id                  bigserial    PRIMARY KEY,
    source_id           varchar(50)  NOT NULL,
    plate_no            varchar(50)  NOT NULL,
    identity_code       varchar(100),
    start_warn_time     timestamp,
    end_warn_time       timestamp,
    start_gps_time      timestamp,
    end_gps_time        timestamp,
    start_lng           varchar(50),
    start_lat           varchar(50),
    end_lng             varchar(50),
    end_lat             varchar(50),
    start_speed         integer,
    end_speed           integer,
    type_id             integer      NOT NULL,
    warn_continue_mark  smallint,
    rule_id             bigint       NOT NULL DEFAULT 0,
    handle_status       smallint,
    handle_result_code  varchar(2),
    handle_result_msg   varchar(200),
    handler             varchar(30),
    creator             varchar(30),
    create_date         timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater             varchar(30),
    update_date         timestamp
);
COMMENT ON TABLE traj.traj_warn_info IS '报警信息表';
CREATE INDEX IF NOT EXISTS idx_traj_warn_plate_time ON traj.traj_warn_info (plate_no, start_warn_time DESC);
CREATE INDEX IF NOT EXISTS idx_traj_warn_type ON traj.traj_warn_info (type_id);
CREATE INDEX IF NOT EXISTS idx_traj_warn_status ON traj.traj_warn_info (handle_status);

-- 2.8 抓拍图片
CREATE TABLE IF NOT EXISTS traj.traj_gps_photo (
    photo_id      bigserial    PRIMARY KEY,
    truck_id      bigint,
    identity_code varchar(100),
    channel_id    integer,
    file_name     varchar(100),
    url           varchar(255),
    photo_size    integer,
    track_id      varchar(64),
    task_id       varchar(100),
    req_source    varchar(20),
    cmd_time      timestamp,
    gps_time      timestamp,
    receive_time  timestamp,
    create_time   timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE traj.traj_gps_photo IS '车辆拍照信息表';
CREATE INDEX IF NOT EXISTS idx_traj_photo_truck ON traj.traj_gps_photo (truck_id);
CREATE INDEX IF NOT EXISTS idx_traj_photo_file ON traj.traj_gps_photo (file_name);

-- 2.9 报警类型字典
CREATE TABLE IF NOT EXISTS traj.base_warn_type (
    id          integer      PRIMARY KEY,
    pid         integer      NOT NULL DEFAULT -1,
    name        varchar(50)  NOT NULL,
    grade_level smallint     NOT NULL DEFAULT 2,
    valid_mark  smallint     NOT NULL DEFAULT 1,
    creator     varchar(40)  NOT NULL DEFAULT 'system',
    create_date timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ===== 种子数据 =====
INSERT INTO traj.traj_vehicle (id, dept_id, vehicle_no, vehicle_plate_color, vin, vehicle_type, operation_type, vehicle_brand, owner_name, creator)
VALUES
(1, 1, '京A12345', '蓝色', 'LSVAM4187C2014001', '重型货车', 1, '解放', '北京物流公司', 'system'),
(2, 1, '京A23456', '蓝色', 'LSVAM4187C2014002', '中型货车', 1, '东风', '北京物流公司', 'system'),
(3, 1, '京A34567', '黄色', 'LSVAM4187C2014003', '重型货车', 1, '陕汽', '北京物流公司', 'system'),
(4, 2, '京B45678', '黄色', 'LSVAM4187C2014004', '轻型货车', 1, '福田', '北京配送中心', 'system'),
(5, 2, '京B56789', '白色', 'LSVAM4187C2014005', '冷链车',   1, '江铃', '北京配送中心', 'system')
ON CONFLICT (id) DO NOTHING;

INSERT INTO traj.traj_terminal (id, identity_code, tl_model, sim_account, protocol_type, equipment_type, video_channel, status, creator)
VALUES
(1, 'TERM_001', 'GT06N', '13800100001', 'JT808', '4', 4, 1, 'system'),
(2, 'TERM_002', 'GT06N', '13800100002', 'JT808', '4', 4, 1, 'system'),
(3, 'TERM_003', 'GT06N', '13800100003', 'JT808', '4', 4, 1, 'system'),
(4, 'TERM_004', 'GT06N', '13800100004', 'JT808', '2', 0, 1, 'system'),
(5, 'TERM_005', 'GT06N', '13800100005', 'JT808', '2', 0, 1, 'system')
ON CONFLICT (id) DO NOTHING;

INSERT INTO traj.traj_vehicle_terminal (vehicle_id, terminal_id, bind_type, bind_time, status, creator)
VALUES
(1, 1, 1, CURRENT_TIMESTAMP, 1, 'system'),
(2, 2, 1, CURRENT_TIMESTAMP, 1, 'system'),
(3, 3, 1, CURRENT_TIMESTAMP, 1, 'system'),
(4, 4, 1, CURRENT_TIMESTAMP, 1, 'system'),
(5, 5, 1, CURRENT_TIMESTAMP, 1, 'system')
ON CONFLICT DO NOTHING;

INSERT INTO traj.traj_driver (id, driver_name, sex, idcard, contact_phone, license_code, licence_category, status, creator)
VALUES
(1, '张伟', 1, '110101********0001', '138****0001', 'LIC001', 'A2', 1, 'system'),
(2, '李强', 1, '110101********0002', '138****0002', 'LIC002', 'B2', 1, 'system'),
(3, '王磊', 1, '110101********0003', '138****0003', 'LIC003', 'A2', 1, 'system'),
(4, '刘洋', 1, '110101********0004', '138****0004', 'LIC004', 'C1', 1, 'system'),
(5, '陈杰', 1, '110101********0005', '138****0005', 'LIC005', 'B2', 1, 'system')
ON CONFLICT (id) DO NOTHING;

INSERT INTO traj.traj_vehicle_driver (vehicle_id, driver_id, driver_type, bind_time, status, creator)
VALUES
(1, 1, 1, CURRENT_TIMESTAMP, 1, 'system'),
(2, 2, 1, CURRENT_TIMESTAMP, 1, 'system'),
(3, 3, 1, CURRENT_TIMESTAMP, 1, 'system'),
(4, 4, 1, CURRENT_TIMESTAMP, 1, 'system'),
(5, 5, 1, CURRENT_TIMESTAMP, 1, 'system')
ON CONFLICT DO NOTHING;

INSERT INTO traj.base_warn_type (id, pid, name, grade_level)
VALUES
(1, -1, '超速报警', 1),
(2, -1, '疲劳驾驶', 1),
(3, -1, '围栏越界', 2),
(4, -1, '紧急求助', 1),
(5, -1, '设备故障', 3)
ON CONFLICT (id) DO NOTHING;
