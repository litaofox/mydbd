# mydbd — 北斗导航数据业务平台

> 版本：0.1.0
> 定位：专注于北斗导航数据核心业务的独立项目 —— 轨迹接入解析、地图可视化、历史轨迹回放、实时导航监控、基于导航数据与驾驶员监控视频的风险预警分析。

## 功能范围

| # | 能力 | 说明 |
|---|---|---|
| 1 | 北斗轨迹数据接入与解析 | 批量导入（CSV）+ 终端实时推送（HTTP），PostgreSQL/PostGIS 持久化 |
| 2 | 轨迹地图可视化 | Leaflet 地图实时展示车辆位置、速度、报警状态 |
| 3 | 历史轨迹回放 | 按车辆与时间范围查询轨迹，地图上按时间轴回放 |
| 4 | 实时导航数据监控 | 车辆在线情况、最新位置、报警态势监控 |
| 5 | 风险预警与分析 | ADAS/DSM 事件（疲劳、分心、前向碰撞等）风险分级、处置与统计，含驾驶员监控视频分析任务 |

## 工程结构（模块化、低耦合高内聚）

```
mydbd/
├── backend-java/            # Java 平台（Spring Boot 3 + MyBatis-Plus）
│   ├── platform-common/     # 统一响应、JWT、异常、Web/分页配置
│   ├── module-trajectory/   # 轨迹域：车辆/终端/轨迹点 查询与回放
│   ├── module-monitor/      # 监控域：实时态势、风险事件、视频分析结果
│   └── platform-boot/       # 启动入口、登录认证、健康检查
├── processing-service/      # Python 处理服务（FastAPI）
│   └── app/
│       ├── routes/          # simulator 模拟器 / ingest 接入 / analysis 视频分析
│       └── services/
├── frontend/                # Vue3 + TS + Element Plus + Leaflet + ECharts
├── infra/                   # PostGIS 初始化 SQL、nginx、
├── docs/                    # 设计文档（从 mydatama 完整迁移 + 本项目架构文档）
└── samples/                 # 北斗样例数据（GPS、司机事件、行车视频片段）
```

模块间仅通过 HTTP / 数据库契约交互，无共享代码、无中间件依赖。

## 快速启动

```bash
cp .env.example .env          # 按需修改密码与密钥
(cd frontend && npm install && npm run build)   # 产出 frontend/dist 供门户挂载
docker compose up -d          # postgis / platform-app / processing-app / portal-nginx
# 浏览器访问 http://localhost:8090  演示账号 admin / admin123
```

登录后在"实时监控"页点击「加载样例数据」，即可将 `samples/` 中的轨迹导入并在地图展示。

## 文档

- **[documentation/index.html](documentation/index.html) — 项目文档中心（正式文档库 v1.0.0，2026-10-05 起生效）**：总体设计、架构、数据库、部署运维、18 个功能模块产品与技术文档、接口手册与参考资料，支持搜索与跨文档跳转。
- docs/ — 历史文档目录，自 v1.0.0 起作为归档快照保留，不再更新；内容与代码冲突时以 documentation/ 为准。

## 版本控制

语义化版本（SemVer）；当前 **v0.1.0**（骨架基线）。变更记录见 [CHANGELOG.md](CHANGELOG.md)。
