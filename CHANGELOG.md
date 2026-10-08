# Changelog

本项目遵循语义化版本（SemVer）。

## v0.1.0 — 2026-10-02

首个基线版本：

- 建立模块化工程骨架：Java 平台（common / trajectory / monitor / boot）、Python 处理服务、Vue3 前端、PostGIS 基础设施
- 从 mydatama 完整迁移北斗业务文档：MOD-TRAJ-001、DDL-TRAJ-001、API-TRAJ-001/002、UC-TRAJ-001~004 及《导航系统数据采集与应用平台设计》
- 迁移北斗样例数据（GPS CSV、司机事件、行车视频片段、车队信息表）
- 能力占位：轨迹接入（CSV 导入 / 实时推送）、地图监控、轨迹回放、风险事件管理、视频分析任务

## v0.4.0 — 2026-10-08

实时监控与历史回放体验增强：

- 实时导航监控页（Monitor.vue）打开且实时定位数据到达后，自动勾选全部"行驶中"状态车辆（调用 `pickStat('drive')`）并切换底部"行驶中"标签页，仅执行一次
- 历史轨迹播放页（Playback.vue）底部新增 200px 高结果面板，含"轨迹/事件/停车"三个标签页，支持滚动加载（每页 50 条）、行点击地图定位、播放当前行高亮，"匹配位置"勾选后懒解析逆地理地址
- 后端新增 3 个分页查询接口：
  - `GET /api/traj/track/page` — 轨迹点分页查询，含当前绑定司机姓名（LATERAL 关联）
  - `GET /api/traj/events/page` — 事件分页查询（来源 `mon.risk_event`，跨 schema 只读）
  - `GET /api/traj/stops/page` — 停车段分页查询（连续零速点聚合，间隔>5 分钟切分，时长≥3 分钟计入）
  - 新建实体：`TrackPointRow`、`EventRow`、`StopSeg`；新建 Mapper：`TrackQueryMapper`；`TrajectoryService` 新增 `pageTrack`/`pageEvents`/`pageStops` 方法
- 前端 API（`frontend/src/api/traj.ts`）新增 `getTrackPage`/`getEventsPage`/`getStopsPage` 及对应类型
- 地图车辆图标放大一倍：`iconSize` 28→56、`iconAnchor` 14→28、SVG `size` 16→32（Monitor.vue 与 Playback.vue 同步调整）
