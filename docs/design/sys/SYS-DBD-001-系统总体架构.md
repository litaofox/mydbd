# SYS-DBD-001 系统总体架构

> 版本：v0.1.0 ｜ 日期：2026-10-02
> 关联文档：MOD-TRAJ-001、DDL-TRAJ-001、API-TRAJ-001/002、UC-TRAJ-001~004、research/03

## 1. 项目定位

mydbd 是专注于**北斗导航数据核心业务**的独立项目，能力范围：

1. 北斗轨迹数据的接入与解析（批量导入 + 实时推送；后续接入 JT/T 808 协议网关）
2. 轨迹地图可视化（Leaflet + 高德瓦片）
3. 历史轨迹路线回放（时间轴 + 速度控制）
4. 实时导航数据监控（在途车辆、最新位置、报警态势）
5. 风险预警与分析：基于 ADAS/DSM 事件与驾驶员监控视频，风险分级、处置与统计

业务蓝图与行业背景见《[03-导航系统数据采集与应用平台设计](../../research/03-导航系统数据采集与应用平台设计.md)》。

## 2. 总体架构

```mermaid
flowchart TB
    subgraph Edge["门户层"]
        NGINX["portal-nginx :8090<br/>静态站 + 路由分发"]
    end

    subgraph FE["展示层 frontend"]
        VUE["Vue3 SPA<br/>实时监控 / 轨迹回放 / 风险预警"]
    end

    subgraph JAVA["业务平台 platform-app (Spring Boot 3)"]
        BOOT["platform-boot<br/>启动 / 登录 / 健康检查"]
        TRAJ["module-trajectory<br/>车辆/轨迹点 查询与回放"]
        MON["module-monitor<br/>实时态势 / 风险事件 / 视频分析结果"]
        COMMON["platform-common<br/>统一响应/JWT/异常/分页"]
    end

    subgraph PY["处理服务 processing-app (FastAPI)"]
        SIM["simulator 模拟器"]
        INGEST["ingest 轨迹接入/CSV导入"]
        ANA["analysis 视频风险分析（占位）"]
    end

    subgraph DB["数据层"]
        PG[("PostgreSQL 16 + PostGIS 3.4<br/>schema: traj / mon")]
    end

    VUE --> NGINX
    NGINX -->|/api/simulator /api/ingest /api/analysis| PY
    NGINX -->|/api/**| JAVA
    TRAJ --> PG
    MON --> PG
    SIM --> PG
    INGEST --> PG
    ANA --> PG
```

## 3. 模块划分（低耦合高内聚）

| 模块 | 类型 | 职责 | 依赖 |
|---|---|---|---|
| platform-common | Java | 统一 Result/分页、错误码、BizException/全局异常、JWT 工具与过滤器、MyBatis-Plus 分页/CORS | 无业务依赖 |
| module-trajectory | Java | 轨迹域：车辆台账、轨迹点查询、最新位置、历史轨迹 | platform-common |
| module-monitor | Java | 监控域：总览指标、风险事件分页与处置、类型分布、终端报警、视频分析任务 | platform-common |
| platform-boot | Java | 启动入口、演示账号登录、健康检查、actuator | 全部业务模块 |
| processing-service | Python | 轨迹模拟器、终端接入（服务令牌）、样例 CSV 导入、视频分析占位引擎 | 仅依赖数据库契约 |
| frontend | Vue3 | 实时监控页、回放页、风险预警页 | 仅依赖 HTTP 接口契约 |

耦合规则：

- Java 业务模块之间 **零 Maven 依赖**；监控域需要轨迹统计时直接读取 `traj` schema（见 WarnInfoMapper 注释），后续演进为内部 API。
- Java 与 Python 之间仅通过 HTTP + 数据库表契约协作，当前骨架不共享代码。
- 前端只认 HTTP 接口；接口走统一前缀 `/api/**` 与 `{code, message, data}` 响应结构。

## 4. 数据模型

| schema | 核心表 |
|---|---|
| traj | traj_gps_point（轨迹点 + PostGIS geometry/GIST 索引）、traj_vehicle、traj_terminal、traj_driver、traj_vehicle_driver、traj_vehicle_terminal、traj_warn_info、traj_gps_photo、base_warn_type |
| mon | risk_event（风险预警事件）、video_analysis（视频分析任务） |

traj 域结构与迁移的 DDL-TRAJ-001 完全一致；mon 域为本项目新增，定义见 `infra/postgres/init/03-mon-tables.sql`。

## 5. 接口总览

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /api/auth/login | 登录（演示账号 admin/admin123） |
| GET | /api/traj/vehicles | 在途车辆（轨迹点去重） |
| GET | /api/traj/vehicles/registered | 车辆台账 |
| GET | /api/traj/latest | 各终端最新轨迹点 |
| GET | /api/traj/track | 历史轨迹查询（identityCode/plateNo + 时间范围） |
| GET | /api/monitor/overview | 监控总览指标 |
| GET | /api/monitor/risks | 风险事件分页 |
| POST | /api/monitor/risks/{id}/handle | 风险事件处置 |
| GET | /api/monitor/risks/type-stats | 事件类型分布 |
| GET | /api/monitor/warnings | 终端报警列表 |
| GET | /api/monitor/video-analyses | 视频分析任务列表 |
| POST | /api/simulator/start · /stop · GET /status | 模拟器控制 |
| POST | /api/ingest/traj | 终端实时推送（X-Service-Token） |
| POST | /api/ingest/load-sample | 样例 CSV/事件导入 |
| POST | /api/analysis/video | 视频分析（X-Service-Token，当前为占位引擎） |

迁移的 API-TRAJ-001/002 描述了同一批轨迹/模拟器接口的详细字段语义。

## 6. 部署

四个容器：`postgres`（postgis/postgis:16-3.4）、`platform-app`、`processing-app`、`portal-nginx`。

```bash
cp .env.example .env
docker compose up -d
# http://localhost:8090  账号 admin / admin123
```

processing-app 只读挂载 `samples/` 到 `/data/samples`；PostgreSQL 初始化脚本首次启动自动执行。

## 7. 迭代路线

| 阶段 | 内容 |
|---|---|
| v0.1（当前） | 模块化骨架；文档迁移；样例导入；地图监控；轨迹回放；风险事件处置与统计；视频分析占位 |
| v0.2 | JT/T 808-2019 终端接入（注册/鉴权/心跳/定位/报警报文解析）；电子围栏；风险规则引擎下推 SQL |
| v0.3 | JT/T 1078 视频通道：实时视频调阅、事件片段上传；接入真实 DSM/ADAS 视觉模型 |
| v1.0 | IAM 权限体系、审计日志、大屏、面向多车队/多租户的运营能力（对齐 research/03 蓝图） |
