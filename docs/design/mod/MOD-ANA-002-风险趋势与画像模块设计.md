# MOD-ANA-002 风险趋势与画像（F23）模块设计

- 功能编号：F23（风险预警闭环域，第一波，待建设）
- 功能定义（FUNC-DBD-001 §95）：按车/司机/企业/路段的风险趋势分析与黑点路段热力图
- 资源依据：**RES-DBD-001 第一波并行资源分配表（唯一仲裁依据）**
- 版本：v1.1　日期：2026-10-03（v1.1 追加 §11 实施记录）
- 关联：F18 事件语义（MOD-RISK-001）、F20/F21 工单与干预（MOD-RISK-002/003）、F22 评分契约（本表 §五，MOD-ANA-001 并行产出、不等待）

---

## 1. 概述与范围

### 1.1 目标

在**新建 module-analysis 的 `com.mydbd.analysis.profile` 子包**中，提供只读分析能力，前端落独立页面 `views/analysis/RiskProfile.vue`（菜单 302，`analysis:profile:view`，path `/analysis/profiles`，挂 300"分析决策"目录下）：

1. **趋势分析**：按日/周/月的风险事件量折线（总量 + 分等级 + 分 event_code Top5），支持车/司机/企业/全网四个维度切换；
2. **对象画像**：单车/单司机画像卡与车队（企业）汇总卡——评分曲线（读 F22 `mon.driver_score`）、事件构成、干预与闭环率、工单 Top；
3. **黑点路段**：本期无路网匹配，用"事件位置网格聚类"近似路段，PostGIS `ST_SnapToGrid` 聚合 Top N 黑点，地图标记 + 列表联动；
4. **对比排行**：司机/车辆周期内风险 Top N 榜（等级加权事件数 + 低分评分）。

### 1.2 明确不做（本期边界）

| 不做 | 原因 / 后续 |
|---|---|
| 真实路段匹配（事件吸附到路网 link） | 依赖 F10 路网数据（第二波）；本期黑点为**网格近似口径**，F10 后升级（见 §3.4） |
| 风险预测模型（未来趋势外推） | 无训练数据与模型基座，属第三波智能域 |
| 连续热力图渲染 | 属 F15 大屏职责（实时事件连续热力）；F23 黑点是**历史聚合列表 + 地图标记**，两者不共用组件（见 §7.4） |
| 回写 `mon.driver_score` 或任何 F22/risk 表 | 分配表 §五硬约束：F23 对 risk_event / work_order / intervention / driver_score / gps **一律只读** |
| 导出报表 / PDF | 后续波次 |
| 新建字典 | 复用 `risk_level`、`score_level`、`risk_event_code`（09 脚本已有） |

### 1.3 与 F22 的并行与契约边界

- 同模块不同包：F22 落 `com.mydbd.analysis.score`，F23 落 `com.mydbd.analysis.profile`；module-analysis 骨架（父 pom modules + dependencyManagement、platform-boot 依赖三处注册）**由先实施者创建**，后实施者只补自己的包——两文档描述同一注册动作，实施时以仓库现状为准、幂等处理。
- 只依赖分配表 §五已锁定的 `mon.driver_score` 列契约（score_date/driver_id/identity_code/plate_no/dept_id/score/level/features/sample_points/event_count/create_date，uk(score_date,driver_id)），**不等 F22 文档与实施**。
- 降级设计：若 F22 晚于 F23 上线，`driver_score` 表可能不存在。画像接口对评分曲线子查询单独捕获 `undefined_table`（PSQLException）→ 该子块返回 `scoreReady:false`，其余子块（事件/工单/干预）正常；前端评分曲线区显示"依赖未就绪（F22 评分模型）"占位。趋势/黑点/排行不依赖评分表（排行中评分列同样可缺省）。
- 前端共用文件 `api/analysis.ts`：F23 只**追加** `getProfile*` 前缀函数（§6.1），与 F22 的 `getScore*` 互不撞名；`RiskProfile.vue` 为 F23 独立文件。

---

## 2. 信息架构与视图设计

### 2.1 页面布局（定稿：单页四 Tab）

`/analysis/profiles` 页面顶部为全局时间范围条（快捷：近7日/近30日/近90日/自定义，dayjs 处理，跨度 >366 天前端拦截 + 后端 46101 兜底），下方 `el-tabs` 四个视图：

```
┌────────────────────────────────────────────────────────┐
│ 时间范围: [近30日 ▾] [2026-09-04 ~ 2026-10-03]  [刷新]  │
├────────────────────────────────────────────────────────┤
│ Tab1 趋势分析 | Tab2 对象画像 | Tab3 黑点路段 | Tab4 对比排行 │
└────────────────────────────────────────────────────────┘
```

- **Tab1 趋势分析**：维度单选（全网/企业/司机/车辆）+ 对象选择器（企业下拉复用 `/api/mdm/depts/tree` 数据、司机/车辆用可搜索 el-select，走既有 mdm 分页接口）+ 粒度单选（日/周/月）。主图为 ECharts 折线：总量线 + 高/中/低三条分级线（虚线）；副图为 Top5 event_code 堆叠柱（同坐标系第二 grid 或切换展示）。空桶补零（后端做，见 §3.1.4）。
- **Tab2 对象画像**：类型（车辆/司机/企业）+ 对象选择。四分区卡片：
  1. 概览头卡：对象名 + 周期事件总数/高危数/最新评分与等级；
  2. 评分曲线（driver 直接读其 driver_id；vehicle 读当前绑定司机；dept 显示车队均值线）——`scoreReady:false` 时占位；
  3. 事件构成：ECharts 雷达（按 event_code 六轴归一）+ 堆叠占比条；
  4. 干预与闭环：漏斗四联卡（事件→工单→干预→闭环，口径同 F21 funnel 但限定对象）+ 闭环率；工单 Top5 简表（order_no/事件名/等级/状态/时限超时标记）。
- **Tab3 黑点路段**：网格半径单选（100/200/500 米，默认 200）+ Top N（默认 20，≤50）。左侧黑点列表（排名、中心坐标、事件数、等级加权分、涉及车辆数、代表车牌、最高频事件码），右侧 Leaflet 地图 `circleMarker`（半径/颜色按加权分分档：红≥均值×1.5、橙、黄）；点击列表项 → 地图 flyTo + 高亮，点击 marker → 列表滚动定位。页脚固定口径说明文案："网格聚类近似，非真实路段；F10 路网匹配上线后升级"。
- **Tab4 对比排行**：维度（司机/车辆）+ Top N（默认 10，≤50）。el-table：排名、对象、周期事件数、高危数、等级加权分（排序键）、最新评分与等级（F22 未就绪时该两列显示"—"）。点击行跳转 Tab2 对应画像（对象类型联动）。

### 2.2 后端包结构（module-analysis / com.mydbd.analysis.profile）

```
com.mydbd.analysis.profile
 ├─ controller/ProfileController.java        // /api/analysis/profile/**
 ├─ service/ProfileTrendService.java         // 趋势 + 排行
 ├─ service/ProfileCardService.java          // 对象画像（含评分降级）
 ├─ service/ProfileHotspotService.java       // 黑点网格聚合
 ├─ mapper/ProfileEventMapper.java           // risk_event 聚合 SQL（@Select 注解）
 ├─ mapper/ProfileScoreMapper.java           // 只读 mon.driver_score
 ├─ mapper/ProfileOrderMapper.java           // 只读 work_order/intervention
 └─ vo/  TrendVO / ObjectCardVO / HotspotVO / RankingVO / BucketPoint / SeriesVO ...
```

模块间零 Maven 依赖约定不变：跨域数据一律同库跨 schema SQL（`mon.*` / `traj.*`），不 import monitor/risk/mdm 模块类。

---

## 3. 数据口径与聚合 SQL 设计

### 3.0 通用口径

- **时间基准**：一律 `COALESCE(event_time, create_date)`（risk_event.event_time 可空，与 08 脚本补单口径一致）；区间 `[start, end)` 左闭右开。
- **有效行**：risk_event 无 valid_mark，全量计入；工单统计带 `valid_mark = 1`。
- **对象归属**：
  - 车辆：`plate_no = :plateNo`（画像主键用 plate_no；identity_code 作辅助显示）；
  - 司机：risk_event 无 driver_id，经 `traj_vehicle_driver`（status=1 且 valid_mark=1 的**当前绑定**）取该司机名下车辆，再按 plate_no 归集。**近似口径**：车辆换司机后历史事件跟随当前司机，页面标注；
  - 企业：`traj_vehicle.dept_id = :deptId`（精确匹配本级，车辆档案口径），经 plate_no 关联事件。
- **等级加权分**：`SUM(risk_level)`（低1/中2/高3），用于黑点与排行排序。
- **数据范围**：受限用户（`UserContext.get().deptScope()` 非 null）所有查询追加 `AND EXISTS (SELECT 1 FROM traj.traj_vehicle v WHERE v.plate_no = e.plate_no AND v.valid_mark = 1 AND v.dept_id = ANY(:scope))`；空集合直接返回空结果。无法归属车辆的事件（如测试 identity）对受限用户不可见——与 IAM 车辆档案口径一致。

### 3.1 趋势分析 SQL

**3.1.1 分桶 + 分等级（一条）**：

```sql
SELECT date_trunc(:gran, COALESCE(e.event_time, e.create_date)) AS bucket,
       count(*)                                    AS total,
       count(*) FILTER (WHERE e.risk_level = 3)    AS high_cnt,
       count(*) FILTER (WHERE e.risk_level = 2)    AS mid_cnt,
       count(*) FILTER (WHERE e.risk_level = 1)    AS low_cnt
FROM mon.risk_event e
WHERE COALESCE(e.event_time, e.create_date) >= :start
  AND COALESCE(e.event_time, e.create_date) <  :end
  AND (:dimAll OR e.plate_no IN (:plateNos))      -- dim=global 时无此条件
GROUP BY 1 ORDER BY 1;
```

**3.1.2 Top5 event_code（先选码）**：

```sql
SELECT e.event_code
FROM mon.risk_event e
WHERE ... 同范围/对象条件 ... AND e.event_code IS NOT NULL
GROUP BY e.event_code ORDER BY count(*) DESC LIMIT 5;
```

**3.1.3 Top5 分桶序列（再取数）**：

```sql
SELECT date_trunc(:gran, COALESCE(e.event_time, e.create_date)) AS bucket,
       e.event_code, count(*) AS cnt
FROM mon.risk_event e
WHERE ... AND e.event_code = ANY(:topCodes)
GROUP BY 1, 2 ORDER BY 1;
```

**3.1.4 空桶补零**：Java service 按 granularity 生成完整桶序列（start→end），SQL 结果按桶对齐、缺桶补 0，避免前端折线跳变。`granularity` 白名单映射：`day→'day' / week→'week' / month→'month'`（date_trunc 的 unit 用 `<choose>` 字面量拼接，**不做参数化**——date_trunc 首参为 text 虽可参数化，但白名单常量拼接可保执行计划稳定，杜绝注入）。

> **避坑（项目经验，F20）**：PostgreSQL `FILTER (WHERE ...)` 必须**紧跟聚合函数**——`count(*) FILTER (...)` 合法；`EXTRACT(EPOCH FROM AVG(x)) FILTER (...)` 报 syntax error，正确是 `EXTRACT(EPOCH FROM AVG(x) FILTER (WHERE ...))`。本模块所有条件计数一律 `count(*) FILTER (...)` / `SUM(...) FILTER (...)` 写法。

### 3.2 对象画像 SQL（限定对象条件同 §3.0）

- **事件构成**：`GROUP BY event_code`，取 count 与分等级 count（雷达轴 = Top6 事件码，归一化由前端做）。
- **漏斗（对象版，参考 F21 funnel 写法，四条 count）**：

```sql
-- ① 事件数：risk_event 范围计数
-- ② 工单数：
SELECT count(*) FROM mon.risk_work_order w
WHERE w.valid_mark = 1 AND w.plate_no IN (:plateNos)
  AND COALESCE(w.event_time, w.create_date) >= :start AND COALESCE(w.event_time, w.create_date) < :end;
-- ③ 有干预工单数（distinct order_id 口径同 F21）：
SELECT count(DISTINCT i.order_id)
FROM mon.risk_intervention i
JOIN mon.risk_work_order w ON w.id = i.order_id AND w.valid_mark = 1
WHERE i.create_date >= :start AND i.create_date < :end
  AND w.plate_no IN (:plateNos);
-- ④ 闭环工单数：status='CLOSED' 且 close_time 在范围内
```

  `interventionRate = ③/②`、`closeRate = ④/②`（②=0 时返回 null，前端显示"—"），保留 1 位小数（与 F21 实现口径一致）。
- **工单 Top5**：`ORDER BY (risk_level DESC, overdue DESC, event_time DESC) LIMIT 5`。
- **评分曲线**：`SELECT score_date, score, level FROM mon.driver_score WHERE driver_id = ANY(:driverIds) AND score_date BETWEEN :start::date AND :end::date ORDER BY score_date`；dept 类型按 `dept_id = :deptId` 聚合 `AVG(score) GROUP BY score_date`。整段独立 try-catch（表不存在 → scoreReady=false）。

### 3.3 排行 SQL

```sql
SELECT e.plate_no,
       max(e.identity_code)                       AS identity_code,
       count(*)                                   AS event_cnt,
       count(*) FILTER (WHERE e.risk_level = 3)   AS high_cnt,
       SUM(COALESCE(e.risk_level, 2))             AS weighted_score
FROM mon.risk_event e
WHERE COALESCE(e.event_time, e.create_date) >= :start
  AND COALESCE(e.event_time, e.create_date) <  :end
  AND e.plate_no IS NOT NULL
  [数据范围 EXISTS]
GROUP BY e.plate_no
ORDER BY weighted_score DESC, event_cnt DESC
LIMIT :limit;
```

dim=driver 时先按 §3.0 建立 plate→driver 映射（traj_vehicle_driver 当前绑定），Java 内按 driver 合并再取 Top N；未绑定司机的车辆不进司机榜。评分列在 Java 侧按对象批量补查 driver_score 最新日 score/level（左连接语义，缺则 null）。

### 3.4 黑点路段 SQL（网格聚类，PostGIS）

risk_event 无 geometry 列（仅 lng/lat numeric），现算点几何；约 1 万行全表聚合无性能压力，无需空间索引（traj.traj_gps_point 的 location GIST 索引本模块不用）。**网格口径**：投 3857 平面后按 `radius_m` 米吸附成格，质心回 4326 输出：

```sql
WITH pts AS (
    SELECT ST_SetSRID(ST_MakePoint(e.lng, e.lat), 4326) AS g4326,
           ST_SnapToGrid(ST_Transform(ST_SetSRID(ST_MakePoint(e.lng, e.lat), 4326), 3857), :radiusM) AS cell,
           COALESCE(e.risk_level, 2) AS lvl, e.plate_no, e.event_code
    FROM mon.risk_event e
    WHERE e.lng IS NOT NULL AND e.lat IS NOT NULL
      AND COALESCE(e.event_time, e.create_date) >= :start
      AND COALESCE(e.event_time, e.create_date) <  :end
      [数据范围 EXISTS]
),
agg AS (
    SELECT cell,
           count(*) AS event_cnt,
           SUM(lvl) AS weighted_score,
           count(DISTINCT plate_no) AS plate_cnt,
           (array_agg(plate_no ORDER BY lvl DESC))[1]  AS top_plate,
           (array_agg(event_code ORDER BY lvl DESC))[1] AS top_code
    FROM pts GROUP BY cell
    ORDER BY weighted_score DESC, event_cnt DESC
    LIMIT :limit
)
SELECT round(ST_X(ST_Transform(ST_Centroid(cell), 4326))::numeric, 6) AS lng,
       round(ST_Y(ST_Transform(ST_Centroid(cell), 4326))::numeric, 6) AS lat,
       event_cnt, weighted_score, plate_cnt, top_plate, top_code
FROM agg ORDER BY weighted_score DESC;
```

- `radiusM` 白名单 {100,200,500}（数值校验后拼接/参数均可，参数化 `::float8` 传入即可，ST_SnapToGrid 支持）。
- **近似口径声明（写入接口注释与页面文案）**：3857 投影在中纬度高估地面格宽约 1/cos(lat)（39°N≈1.29 倍），网格为规则方格而非真实路段；F10 路网匹配上线后，本接口升级为按 link 聚合，接口响应结构不变（lng/lat 换为 link 几何）。
- 网格跨日期无时间维（纯周期聚合）；分等级明细本期不进黑点（列表已含加权分）。

---

## 4. 接口设计（/api/analysis/profile/**，全部 GET、perm `analysis:profile:view`）

统一响应：HTTP 200 + `{code:0, message:'ok', data:...}`；业务错误 code 非 0。

### 4.0 参数校验与错误码（复用 46xxx，46101 起）

| 校验 | 规则 | 错误 |
|---|---|---|
| start/end | 必填，ISO `yyyy-MM-dd'T'HH:mm:ss`（沿用 F21 funnel 的 `@DateTimeFormat(ISO.DATE_TIME)`）；start<end；跨度 ≤366 天 | 格式错 40001；跨度>366 天或 start≥end → **46101 时间范围非法** |
| dim | trend/ranking：`global\|dept\|driver\|vehicle`（trend 默认 global；ranking 仅 driver\|vehicle） | 非法值 **46102 维度或粒度参数非法** |
| granularity | `day\|week\|month`（默认 day） | 46102 |
| id | dim≠global 时必填；对象解析不到（车辆/司机/企业不存在或越权） | 40401 资源不存在 |
| radius_m | 100/200/500 之一，默认 200 | 46102 |
| limit | 1~50，默认 20（ranking 默认 10） | 40001 |

`BizException(46101, "时间范围非法：跨度不得超过366天")` 用 `new BizException(int, String)` 构造（platform-common 已支持）；ErrorCode 枚举不新增（46xxx 段为模块自定义码）。

### 4.1 GET /trend

`?dim=vehicle&id=京A12345&start=2026-09-01T00:00:00&end=2026-10-01T00:00:00&granularity=day`
（dim=global 时省略 id；dim=dept/driver 时 id 为 deptId/driverId）

```json
{ "code": 0, "data": {
  "dim": "vehicle", "id": "京A12345", "granularity": "day",
  "buckets": ["2026-09-01", "2026-09-02", "..."],
  "total":   [12, 9, "..."],
  "byLevel": { "high": [2,1,"..."], "mid": [6,5,"..."], "low": [4,3,"..."] },
  "topCodes": [
    { "code": "SPEED_GENERAL", "name": "一般超速", "series": [5,3,"..."] },
    { "code": "DSM_FATIGUE",   "name": "终端信号·疲劳", "series": [3,2,"..."] }
  ] } }
```

`name` 取 `risk_event.title` 快照众数或字典 `risk_event_code` 兜底（Java 侧字典缓存补全，事件码→中文名）。

### 4.2 GET /object-card

`?type=driver&id=1&start=...&end=...`（type ∈ vehicle|driver|dept）

```json
{ "code": 0, "data": {
  "type": "driver", "id": 1, "name": "张伟", "sub": "京A12345",
  "eventTotal": 45, "highCnt": 8,
  "scoreReady": true,
  "scoreCurve": { "dates": ["09-01","09-02"], "scores": [86.5, 82.0], "levels": ["B","C"] },
  "latestScore": { "score": 82.0, "level": "C" },
  "composition": [ { "code": "SPEED_SEVERE", "name": "严重超速", "cnt": 12, "high": 12, "mid": 0, "low": 0 } ],
  "funnel": { "eventTotal": 45, "orderTotal": 44, "interventionTotal": 20, "closedTotal": 38,
              "interventionRate": 45.5, "closeRate": 86.4 },
  "topOrders": [ { "orderNo": "WO20260928000123", "title": "严重超速", "riskLevel": 3,
                  "status": "CLOSED", "overdue": 0, "eventTime": "2026-09-28 10:12:00" } ] } }
```

F22 未就绪时：`scoreReady:false`，scoreCurve/latestScore 为 null，其余字段照常。

### 4.3 GET /hotspots

`?start=...&end=...&radius_m=200&limit=20`

```json
{ "code": 0, "data": {
  "radiusM": 200, "approx": true,
  "note": "网格聚类近似口径，非真实路段；F10 路网匹配后升级",
  "items": [ { "rank": 1, "lng": 116.404210, "lat": 39.912050,
               "eventCnt": 31, "weightedScore": 78, "plateCnt": 6,
               "topPlate": "京A12345", "topCode": "SPEED_GENERAL" } ] } }
```

### 4.4 GET /ranking

`?dim=driver&start=...&end=...&limit=10`

```json
{ "code": 0, "data": { "dim": "driver", "items": [
  { "rank": 1, "id": 3, "name": "王磊", "sub": "京A34567",
    "eventCnt": 22, "highCnt": 6, "weightedScore": 51,
    "score": 61.0, "level": "D" } ] } }
```

`score/level` 为周期末最新 driver_score 日值，F22 未就绪时 null。

---

## 5. 权限与数据范围

- 功能权限：Controller 类级 `@RequiresPerm("analysis:profile:view")`（platform-common PermAspect 机制）；超管恒放行。无按钮级权限点（全只读）。
- 角色授权（分配表 §3.3）：role1 admin、role2 SAFE_ADMIN 授予菜单 300/301/302；role3/role4 不可见（前端动态菜单不显示 + 路由守卫 `meta.perm` 弹回 + 后端 40301 三层一致）。
- 数据范围：沿用 IAM `UserContext.deptScope()` 口径（MOD-IAM-001 §数据权限：null=全部、空集=不可见、非空=IN 过滤），在 profile 各查询统一注入 §3.0 的 EXISTS 片段；dim=dept 且 deptId ∉ scope 时返回 40401（不泄露存在性）。
- 审计：全 GET 只读，不新增 @AuditLog（AuditFilter 已覆盖访问日志）。

---

## 6. 前端设计

### 6.1 api/analysis.ts（与 F22 共用文件，**只追加**，函数 `getProfile*` 前缀防撞名）

```ts
// ===== F23 风险趋势与画像（本文件追加区）=====
export interface ProfileTrend { dim: string; granularity: string; buckets: string[];
  total: number[]; byLevel: { high: number[]; mid: number[]; low: number[] };
  topCodes: { code: string; name: string; series: number[] }[] }
export interface ProfileObjectCard { /* 与 §4.2 data 对应，含 scoreReady: boolean */ }
export interface ProfileHotspots { radiusM: number; approx: boolean; note: string;
  items: { rank: number; lng: number; lat: number; eventCnt: number; weightedScore: number;
           plateCnt: number; topPlate: string; topCode: string }[] }
export interface ProfileRanking { dim: string; items: { rank: number; id: number; name: string;
  sub: string; eventCnt: number; highCnt: number; weightedScore: number;
  score: number | null; level: string | null }[] }

export function getProfileTrend(params: { dim: string; id?: string; start: string; end: string; granularity: string }): Promise<ProfileTrend>
export function getProfileObjectCard(params: { type: string; id: string; start: string; end: string }): Promise<ProfileObjectCard>
export function getProfileHotspots(params: { start: string; end: string; radius_m?: number; limit?: number }): Promise<ProfileHotspots>
export function getProfileRanking(params: { dim: string; start: string; end: string; limit?: number }): Promise<ProfileRanking>
```

### 6.2 RiskProfile.vue（F23 独立文件，views/analysis/）

- 结构：全局时间条 + el-tabs 四 Tab（§2.1）；Tab 懒加载（首次激活才请求），时间/筛选变化刷新当前 Tab。
- **图表选型（定稿）**：package.json 已有 `echarts ^5.5.0` 且 RiskEvents.vue 在用 → **直接用 echarts，不新增依赖**。折线/堆叠柱/雷达/曲线均 ECharts 原生；按需引入 `echarts/core` + 所用组件注册（与 RiskEvents 的全量 import 并存不冲突，新文件用按需引入减小本页面 chunk）。
- **地图**：Leaflet（已有依赖），底图与坐标处理同 GeoFenceList.vue/Monitor.vue 的高德瓦片 URL；黑点用 `L.circleMarker`（radius=6+加权分归一×10，颜色三档），**不引入 leaflet.heat 等任何新包**。
- 联动：列表行 hover→对应 marker 放大；click→`map.flyTo([lat,lng], 14)`；反向点击 marker 高亮列表行。
- 降级：`scoreReady=false` 时评分卡片区 el-empty + 文案"依赖未就绪：驾驶行为评分（F22）尚未上线"；ranking 评分列"—"。
- 权限：路由 meta `perm:'analysis:profile:view'`；页面内无编辑操作，不需要 v-perm 按钮控制（整页由菜单/路由守卫控制）。
- 字典：等级/事件码中文名复用 `useDict`（risk_level、risk_event_code、score_level）。

### 6.3 路由追加（router/index.ts，只追加一条）

```ts
{ path: 'analysis/profiles', name: 'analysis-profiles',
  component: () => import('@/views/analysis/RiskProfile.vue'),
  meta: { title: '风险趋势与画像', perm: 'analysis:profile:view' } }
```

MainLayout 图标 map 补 300 目录所用图标（与 F22 同一目录，先实施者已加则跳过）。

---

## 7. SQL 脚本与边界

### 7.1 15-risk-profile.sql（infra/postgres/init，幂等）

内容仅三块：

1. **菜单 302**：`INSERT INTO traj.sys_menu (id,parent_id,menu_name,menu_type,perm_code,path,icon,sort_no,visible,status) VALUES (302,300,'风险趋势与画像',2,'analysis:profile:view','/analysis/profiles','TrendCharts',32,1,1) ON CONFLICT (id) DO NOTHING;` + sys_menu 序列 setval（同 07/08 写法）。
2. **授权**：`INSERT INTO traj.sys_role_menu (role_id,menu_id) VALUES (1,302),(2,302) ON CONFLICT DO NOTHING;`（300 目录与 301 由 14-driving-score.sql 负责，勿重复插入目录行）。
3. **索引补齐**（IF NOT EXISTS，幂等）：

```sql
CREATE INDEX IF NOT EXISTS idx_mon_risk_identity_time
    ON mon.risk_event (identity_code, event_time DESC);
CREATE INDEX IF NOT EXISTS idx_mon_risk_plate_time
    ON mon.risk_event (plate_no, event_time DESC);
```

（`event_time` 单列索引 03 脚本已有 idx_mon_risk_time；本期**不建物化视图**——1 万行直查聚合 <50ms，MV 刷新调度属过度设计，量级增长到 50 万+再评估进本脚本追加。）

### 7.2 与 F15 大屏边界

F15 风险热力 = 当日实时事件连续热力（leaflet.heat 类渲染、随推送刷新）；F23 黑点 = 历史周期网格聚合列表 + 静态标记。**不共用组件**：F23 地图逻辑内聚在 RiskProfile.vue，F15 自建；若后续要抽公共底图封装，须先改分配表再动。

---

## 8. 性能与容量

| 项 | 评估 |
|---|---|
| risk_event 现量 ~1 万行 | 趋势/黑点/排行全表聚合 <50ms，直接查即可，无缓存 |
| 索引 | event_time（已有）、identity_code+event_time、plate_no+event_time（进 15 脚本）；对象画像按 plate 过滤走 plate_time 索引 |
| 时间跨度上限 | 366 天（46101），防误传十年区间 |
| 黑点聚合 | ST_Transform/ST_SnapToGrid 逐行现算，1 万行 CPU 开销可忽略；radius 白名单防全表异常格 |
| 排行/画像多子查询 | 单请求 ≤6 条 SQL，串行执行；无 N+1（对象→车牌集一次取全） |
| 未来量级 | 事件日增万级后再考虑：日聚合 MV / 分区表，本期只留接口结构兼容 |

---

## 9. 实施任务拆解

| 任务 | 内容 | 产出 |
|---|---|---|
| T1 | 15-risk-profile.sql（菜单 302/授权/两索引）+ 手工执行（同 F33/F34 docker cp 方式） | 库侧就绪 |
| T2 | module-analysis profile 包：确认/创建模块骨架三处注册（与 F22 幂等互斥）→ entity 免建（全 @Select 注解 Mapper）→ 4 组聚合 SQL Mapper → 3 Service（含 46101/46102 校验、空桶补零、评分降级 try-catch、deptScope 注入）→ ProfileController 4 GET 接口 | 后端 API |
| T3 | 前端：api/analysis.ts 追加 getProfile* → RiskProfile.vue 四 Tab（ECharts 按需引入 + Leaflet 黑点联动）→ 路由追加 → MainLayout 图标确认 | 页面 |
| T4 | 自测（§10）：接口 curl + 浏览器双身份 + 全量编译构建（mvn install+package、npm build，按项目既有部署流程） | 验证结论 |

依赖顺序：T1 → T2 → T3 → T4；与 F22 仅共享模块骨架与 api/analysis.ts 追加区，按前缀约定并行无冲突。

## 10. 验收清单

1. 菜单：admin/SAFE_ADMIN 见"分析决策→风险趋势与画像"，页面四 Tab 完整渲染；DISPATCHER/FLEET_CAPTAIN 菜单不可见、直访 `/analysis/profiles` 被守卫弹回、直调接口 40301。
2. 趋势：dim=global 与库内 `GROUP BY date_trunc` 手工 SQL 数字一致；日/周/月切换桶数正确、空桶补零连续；Top5 事件码与 count 排序一致；dim=vehicle 指定车牌仅含该车事件。
3. 画像：driver 类型评分曲线来自 driver_score（F22 未就绪场景：临时改表名模拟缺失 → scoreReady:false、占位显示、其余区块正常）；漏斗四数与 F21 全局 funnel 口径一致（对象子集验证）；工单 Top5 可点开对应工单页（跳转 /risk/orders 带 query 即可，不要求本期联动）。
4. 黑点：radius 200 默认出 Top20；列表与地图标记双向联动；加权分=SUM(risk_level) 抽一格手工 SQL 核对；lng/lat 为 null 的事件不计入且页面总数对得上。
5. 排行：Top10 按 weightedScore 降序；司机榜未绑定司机的车辆不出现；点击行切到画像 Tab 并带入对象。
6. 校验：跨度 367 天 → 46101；granularity=hour → 46102；不存在 id → 40401；limit=999 → 40001。
7. 数据范围：受限 deptScope 用户（如车队长角色临时授 302 验证）仅见本企业车辆事件，hotspots/trend/ranking 同步收敛。
8. 回归：F22 的 /analysis/scores 页面与 api/analysis.ts 既有函数不受追加影响；共用 driver_score 只读、无任何 UPDATE 语句。

---

## 11. 实施记录（v1.1，2026-10-03）

### 11.1 交付物

- **T1**：`infra/postgres/init/15-risk-profile.sql` 已入库（菜单 302 + role 1/2 授权 + idx_mon_risk_identity_time / idx_mon_risk_plate_time）。
- **T2**：`module-analysis` 新增 `com.mydbd.analysis.profile` 包共 22 文件：
  - `constant/ProfileConstants`（46101/46102、366 天上限、维度/粒度/类型/radius 白名单、EVENT_NAME_FALLBACK 21 码兜底表、HOTSPOT_NOTE）；
  - `mapper/`：ProfileEventMapper（trendBuckets/topCodes/codeBuckets/composition/eventTotal/rankingPlates/hotspots）、ProfileScoreMapper（只读 4 查询）、ProfileOrderMapper（orderTop/topOrders）、ProfileMasterMapper（对象归属与名称 8 查询）；
  - `service/`：ProfileSupport（scopeOrNull + resolvePlates + eventName 兜底链）、ProfileTrendService（趋势/排行，空桶补零、week 桶键=周一）、ProfileCardService（画像，评分块独立 try-catch 降级）、ProfileHotspotService；
  - `vo/` 4 个 + `dto/` 9 个行对象；`controller/ProfileController`（4 GET，类级 @RequiresPerm("analysis:profile:view")，parseRange/checkLimit 校验）。
- **T3**：前端 `api/analysis.ts` 追加 getProfile* 四函数；`views/analysis/RiskProfile.vue`（四 Tab：趋势双图/画像卡+漏斗+雷达+评分曲线/黑点表图联动/排行行跳画像）；`router/index.ts` 追加 analysis/profiles；nginx `portal.conf` 追加 `/api/analysis/profile/` → platform-app（更长前缀优先于 Python 的 /api/analysis/）。
- **T4**：`mvn -o clean install -DskipTests` 全量构建 → docker cp `/app/platform-app.jar` → 重启 platform-app + portal-nginx；`npm run build` 产物经 bind mount 即时生效。

### 11.2 验收结果（§10 逐条）

1. 菜单/路由：admin 浏览器 E2E 四 Tab 渲染正常、console 无 error；dispatcher 浏览器实测"分析决策"分组不可见、直访 /analysis/profiles 被路由守卫弹回；无权限用户直调接口 40301（capt01 收回授权场景实测）。✅
2. 趋势：dim=global 2026-09-01~10-03 total=10015 / high=3342 与手工 SQL 一致；month 2 桶、week 5 桶（键=周一日期）、空桶补零；Top5 码排序正确；dim=vehicle 京A12345=1 条。✅
3. 画像：driver id=1 张伟（事件 1、scoreReady=true、曲线 10-02=82.0/B、漏斗 e1-o0-i0-c0、率 null）；dept id=1 北京物流公司 3 辆车 eventTotal=4；RENAME driver_score 模拟缺失 → scoreReady=false、ranking score=null、其余区块正常，已恢复表。✅
4. 黑点：r200 Top20；Top1 (116.845666,40.124151) cnt=4/ws=8/plates=2/津A00011 与手工 SQL 逐字段一致；r100/r500 各 5 条；null 坐标不计入。✅
5. 排行：driver 榜 5 人（陈杰 ws=18 居首带 90.0/A）；前端行点击切画像 Tab。✅
6. 校验：46101×2（跨度 367 天 / start≥end）、46102×4（gran=hour、dim=road、type 非法、radius=300、rank dim=global）、40401×2（不存在 id / 缺 id 解析失败）、40001×2（limit=999 / 日期格式错）。✅
7. deptScope：临时启用 capt01（dept2 车队长，授 300/302）→ trend=10、hotspots=7、ranking 仅京B 两车+刘洋/陈杰；越权 dept1、京A12345 → 40401；收回授权 → 40301；测试数据已还原。✅
8. 回归：/analysis/scores 页面正常；profile 包对 driver_score 等全只读。✅

### 11.3 设计偏差与修正

- **traj_vehicle 车牌列是 `vehicle_no` 而非 `plate_no`**（§3.0 deptScope 片段按 plate_no 写会 500：`column v.plate_no does not exist`）。已修正为 `v.vehicle_no = e.plate_no`。risk_event 侧列名才是 plate_no，两表不同名是本项目历史命名差异。
- **事件名兜底表扩至 21 码**：库内 event_code 实际含 V_LDW/V_HMW/V_FCW/V_PCW（厂商 ADAS 码）、national 数字码 1~7（JT/T 808 报警类型）、GEO_ENTER/EXIT、BEIDOU_SPEED，而 risk_event_code 字典仅 6 项且 title 全空——按"字典 > 兜底表 > 原码"三级兜底，前端 CODE_NAMES 同步。
- **ECharts 按需引入**（echarts/core + Line/Bar/Radar + CanvasRenderer），避免整包进 RiskProfile chunk；页面未用 dayjs（项目未安装），时间格式化用原生 Date 本地时区实现。
- 其余实现与 §3~§6 设计一致，无接口契约变更。
