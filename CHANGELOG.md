# Changelog

本项目遵循语义化版本（SemVer）。

## v0.1.0 — 2026-10-02

首个基线版本：

- 建立模块化工程骨架：Java 平台（common / trajectory / monitor / boot）、Python 处理服务、Vue3 前端、PostGIS 基础设施
- 从 mydatama 完整迁移北斗业务文档：MOD-TRAJ-001、DDL-TRAJ-001、API-TRAJ-001/002、UC-TRAJ-001~004 及《导航系统数据采集与应用平台设计》
- 迁移北斗样例数据（GPS CSV、司机事件、行车视频片段、车队信息表）
- 能力占位：轨迹接入（CSV 导入 / 实时推送）、地图监控、轨迹回放、风险事件管理、视频分析任务
