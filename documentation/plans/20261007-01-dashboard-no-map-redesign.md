# 方案 20261007-01：监控总览大屏改版（去地图 · 用户视角 · 车队全景）

- 日期：2026-10-07
- 状态：已实施并验证
- 关联文档：[MOD-DBD-005 实时监控与可视化](../modules/MOD-DBD-005-实时监控与可视化.html)（v1.1.0）
- 关联代码：`frontend/src/views/dashboard/Dashboard.vue`、`frontend/src/views/mdm/VehicleList.vue`

## 一、目标与范围

按用户需求重做监控总览大屏（F15，`/dashboard`）：

1. **不加载地图**：移除 Leaflet 瓦片地图、车标差分渲染与 HeatLayer 热力层。
2. **以当前登录用户视角**：顶栏新增视角徽标（用户 · 部门 · 数据范围）；数据本身沿用后端 `DashboardService` 按 `deptScope` 逐块裁剪的事实（null 不限制 / 空集合 fail-closed 全零 / 非空按部门过滤），前端如实渲染，即天然"企业负责人看全部、车队负责人只看本队"。
3. **车辆全景信息**：KPI 指标带（有效车辆/在线/里程/报警/未处置风险/今日风险事件）+ 中央车队全景卡片矩阵 + 区域分布 + 工单处置 + 在线车速分布。
4. **链接钻取**：所有卡片/排行行/区域行带查询条件跳转（/monitor、/playback、/mdm/vehicles?deptId=、/risk?handleStatus=0&cityCode=、/risk/orders?status=、/alarms?plateNo=&时间窗）。

不做的事：不改后端接口、不改路由与菜单、不删除 `HeatLayer.ts` 文件（仅停用引用）。

## 二、现状分析（改版前）

- `Dashboard.vue`（752 行）三栏布局：左列在线率环/里程/工单，中央 Leaflet 地图（车标 + HeatLayer 热力）+ 车队/区域 TOP8 ECharts 柱图，右列报警态势 + AlarmTicker。
- 数据源：`/api/monitor/dashboard/summary`（六块聚合，已按 deptScope 裁剪）、`/api/alarm/stats`、`/api/alarm/latest`、WS（POINTS/RISK/ALARM）。
- "今日风险/未处置风险"计数已有现成接口 `GET /api/monitor/overview`（`MonitorController.overview`），改版前大屏未消费。

## 三、实施方案（已执行）

| # | 改动 | 文件 | 说明 |
|---|------|------|------|
| 1 | 移除地图 | Dashboard.vue | 删除 Leaflet/HeatLayer import、marker 差分渲染、rAF 合帧、fitBounds 等；`HeatLayer.ts` 文件保留不再被引用 |
| 2 | 顶栏视角徽标 | Dashboard.vue | `useAuthStore()` 取 realName/deptName，SUPER_ADMIN 显示"全部数据"，否则"本部门及下级" |
| 3 | KPI 指标带 | Dashboard.vue | 6 卡：有效车辆/在线车辆/今日里程/今日报警/未处置风险（新增消费 `getOverview()`）/今日风险事件 |
| 4 | 车队全景卡片矩阵 | Dashboard.vue | 中央主视觉：渲染 `fleetStats`（每队一卡：在线/总数、在线率条），点击钻 `/mdm/vehicles?deptId=`；空范围显示 fail-closed 空态 |
| 5 | 我的车队在线 TOP5 | Dashboard.vue | HTML 排行条（替代 ECharts 柱图），行级钻取带 deptId；**仅当车队数 ≥2 才渲染**（单车队负责人不显示冗余排行） |
| 6 | 在线车速分布 | Dashboard.vue | WS/降级轮询点位写入 `reactive(Map)`（按 identityCode 合并），computed 聚合 <20/20-40/40-60/60-80/≥80 五档；无后端改动 |
| 7 | 区域分布 TOP8 | Dashboard.vue | HTML 排行条（车辆数条 + 今日风险数），行点击钻 `/risk?handleStatus=0&cityCode=&cityName=` |
| 8 | 保留件 | Dashboard.vue | AlarmTicker、VehicleDetailDrawer、风险飘条、30s 轮询（summary+overview+alarmStats）、全屏/退出、drill() |
| 9 | 存量缺陷修复 | VehicleList.vue | 该文件只 import 了 `useRoute` 却从未调用 `const route = useRoute()`，onMounted 读 `route.query` 抛 `ReferenceError: route is not defined`，导致大屏钻取车辆档案时 deptId 过滤失效——补一行实例化修复 |

## 四、设计要点（已与用户确认的设计稿）

- 布局：顶栏（标题+视角徽标+时钟+WS 状态+全屏/退出）→ 六卡 KPI 带 → 三栏主体（左：在线率环/车队 TOP5/车速分布；中：车队全景矩阵+实时报警；右：报警态势/区域分布/工单处置）→ 风险飘条。
- 角色效果：企业负责人见全部 8 车队；车队负责人只见本队（全景矩阵仅 1 卡、TOP5 自动隐藏、区域只含本队注册地）；空范围用户全零+空态提示。

## 五、影响面

- 前端仅 `Dashboard.vue`（重写，752→879 行）与 `VehicleList.vue`（+1 行）；后端零改动；路由/菜单/权限零改动。
- `summary` 接口的 `riskHeat` 块前端暂不消费，接口保留向后兼容。
- 打包产物：Dashboard chunk 16.76 kB；leaflet chunk 仍随 /monitor 路由懒加载，不影响大屏。

## 六、风险与回滚

- 风险低：单页面改版 + 一行存量缺陷修复；回滚 = `git revert` 单 commit（或还原两文件）。

## 七、验证方式与结论

1. `npm run build` 通过（34.7s）。
2. 浏览器实测（admin 登录，browser a11y 快照 + evaluate）：
   - 顶栏标题/视角徽标（"当前视角：超级管理员 · 全部数据"）/时钟/实时推送/全屏/退出 全部存在；
   - 6 张 KPI 卡数字正常（在线 283/500，"今日报警 0"为已知数据时基滞后墙钟现象，非缺陷）；
   - 车队全景矩阵（上海干线车队等）、实时报警列表、左列三卡、右列三卡全部渲染；
   - `.dash` 根节点内 leaflet 元素计数 = 0（无地图确认）；
   - 钻取：车队卡 → `/mdm/vehicles?deptId=116` 命中"上海干线车队"，无 JS 报错；"退出大屏" → `/monitor` 正常；
   - 控制台无新增严重错误；首轮发现的 `route is not defined` 经修复后未复现。

## 八、文档更新

- MOD-DBD-005 升 v1.1.0（2026-10-07）：§1.1 F15、§1.2 功能表、§2 场景、§3.2/§3.3、§4 截图 4-3、§6 架构节点与组件表、§7.5（riskHeat 前端暂不消费）、§8 数据流程，修订记录与本方案链接。
- FUNC-DBD-001 F15 行措辞同步（去"风险热力"，改"无地图数据驾驶舱"）。
- index.html 门户：MOD-DBD-005 卡片描述/版本、topbar 基准日期同步。
