-- 01 扩展与 schema
CREATE EXTENSION IF NOT EXISTS postgis;

CREATE SCHEMA IF NOT EXISTS traj;
CREATE SCHEMA IF NOT EXISTS mon;

COMMENT ON SCHEMA traj IS '北斗轨迹数据域：车辆/终端/驾驶员/轨迹点/报警/抓拍';
COMMENT ON SCHEMA mon  IS '监控分析域：风险预警事件、驾驶员视频分析任务';
