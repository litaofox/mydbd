# MOD-RISK-001 风控规则引擎（CEP）模块技术设计

- 功能编号：F18（风险预警闭环域）
- 配套 PRD：[PRD-RISK-001 风控规则引擎产品设计](../../html/PRD-RISK-001-风控规则引擎产品设计.html)
- 版本：v1.0（2026-10-02）
- 关联模块：processing-service（引擎宿主）、module-monitor（事件消费）、module-iam（权限）、module-audit（审计）

---

## 1. 目标与范围

平台侧 CEP（Complex Event Processing）规则引擎：对**实时北斗定位点流**与**终端 DSM/ADAS 预警信号**按可配置规则做实时判定，产出分级 `mon.risk_event`。

第一波规则类型：

| 类型 | rule_type | 触发数据 | 判定 |
|---|---|---|---|
| 超速 | SPEED | 北斗点 speed | speed ≥ speedKmh |
| 疲劳驾驶 | FATIGUE | 北斗点时间窗回溯 | 连续行驶会话时长 ≥ continuousMin |
| 围栏进出 | （围栏实体，非 rule 行） | 北斗点 + PostGIS | inside 状态翻转 + trigger_dir |
| 信号分级 | SIGNAL | /api/analysis/video 信号 | eventCode 匹配启用规则，按规则定级 |
| 组合事件 | COMBO | 已产事件 + 当前北斗点 | 窗口内有疲劳事件且当前超速 |

明确不做：通知动作（F19）、工单（F20）、路网限速、按企业差异化规则、九轴急加减速（F22）、真实 JT/T 808 网关。

---

## 2. 总体架构

### 2.1 引擎为什么放在 processing-service（Python）

GPS 点的唯一实时写入口是 processing-service（`POST /api/ingest/traj` 与模拟器进程内直写），引擎必须在"点写入后、同一热路径上"完成判定；若把引擎放 Java，Python 每条批次都要同步回调 Java，引入跨服务 HTTP 耦合与故障扩散（引擎挂了轨迹入库也被拖死）。

因此采用**"配置在 Java、执行在 Python、数据库为契约"**：

```
安全管理员 ──HTTP──> platform-app(Java, module-risk)
                        │ 写 mon.risk_rule / mon.risk_geo_fence
                        ▼
                  PostgreSQL（契约）
                        ▲ 规则/围栏 TTL 30s 缓存读取
processing-app(Python) ─┤
  ├─ POST /api/ingest/traj ──> insert_gps_points ──> cep_engine.evaluate_points()
  ├─ simulator._tick ────────> insert_gps_points ──> cep_engine.evaluate_points()
  └─ POST /api/analysis/video ────────────────────> cep_engine.evaluate_signal()
                        │ 命中 + 冷却去重
                        ▼
              mon.risk_event（扩展 rule_id/fence_id/title）
                        ▲
前端风险页 ──> module-monitor（只读 + 处置，既有）
```

- 引擎对轨迹主链路**只旁路、不阻断**：调用点全部 try/except，异常 `print` 日志后吞掉，不影响 insert 返回。
- load-sample（历史 CSV 导入）**不挂引擎**，行为不变。

### 2.2 进程与事务边界

- 点入库（db.py 各自短事务）与事件入库（insert_risk_events 独立短事务）分离：先提交点，再评估；评估期间的查询能读到已提交的点。
- 围栏状态表 upsert 与事件插入放在同一事务（每车一个连接），避免"状态翻了但事件丢了/重复产生"。
- Python 侧不加消息队列/Redis：单进程、批规模小（模拟器 5 点/2 秒；终端批量 ≤5000 点但按车聚合后 SQL 次数为 O(车数 × 规则类数)）。

---

## 3. 数据库设计（07-risk-tables.sql）

新文件 `infra/postgres/init/07-risk-tables.sql`（init 目录仅首库初始化执行；本期环境按 F33/F34 既有方式 docker cp 手工执行）。

### 3.1 规则表 mon.risk_rule

```sql
CREATE TABLE IF NOT EXISTS mon.risk_rule (
    id           bigserial    PRIMARY KEY,
    rule_code    varchar(40)  NOT NULL,              -- SPEED_GENERAL / DSM_FATIGUE / COMBO_FATIGUE_SPEED ...
    rule_name    varchar(80)  NOT NULL,              -- 事件名称快照来源
    rule_type    varchar(16)  NOT NULL,              -- SPEED / FATIGUE / SIGNAL / COMBO
    event_code   varchar(40)  NOT NULL,              -- 产出事件码：SIGNAL 类等于信号码；围栏固定 GEO_ENTER/GEO_EXIT
    risk_level   smallint     NOT NULL DEFAULT 2,    -- 1低 2中 3高
    params       jsonb        NOT NULL DEFAULT '{}'::jsonb,
    cooldown_sec integer      NOT NULL DEFAULT 300,  -- 同车同规则去重窗口
    status       smallint     NOT NULL DEFAULT 1,    -- 1启用 0停用
    built_in     smallint     NOT NULL DEFAULT 0,
    valid_mark   smallint     NOT NULL DEFAULT 1,
    remark       varchar(255),
    creator      varchar(40),
    create_date  timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      varchar(40),
    update_date  timestamp
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_risk_rule_code
    ON mon.risk_rule (rule_code) WHERE valid_mark = 1;
CREATE INDEX IF NOT EXISTS idx_risk_rule_type ON mon.risk_rule (rule_type) WHERE valid_mark = 1;
```

params 按类型：

| rule_type | params |
|---|---|
| SPEED | `{"speedKmh": 100}` |
| FATIGUE | `{"continuousMin": 240, "gapMin": 10}` |
| SIGNAL | `{}`（阈值即信号本身，event_code=信号码） |
| COMBO | `{"windowMin": 30, "speedKmh": 100}`（窗口内有 FATIGUE 事件且当前速度≥speedKmh） |

### 3.2 电子围栏 mon.risk_geo_fence

```sql
CREATE TABLE IF NOT EXISTS mon.risk_geo_fence (
    id           bigserial      PRIMARY KEY,
    fence_name   varchar(80)    NOT NULL,
    fence_type   varchar(10)    NOT NULL,           -- CIRCLE / POLYGON
    center_lng   numeric(10,6),
    center_lat   numeric(10,6),
    radius_m     integer,                            -- 圆形半径（米）
    polygon_geom geometry(Polygon, 4326),            -- 多边形
    trigger_dir  smallint       NOT NULL DEFAULT 2, -- 1进入 2离开 3进出
    risk_level   smallint       NOT NULL DEFAULT 2,
    cooldown_sec integer        NOT NULL DEFAULT 600,
    status       smallint       NOT NULL DEFAULT 1,
    valid_mark   smallint       NOT NULL DEFAULT 1,
    remark       varchar(255),
    creator      varchar(40),
    create_date  timestamp      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      varchar(40),
    update_date  timestamp
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_risk_fence_name
    ON mon.risk_geo_fence (fence_name) WHERE valid_mark = 1;
CREATE INDEX IF NOT EXISTS idx_risk_fence_geom
    ON mon.risk_geo_fence USING gist (polygon_geom);
```

约束（service 层保证）：CIRCLE 必须有 center+radius（50~100000 米）；POLYGON 必须有 ≥4 个点（首尾闭合，前端回传时保证闭合）。

### 3.3 围栏车辆状态 mon.risk_fence_state

```sql
CREATE TABLE IF NOT EXISTS mon.risk_fence_state (
    identity_code   varchar(100) NOT NULL,
    fence_id        bigint       NOT NULL,
    inside          smallint     NOT NULL DEFAULT 0, -- 最近一个点是否在围栏内
    last_point_time timestamp,
    last_enter_time timestamp,                     -- 最近一次 ENTER 事件时间（冷却判定）
    last_exit_time  timestamp,                     -- 最近一次 EXIT 事件时间
    update_date     timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (identity_code, fence_id)
);
```

无历史点回填：首次见到（state 无行）只 upsert 当前 inside，**不产生事件**。

### 3.4 风险事件表扩展

```sql
ALTER TABLE mon.risk_event ADD COLUMN IF NOT EXISTS rule_id  bigint;
ALTER TABLE mon.risk_event ADD COLUMN IF NOT EXISTS fence_id bigint;
ALTER TABLE mon.risk_event ADD COLUMN IF NOT EXISTS title    varchar(100); -- 中文名快照
CREATE INDEX IF NOT EXISTS idx_mon_risk_rule ON mon.risk_event (rule_id);
```

事件编码：SPEED/FATIGUE/SIGNAL/COMBO 取 rule.event_code；围栏事件固定 `GEO_ENTER` / `GEO_EXIT`，fence_id 区分围栏，title = `进入围栏·{围栏名}` / `离开围栏·{围栏名}`。

### 3.5 种子（同文件）

- 8 条内置规则（PRD §5.5），`built_in=1`，全部启用；`INSERT ... SELECT WHERE NOT EXISTS` 幂等。
- 1 条演示围栏：`演示围栏·国贸（可删）`，CIRCLE 中心 (116.404, 39.912)、半径 3000 米、trigger_dir=3、等级中、冷却 600 秒、启用。
- 序列 setval：
  ```sql
  SELECT setval(pg_get_serial_sequence('mon.risk_rule','id'), (SELECT MAX(id) FROM mon.risk_rule), true);
  ```
- 菜单/权限点（traj.sys_menu，显式 id）：

| id | parent | 名称 | type | perm_code | path | icon | sort |
|---|---|---|---|---|---|---|---|
| 904 | 900 | 风控规则 | 2 | risk:rule:view | /system/rules | SetUp | 94 |
| 905 | 900 | 电子围栏 | 2 | risk:fence:view | /system/fences | MapLocation | 95 |
| 913 | 904 | 风控规则维护 | 3 | risk:rule:edit | — | — | 1 |
| 914 | 905 | 围栏维护 | 3 | risk:fence:edit | — | — | 1 |

  `ON CONFLICT (id) DO NOTHING` + setval；role_menu：role 1 全量（既有 `SELECT 1,id FROM sys_menu` 幂等语句补跑）；role 2 安全管理员补 (2,904)(2,905)(2,913)(2,914)；role 3/4 不授配置菜单。

### 3.6 ID 策略

risk_rule / risk_geo_fence 为**低频配置表，用 bigserial 小号 id**（IdType.AUTO，与 sys_menu/dept 同），前端 id 保持 number；risk_event 仍 bigserial。

---

## 4. CEP 引擎设计（processing-service）

### 4.1 新增/改动文件

| 文件 | 说明 |
|---|---|
| app/services/cep_engine.py | 新增：规则/围栏缓存 + evaluate_points + evaluate_signal |
| app/db.py | 扩展：规则/围栏/状态/事件读写函数；insert_risk_events 增加 rule_id/fence_id/title 列 |
| app/routes/ingest.py | 改动：入库成功后 try/except 调 evaluate_points |
| app/services/simulator.py | 改动：_tick 入库后 try/except 调 evaluate_points |
| app/routes/analysis.py | 改动：analyze 占位识别后，交由 evaluate_signal 按启用规则定级/过滤 |
| app/services/risk_engine.py | 保留：降级为"占位信号识别器"（只产出信号候选，不落库、不定级） |
| app/config.py | 增加 `risk_cache_sec: int = 30`（env RISK_CACHE_SEC） |

### 4.2 规则/围栏缓存

模块级 `_RULES_CACHE = {"ts":0, "rows":[]}`、`_FENCES_CACHE` 同构；每次评估先比对 `time.time()-ts < settings.risk_cache_sec`，过期则一次 SELECT 全量 `status=1 AND valid_mark=1`。无外部依赖、无主动推送；配置改动最坏 30 秒生效。

### 4.3 evaluate_points(rows) 算法

rows 为已插入的同一批点（dict 字段同 insert_gps_points）。

```
rules   = 启用规则；fences = 启用围栏；皆空 → return
1. 按 identity_code 分组，组内按 gps_time 升序；plate_no 取组内非空值
2. 对每车 v：
   ─ SPEED（所有启用的 SPEED 规则）：
       取本批 speed 最大点 pmax；对每条 speedKmh ≤ pmax.speed 的规则生成候选(rule, pmax)
   ─ FATIGUE（每车仅在本批最后点 speed>0 时评估）：
       一条窗口 SQL（见 §4.4）求最近连续行驶会话 [s0,s1]；
       s1 与本批最后点时间差 ≤ gapMin 且 s1-s0 ≥ continuousMin → 候选(rule, 最后点)
   ─ FENCES（见 §4.5）：最后点 vs 全部启用围栏，一条 SQL 取 inside 集
   ─ COMBO 不在本阶段（需依赖已落库的疲劳事件，见 §4.6）
3. 冷却过滤（规则类）：
   一次 SQL 取本车各 rule 最近事件时间：
     SELECT rule_id, MAX(event_time) FROM mon.risk_event
     WHERE plate_no=%s AND rule_id = ANY(%s) GROUP BY rule_id
   now - last < cooldown_sec 的候选剔除
4. insert_risk_events(候选) 提交
5. COMBO 评估（§4.6）→ 追加插入
```

事件字段：event_source 一律 `'北斗'`；confidence=1.00；event_time/经纬度/speed 取代表点；rule_id/title 取规则。

### 4.4 疲劳会话 SQL（每车一条）

```sql
WITH base AS (
    SELECT gps_time,
           LAG(gps_time) OVER (ORDER BY gps_time) AS prev_t
    FROM traj.traj_gps_point
    WHERE identity_code = %s
      AND speed > 0
      AND gps_time BETWEEN %s - ((%s + %s) * interval '1 minute') AND %s
),
grp AS (
    SELECT gps_time,
           COUNT(*) FILTER (
               WHERE prev_t IS NULL OR gps_time - prev_t > %s * interval '1 minute'
           ) OVER (ORDER BY gps_time) AS g
    FROM base
)
SELECT MIN(gps_time) AS s0, MAX(gps_time) AS s1
FROM grp GROUP BY g
ORDER BY s1 DESC
LIMIT 1;
```

参数：identity、batch_last_time、continuousMin、gapMin、batch_last_time、gapMin。连续时长 `s1 - s0 ≥ continuousMin 分钟`，且 `batch_last_time - s1 ≤ gapMin 分钟`（会话仍在持续）才命中。

### 4.5 围栏判定（每车一查询 + 状态 upsert）

inside 批量查询（circle 用 geography 米制，polygon 用 ST_Contains）：

```sql
SELECT f.id, f.trigger_dir, f.cooldown_sec, f.risk_level, f.fence_name,
       CASE WHEN f.fence_type = 'CIRCLE'
            THEN ST_DWithin(
                   ST_SetSRID(ST_MakePoint(%s,%s),4326)::geography,
                   ST_SetSRID(ST_MakePoint(f.center_lng,f.center_lat),4326)::geography,
                   f.radius_m)
            ELSE ST_Contains(f.polygon_geom, ST_SetSRID(ST_MakePoint(%s,%s),4326))
       END AS inside
FROM mon.risk_geo_fence f
WHERE f.status = 1 AND f.valid_mark = 1;
```

状态读取：`SELECT fence_id, inside, last_enter_time, last_exit_time FROM mon.risk_fence_state WHERE identity_code=%s AND fence_id = ANY(%s)`。

逐围栏：

| 旧状态 | 现 inside | 动作 |
|---|---|---|
| 无行 | 0/1 | upsert 初始状态，无事件 |
| 1 | 1 | 仅刷 last_point_time |
| 0 | 0 | 仅刷 last_point_time |
| 0 | 1 | trigger_dir ∈(1,3) 且 now-last_enter_time>cooldown → GEO_ENTER；置 inside=1、last_enter_time |
| 1 | 0 | trigger_dir ∈(2,3) 且 now-last_exit_time>cooldown → GEO_EXIT；置 inside=0、last_exit_time |

状态 upsert + 事件插入在**同一事务**（新增 db 函数 `save_fence_results(identity, point, transitions)`）。

### 4.6 组合事件 COMBO

在规则类候选插入后执行：对本批存在 SPEED 候选（或最后点速度 ≥ combo.params.speedKmh）的车辆：

```sql
SELECT 1 FROM mon.risk_event
WHERE plate_no=%s AND rule_id=%s  -- FATIGUE_DRIVE 规则 id（引擎缓存中按 rule_code 找）
  AND event_time >= %s - (%s * interval '1 minute')
LIMIT 1;
```

同时检查**本批次内存中刚产生的疲劳候选**（覆盖同事务未可见）。命中且 COMBO 规则自身冷却通过 → 产 `COMBO_FATIGUE_SPEED` 高风险事件，独立冷却。

### 4.7 evaluate_signal(channel, event_code, plate_no, identity_code, lng, lat, speed, media_url, confidence)

1. 从启用规则缓存找 `rule_type='SIGNAL' AND event_code=%s`：无 → 返回 None（调用方任务成功、eventCount=0、summary 注明"信号未匹配启用规则，已忽略"）。
2. 冷却：`SELECT MAX(event_time) FROM mon.risk_event WHERE plate_no=%s AND rule_id=%s`，窗口内 → 同样忽略。
3. 插入事件：event_source=channel（DSM/ADAS），event_code=信号码，risk_level/title/rule_id 取规则，confidence 缺省 0.88，media_url 透传。

analysis.py 兼容：请求体仍支持只传 channel（占位识别器确定性产出一个候选码）；新增可传 `eventCode/identityCode/lng/lat/speed/clipUrl(透传 media_url)`。

### 4.8 异常隔离与性能

- ingest.py / simulator.py 调用点：
  ```python
  try:
      cep_engine.evaluate_points(rows)
  except Exception as exc:
      print(f"[cep] evaluate error: {exc}")
  ```
- 每批 SQL 上限粗估：1（缓存/规则缓存命中时 0）+ 车数×(疲劳 1 + 围栏 inside 1 + 围栏 state 1 + 冷却 1) + COMBO 少量。5000 点单车批量 ≈ 4 条 SQL。
- 所有几何计算下推 PostGIS，Python 不做几何运算。

---

## 5. Java 新模块 module-risk（com.mydbd.risk）

### 5.1 模块注册（沿用 F33 三处注册）

1. 父 pom `<modules>` + dependencyManagement 加 module-risk；
2. module-risk/pom 依赖 platform-common（mybatis-plus 由 common 传递）；
3. platform-boot/pom 加依赖；@MapperScan(`com.mydbd.**.mapper`) 已覆盖。

### 5.2 实体

- `RiskRule`（@TableName("mon.risk_rule")，autoResultMap=true）：字段与表一一；`params` 用
  `@TableField(typeHandler = JacksonTypeHandler.class) private Map<String,Object> params;`
  MyBatis-Plus 3.5.7 自带 `com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler`，jsonb 读写为 Map。
- `RiskGeoFence`：标量字段常规映射；`polygon_geom` 标 `@TableField(exist=false)` 不参与 MP SQL；增加瞬态 `private List<double[]> points;`（[lng,lat]…，仅圆形时为空、由 center/radius 表达）。
- 两表 id 均 `@TableId(type = IdType.AUTO)`；creator/create_date/updater/update_date 由既有 MybatisMetaObjectHandler 处理（确认其填充字段名兼容；不兼容则 service 显式 set creator）。

### 5.3 Mapper

- `RiskRuleMapper extends BaseMapper<RiskRule>`（无自定义 SQL）。
- `RiskGeoFenceMapper extends BaseMapper<RiskGeoFence>` + 两个注解方法：
  - `@Select("SELECT id, ST_AsGeoJSON(polygon_geom) AS geojson FROM mon.risk_geo_fence WHERE id=#{id}")`
  - `@Update("UPDATE mon.risk_geo_fence SET polygon_geom=ST_GeomFromGeoJSON(#{geojson},4326) WHERE id=#{id}")`
  列表/详情的 polygon 回显：详情用上述查询 + service 把 GeoJSON coordinates 转 points；列表不返回图形（表格无需）。

### 5.4 Service 与校验

**RiskRuleService**

- `page(q)`：MyBatis-Plus 分页，过滤 ruleCode(like)/ruleName(like)/ruleType(eq)/status(eq)，按 id 排序。
- `create/update`：
  - 通用必填：ruleCode（^[A-Z][A-Z0-9_]{2,39}）、ruleName、ruleType∈{SPEED,FATIGUE,SIGNAL,COMBO}、riskLevel∈{1,2,3}、cooldownSec 0~86400；
  - 按类型校验 params：SPEED 必有 speedKmh(1~220 数值)；FATIGUE 必有 continuousMin(1~1440)、gapMin(1~120)；COMBO 必有 windowMin(1~720)、speedKmh(1~220)；SIGNAL params 忽略；
  - eventCode：SIGNAL 必须 ∈{DSM_FATIGUE,DSM_DISTRACTION,ADAS_FCW,ADAS_LDW}，且一个信号码只能有一条有效规则（重复 → 40901）；其余类型 eventCode 默认取 ruleCode（后端强制，前端不传）；
  - ruleCode 有效行唯一（重复 40901）；
  - built_in=1 更新时 ruleCode/ruleType/eventCode 不可变（即使传了也以后端现值为准）。
- `toggle(id,status)`、`delete(id)`：built_in=1 → BizException(40901,"内置规则不可删除")；删除 = valid_mark=0 逻辑删。
- `all()`：全部有效规则（id/code/name/type/status），供事件页历史筛选下拉。

**RiskGeoFenceService**

- create/update 事务内：MP 存标量 → 用回填 id 调 ST_GeomFromGeoJSON 更新 polygon_geom（POLYGON 时）；GeoJSON 由 service 拼装：
  `{"type":"Polygon","coordinates":[[ [lng,lat], ...闭合 ]]}`。
- 校验：名称有效行唯一（40901）；CIRCLE 中心经纬度合法（73~135，3~54 中国范围粗校）、radius 50~100000；POLYGON points≥4 且首尾一致（不一致后端闭合）；triggerDir∈{1,2,3}。
- delete 逻辑删 valid_mark=0；同时删 `mon.risk_fence_state` 中该围栏状态（再启用时按首次见到处理，PRD §6.2）。
- detail 返回 points（ST_AsGeoJSON 解析）。

**RiskEngineStatsService**

- `GET /api/risk/engine/summary`：
  ```java
  // ruleTotal, ruleEnabled, fenceTotal, fenceEnabled,
  // todayTotal, todayByLevel（List<{level,cnt}>），topRules（List<{ruleId,title,cnt}> 取今日 TOP5）
  ```
  3 条简单聚合 SQL（count mon.risk_rule/fence；mon.risk_event 当日 GROUP BY risk_level / rule_id）。供规则页"在不在工作"的运维感知（前端本期展示在规则列表头部统计条）。

### 5.5 Controller 与权限

`/api/risk/rules`（RiskRuleController）：

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | /api/risk/rules | risk:rule:view | 分页 |
| GET | /api/risk/rules/all | risk:rule:view | 有效规则简表（下拉） |
| GET | /api/risk/rules/{id} | risk:rule:view | 详情 |
| POST | /api/risk/rules | risk:rule:edit | 新建 |
| PUT | /api/risk/rules/{id} | risk:rule:edit | 更新 |
| PUT | /api/risk/rules/{id}/status | risk:rule:edit | 启停 |
| DELETE | /api/risk/rules/{id} | risk:rule:edit | 删除（内置拒绝） |

`/api/risk/fences` 同构（risk:fence:view / risk:fence:edit，含 /all）。
`GET /api/risk/engine/summary`：risk:rule:view。

写接口沿用 `@AuditLog(action=CREATE/UPDATE/DELETE, objectId SpEL)` + AuditFilter 全量记录（F34 已覆盖 POST/PUT/DELETE 与 body 脱敏；本模块 body 无敏感字段）。

### 5.6 module-monitor 改动（最小侵入）

- RiskEvent 实体增加 `Long ruleId; Long fenceId; String title;`（MP select 自动带列）。
- `GET /api/monitor/risks` 查询参数增加 `ruleId`（Long，可选；空则不过滤）。
- 不做事件数据权限裁剪（与 F33 已确认节奏一致，监控域后续波次统一接入）。

---

## 6. 前端设计

### 6.1 路由与菜单

`router/index.ts` MainLayout 子路由新增：

| path | 组件 | meta |
|---|---|---|
| /system/rules | views/system/RiskRuleList.vue | {title:'风控规则', perm:'risk:rule:view'} |
| /system/fences | views/system/GeoFenceList.vue | {title:'电子围栏', perm:'risk:fence:view'} |

菜单由 F33 动态菜单驱动（904/905 种子后自动出现）；MainLayout 图标 map 增加 `SetUp`、`MapLocation`（@element-plus/icons-vue）。

### 6.2 api/risk.ts

```ts
getRiskRules(params) / getAllRiskRules() / getRiskRule(id)
createRiskRule(body) / updateRiskRule(id,body) / setRiskRuleStatus(id,status) / deleteRiskRule(id)
getFences / getAllFences / getFence(id) / createFence / updateFence / setFenceStatus / deleteFence
getEngineSummary()
```

id 类型均 number（小号 bigserial）。

### 6.3 RiskRuleList.vue

- 顶部统计条（engine/summary）：启用规则/总规则、启用围栏/总围栏、今日事件数；
- 筛选：类型、状态；表格列：名称、编码、类型 tag、等级 tag、**参数摘要**（前端按 type+params 拼人话："速度 ≥ 120 km/h · 冷却 180 秒"）、状态 switch（risk:rule:edit）、最近触发（summary 不够则先省略或列表不带）、操作（编辑/删除，内置行无删除）；
- 编辑对话框：类型下拉（自定义规则可选全部；内置行禁用类型/编码）；规则编码（内置禁用）；名称；**动态参数区**（v-if 按 ruleType 渲染 speedKmh / continuousMin+gapMin / SIGNAL 信号码 select / COMBO windowMin+speedKmh）；等级 radio（高/中/低 el-tag 着色）；冷却秒数 InputNumber；备注；
- 表单校验前端做一遍（与后端同口径），错误展示后端 message。

### 6.4 GeoFenceList.vue

- 表格：名称、形状、中心/半径或"多边形 N 点"、触发方向、等级、状态 switch、操作；
- 编辑对话框：左表单（名称、形状 radio、圆形半径 InputNumber、触发方向 select、等级、冷却）、右 Leaflet 地图（高度 360px）：
  - 底图沿用 Monitor.vue 的高德瓦片与坐标系处理（直接复用同样 L.tileLayer URL）；
  - CIRCLE：地图点击→设中心点（放置 L.marker + L.circle 预览，半径输入实时 setRadius）；
  - POLYGON：点击加点（L.polyline 折线预览），双击或"完成绘制"按钮闭合为 L.polygon；"撤销一点""清空重画"按钮；
  - 编辑回显：圆形 marker+circle；多边形 L.polygon(points)；
  - 保存时收集 {centerLng,centerLat,radiusM} 或 {points:number[][]}；未画图形禁止保存。
- 不引入 leaflet-draw 等新依赖（离线环境装包风险），全部基于 Leaflet 核心 click/dblclick 事件，约 120 行内。

### 6.5 RiskEvents.vue 增强

- 表格在"事件码"前增加"事件名称"列（title || eventCode 兜底）；
- 筛选区增加"规则"下拉（getAllRiskRules，含停用项；value=id，label=name），查询带 ruleId；
- 其余不变（处置接口本期保留，F20 再做工单化）。

---

## 7. 端到端时序

**超速（模拟器演示）**：
simulator._tick → insert_gps_points（提交）→ cep_engine.evaluate_points：缓存命中启用规则 → pmax.speed≥100/120 → 冷却查询通过 → insert_risk_event(rule_id,title,...) → 前端风险页刷新可见"严重超速"。

**信号分级**：
终端（演示：视频分析接口）→ analyze() 产出候选信号码 DSM_FATIGUE → evaluate_signal：规则缓存命中启用规则（等级高/冷却 600）→ 落事件；停用后再次调用 → eventCount=0 不落事件。

**配置热更**：
管理员页面停用 SPEED_GENERAL → 30 秒内引擎缓存过期重查 → 之后批次不再评估该规则，无需重启。

---

## 8. 任务分解

| 任务 | 内容 | 产出 |
|---|---|---|
| T1 | 07-risk-tables.sql + 手工执行（表/扩展列/种子规则/演示围栏/菜单/授权/setval） | 库结构 |
| T2 | Python：db.py 扩展 + cep_engine.py + ingest/simulator/analysis 挂载 + config | 实时引擎 |
| T3 | module-risk 后端（注册/实体/mapper/service/controller/VO 校验） | 配置 API |
| T4 | module-monitor RiskEvent 字段 + ruleId 过滤 | 事件联动 |
| T5 | 前端：api/risk.ts、RiskRuleList、GeoFenceList（Leaflet 绘制）、路由/图标、RiskEvents 增强 | 管理页面 |
| T6 | mvn compile + npm build + 重建 processing-app/platform-app | 可运行环境 |
| T7 | 接口自测（§9.1） | 自测记录 |
| T8 | 模拟器驱动端到端 + 浏览器页面验证（§9.2） | E2E 结论 |
| T9 | MOD 实施记录 + 项目记忆更新 | 收尾 |

## 9. 自测计划

### 9.1 接口层（PowerShell + curl.exe，admin/disp01 两身份）

1. 规则 CRUD：建自定义规则（SPEED speedKmh=60）→ 详情 params 正确回显 → 更新阈值 → 停用/启用 → 删除；重复 ruleCode 40901；内置规则 DELETE 40901；非法参数（speedKmh=300、cooldown=-1）40001；SIGNAL 信号码重复 40901。
2. 围栏 CRUD：建圆形围栏 → 详情 center/radius 回显；建多边形 4 点围栏 → SQL 验证 ST_Contains（中点=true、远点=false）；删除围栏后 risk_fence_state 同步清空；重名 40901。
3. 权限：disp01（无 risk:rule:view）GET /api/risk/rules → 40301；admin 正常。
4. engine/summary 数值与库内 count/当日事件一致。
5. 审计：规则/围栏写操作在 sys_audit_log 留痕。

### 9.2 引擎端到端（模拟器 + 接口轮询）

1. 临时 SPEED 规则阈值 60 启用 → POST /api/simulator/start → 60 秒内 GET /api/monitor/risks?ruleId=X 出现事件：title/rule_id/speed/位置正确、来源"北斗"、confidence=1.00；同车同规则在冷却内不重复（计数受限）；停用规则后 35 秒内无新增；删除临时规则，历史事件仍在。
2. 临时 FATIGUE 规则 continuousMin=1、gapMin=2 启用 → 模拟器持续 ~70 秒 → 出现疲劳事件；恢复/删除临时规则。
3. 演示围栏：模拟器车辆游走半径 2~9km 必然越界 → 出现 GEO_EXIT/GEO_ENTER 事件（title 含围栏名、fence_id 正确）；state 表 inside 翻转；冷却期不重复；首点初始化无事件（用新 identity_code 造点验证一次）。
4. 信号：X-Service-Token 调 /api/analysis/video {channel:DSM} → 规则启用时落 DSM_FATIGUE 等级=配置值、带 confidence；停用该规则后再调 → eventCount=0 且 risk_event 无新增；带自定义 eventCode=ADAS_LDW 同理。
5. 组合：临时疲劳规则 + 把 COMBO 参数 speedKmh 临时调到 60 → 疲劳事件窗口内出现 COMBO_FATIGUE_SPEED 事件；恢复 COMBO 参数。
6. 主链路回归：引擎验证全程 /api/traj/latest 正常、监控页点位移正常。
7. 浏览器：admin 见两个新菜单与页面全流程（建规则/地图画围栏）；安全管理员可见可编辑；disp01 菜单不可见、URL 直访被守卫弹回；风险页名称列与规则筛选生效。

## 10. 风险与注意事项

1. **python 时间基准**：模拟器点 gps_time 取数据库写入时刻（`datetime.now()`），冷却比较用 PG `CURRENT_TIMESTAMP`/event_time 同源，避免容器时钟差（两容器同机，可忽略）。
2. **jsonb 读写**：psycopg2 对 jsonb 自动反序列化为 dict；params 缺失键用 .get 默认值，引擎对脏配置零信任（类型转换包 try，单条规则配置错误只跳过该规则）。
3. **ingest 批量 5000 点**：疲劳 SQL 时间窗上限 250 分钟 × 2 秒 ≈ 7500 行扫描（带 identity+gps_time 索引），单车批量可接受；多车大批量终端推送场景若成瓶颈，后续把评估移出请求线程（后台 worker），本期不做。
4. **MP JacksonTypeHandler**：必须 `@TableName(autoResultMap = true)`，否则查询时 typeHandler 不生效（params 读出为字符串）。
5. **logic delete 与唯一索引**：rule_code/fence_name 用 partial unique index（valid_mark=1），删除后允许同名重建。
6. **risk_event 加列为幂等 DDL**：`ADD COLUMN IF NOT EXISTS`，重复执行安全。

---

## 11. 实施记录（2026-10-02 落地回填）

### 11.1 交付清单

| 层 | 实际产出 |
|---|---|
| DDL | infra/postgres/init/07-risk-tables.sql（mon.risk_rule / mon.risk_geo_fence / mon.risk_fence_state，risk_event 加 rule_id/fence_id/title，8 内置规则 + 国贸演示围栏 + 菜单 904/905/913/914 + SAFE_ADMIN 授权） |
| Python | processing-service：cep_engine.py（规则/围栏/信号/组合评估 + 30 秒 TTL 缓存）、db.py（冷却/会话/状态机 SQL）、risk_engine.py 改纯占位识别器；ingest、simulator、analysis 三条入口均旁路挂载，全程 try/except 不阻断主链路 |
| Java | module-risk（RiskRule/RiskGeoFence 实体、mapper、service、controller，权限 risk:rule:* / risk:fence:*，@AuditLog module=RISK）；module-monitor RiskEvent 加字段 + /api/monitor/risks 支持 ruleId |
| 前端 | api/risk.ts、RiskRuleList.vue（统计条+动态参数表单）、GeoFenceList.vue（Leaflet 高德瓦片，圆形拾取/多边形采点）、RiskEvents.vue（事件名称列+命中规则筛选）、路由 /system/rules、/system/fences |

### 11.2 实施中发现并修复的缺陷

1. **psycopg2 时间参数被推断为 text（2 处，阻断围栏/疲劳评估）**
   - `db.py latest_driving_session`：`%s - (… * interval '1 minute')` 报 `invalid input syntax for type interval`；改为 `%s::timestamp - …`。
   - `db.py apply_fence_transitions` 的 upsert VALUES：last_point_time 与 CASE 分支时间参数均为 text，报 `column "last_enter_time" is of type timestamp without time zone but expression is of type text`；全部加 `::timestamp`。
   - 经验：psycopg2 传 Python 字符串时间进含算术/CASE 的 SQL 时，PG 不会自动按目标列类型推断，必须显式转型。
2. **多边形围栏闭合补点顺序错误**：原实现先 closedRing 补尾点再校验，导致前端传入 2 个点时绕过"至少 3 顶点"校验（补点后凑成 3 点）。修复为先校验原始点数 ≥3，再补闭合尾点；validateShape 再查闭合环 ≥4。
3. **nginx upstream 缓存旧容器 IP（运维侧，非代码缺陷）**：重建 platform-app 后容器 IP 变化，portal-nginx 启动时解析的 upstream IP 失效，经 :8090 调 /api 全部 502（直连 :8080 正常）。重启 portal-nginx 恢复。后续若频繁重建后端，需同步重启 nginx 或在 compose 内用依赖重启策略。

### 11.3 自测结论（§9.1 / §9.2 全部通过）

**接口层（§9.1）**：规则 CRUD/params 回显/40901/40001 全口径、围栏圆形+多边形（ST_Contains 中点 true/远点 false/ST_IsValid true）、删除级联清 state、dispatcher 40301、engine/summary 与库一致、RISK 模块审计留痕（traj.sys_audit_log，含 result_code）——全部符合预期。

**引擎端到端（§9.2）**：

1. SPEED：临时规则（阈值 60、冷却 60s）下模拟器 100 秒稳定出事件，title/rule_id/event_source=北斗/confidence=1.00/位置速度均正确，同车冷却窗口内不重复；停用并等待缓存过期（35s > TTL 30s）后再跑 45 秒，rule_id=9 新增 **0** 条，其余启用规则仍正常出事件；规则软删后历史事件仍可查询（97 条历史保留验证）。
2. FATIGUE：continuousMin=1/gapMin=2 临时参数下稳定出疲劳事件，冷却去重正确；验证后内置规则 3 已恢复 240/10/1800。
3. COMBO：窗口内疲劳+超速（临时 speedKmh=60）正确产生组合事件；验证后规则 8 已恢复 30/100/1800。
4. 围栏状态机（受控点序列，车辆 FENCE_E2E，4 点）：围栏外首点仅初始化无事件 → 中心点产生 GEO_ENTER（title「进入围栏·演示围栏·国贸（可删）」、fence_id=1、confidence=1.00）→ 离开产生 GEO_EXIT → 冷却窗口内再进入被抑制（无事件）但 state.inside 正确翻转为 1，last_enter_time 不被覆盖。
5. SIGNAL：/api/analysis/video 显式 eventCode：DSM_FATIGUE → eventCount=1、等级 3、confidence=0.91、media_url 落库、event_source=DSM；同车立即重放 → eventCount=0（600s 冷却）；ADAS_LDW → 等级 2、confidence=0.82；停用规则 4 并过缓存 TTL 后 → eventCount=0 且无新增事件，随后已重新启用。
6. 主链路回归：/api/traj/latest 返回 105 车、/api/monitor/overview 正常；模拟器已停止。
7. 浏览器（http://localhost:8090）：admin 菜单出现风控规则/电子围栏；规则页统计条（8/8、围栏 1/1、今日事件分级）与新增动态表单（SPEED 参数/等级/冷却）、删除确认文案全流程通过；围栏页圆形表单录入后 Leaflet 实时画出圆心+圆覆盖物并成功入库（id=6，CIRCLE 116.30,39.99 r=800，验证后已 UI 删除），多边形抽屉含开始绘制/撤销/清空与顶点计数（多边形 ST_Contains 几何正确性已在 §9.1 API 层验证）；风险事件页事件名称列（title+eventCode 副标题）与"命中规则"下拉（8 规则）生效；无权限用户 perm01（DISPATCHER 角色）登录后两个菜单均不可见，直访 /system/rules 与 /system/fences 均被路由守卫弹回 /monitor。角色权限表确认 SAFE_ADMIN（role_id=2）持有 904/905/913/914（view+edit）。

### 11.4 测试后数据状态

- 临时规则 TST_SPEED_60（id=9）、TST_FAT_1（id=10）、TST_UI_01（id=11）均已逻辑删除（valid_mark=0）；
- 内置规则 3/8 参数与冷却已恢复设计值；规则 4 保持启用；
- 本轮测试事件 188 条、视频分析任务 4 条、FENCE_E2E 的 4 个轨迹点与围栏状态已清理；库内恢复为 8 内置规则 + 1 演示围栏 + 基线 6 条当日冒烟事件（历史 10000 条未动）；
- 临时权限验证账号 perm01 已逻辑删除。
