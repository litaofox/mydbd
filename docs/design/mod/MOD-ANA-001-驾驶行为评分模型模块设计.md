# MOD-ANA-001 驾驶行为评分模型（F22）模块设计

> 功能清单：F22（docs/FUNC-DBD-001 §D 风险预警闭环域，第一波，待建设）——基于急加减速、超速、疲劳、分心等特征输出 0~100 安全分与风险等级（UBI 基础）。
> 资源依据：RES-DBD-001 §二/§三/§五（module-analysis 新建、菜单 300/301、脚本 14-driving-score.sql、错误码 46001~46099、字典 score_level、`mon.driver_score` 列锁定）。本文档与其冲突处以分配表为准。
> 上游数据：F18 CEP（mon.risk_event）、轨迹（traj.traj_gps_point）、F20 工单闭环结果（mon.risk_work_order.close_result）、MDM 绑定（traj.traj_vehicle_driver 等）。
> 下游：F23 风险趋势与画像（只读本表）。
> 版本 v1.0　2026-10-03

---

## 1. 概述与范围

### 1.1 目标

每日（T+1）按"司机 × 自然日"粒度计算驾驶行为安全评分（0~100）与等级（A~E），落 `mon.driver_score` 日快照，提供分页查询、单人趋势、等级分布汇总与手动重算四类接口。评分是 UBI（基于行为的保险定价）与 F23 画像的基础数据资产。

### 1.2 明确不做

| 不做项 | 说明 |
|---|---|
| 实时评分 | 评分是日批快照，不进 CEP 热路径；当日分数 T+1 01:00 后可见 |
| UBI 保费模型 | 只产分数与等级，不做费率换算、不建保单接口 |
| 周/月聚合与排名 | 归 F23（其按需对本表做二次聚合） |
| 评分权重配置页面 | 扣分表以代码常量固化（`ScoreConstants`），配置化留后续波次 |
| 九轴传感器急加减速 | 现有终端无加速度上报，急加减速一律由 GPS 速度差分派生（F18 已明确九轴归本功能，本期即差分口径） |
| 多司机换班精细归因 | 按"当日生效主班司机"单一归属（见 §3.5） |

### 1.3 模块归属（分配表 §二锁定）

新建 Maven 模块 **module-analysis**（`com.mydbd.analysis`），注册三处：父 pom `<modules>` + `<dependencyManagement>`、platform-boot `<dependencies>`。`@MapperScan("com.mydbd.**.mapper")` 已覆盖，无需改启动类。模块间零 Maven 依赖：读 risk/工单/轨迹数据一律同库跨 schema SQL，不 import 其他模块类。API 前缀 `/api/analysis/score/**`。

---

## 2. 评分模型总览（数据流）

```
                    ┌─────────────────────────────────────────────┐
                    │  module-analysis（Java, platform-app 内）     │
                    │                                             │
 mon.risk_event ◄───┤ ① 事件聚合 SQL（白名单 event_code，           │
 (F18 CEP 产出)     │    剔除工单 FALSE_ALARM）→ 按车×码计数        │
                    │                                             │
 traj.traj_gps_     │ ② 逐车取当日点 → 相邻点速度差分               │
 point ◄────────────┤    → 急加速/急减速事件数 + 样本量/行驶时长     │
 (processing 写入)  │                                             │
                    │ ③ 绑定解析：identity → 车辆 → 当日主班司机     │
 traj.traj_vehicle  │    （traj_vehicle_terminal /                │
 _driver 等 ◄───────┤     traj_vehicle_driver，时间窗口径）         │
                    │                                             │
                    │ ④ 扣分模型：100 − Σ事件扣分 − Σ加减速扣分      │
                    │    → max(0,·) → level 映射                  │
                    │                                             │
                    │ ⑤ upsert mon.driver_score（日快照）          │
                    └─────────────────────────────────────────────┘
                          ▲ 每日 01:00 @Scheduled T+1 全量
                          ▲ POST /recalc 手动重算（异步+互斥锁）
                          ▼
 GET /page /trend /summary ──► ScoreList.vue（前端）；F23 只读本表
```

触发方式：定时任务（每日 01:00 重算前一日）+ 手动重算接口（指定日期区间，可指定司机）。计算引擎选型定稿见 §5.1。

---

## 3. 评分算法（核心）

### 3.1 输入源与事件白名单

评分只消费以下 `mon.risk_event.event_code`（与 07-risk-tables.sql 内置规则种子一致，即 F18 CEP 的 8 条内置规则产出码）：

| event_code | 含义 | risk_level | 计分角色 |
|---|---|---|---|
| SPEED_GENERAL | 一般超速 | 2 中 | 事件扣分 |
| SPEED_SEVERE | 严重超速 | 3 高 | 事件扣分 |
| FATIGUE_DRIVE | 疲劳驾驶（时长回溯） | 3 高 | 事件扣分 |
| DSM_FATIGUE | 终端信号·疲劳 | 3 高 | 事件扣分 |
| DSM_DISTRACTION | 终端信号·分心 | 2 中 | 事件扣分 |
| ADAS_FCW | 前向碰撞风险 | 3 高 | 事件扣分 |
| ADAS_LDW | 车道偏离 | 2 中 | 事件扣分 |
| COMBO_FATIGUE_SPEED | 疲劳叠加超速 | 3 高 | 事件扣分 |

白名单以常量 `ScoreConstants.SCORE_EVENT_CODES` 固化。**不计入**的事件：`GEO_ENTER/GEO_EXIT`（围栏越界非驾驶行为特征）、历史遗留种子码（如 `BEIDOU_SPEED`）及未匹配内置规则的自定义码——后续扩展白名单只改常量。

急加速/急减速不是事件，由 `traj.traj_gps_point` 派生（§3.3）。

### 3.2 FALSE_ALARM 剔除

F20 工单闭环结果为 `FALSE_ALARM`（误报，08-work-order.sql / 09 字典 close_result）的事件**不计扣分**：聚合 SQL 中以 `NOT EXISTS (SELECT 1 FROM mon.risk_work_order w WHERE w.event_id = e.id AND w.close_result = 'FALSE_ALARM')` 剔除。工单与事件 1:1（uk_risk_order_event），未闭环/其他闭环结果（电话提醒、教育等）均正常计分。干预记录（mon.risk_intervention）本期不参与扣分修正。

### 3.3 急加减速派生（GPS 速度差分）

采样口径：生产终端按 JT808 惯例约 **10 秒**上报一点；演示模拟器为 2 秒/点。算法对两种频率统一：

对同一 `identity_code` 当日按 `gps_time` 升序的相邻点对 (p1,p2)：

```
Δt = t2 − t1（秒）；仅 1 ≤ Δt ≤ 30 参与差分（超 30 秒视为停车/离线，段断开）
Δv = (v2 − v1) / 3.6（m/s；speed 列为 integer km/h，NULL 按 0 处理并跳过该对）
a  = Δv / Δt（m/s²）

急加速判定：a ≥ +2.5 且 max(v1, v2) ≥ 10 km/h
急减速判定：a ≤ −2.5 且 max(v1, v2) ≥ 10 km/h
```

- `max(v1,v2) ≥ 10 km/h` 过滤低速 GPS 漂移抖动；10s 采样下 2.5 m/s² 对应单对 Δv ≥ 25 km/h，属真实激烈操作量级。
- **连续段合并**：相邻差分点方向相同且均超阈值视为**一次**事件（状态机：越阈值进入、回落即结束），避免持续加速按点重复计数。
- 实现：Mapper 逐车返回当日 `(gps_time, speed)` 有序列表（单车日点量级 ≤ 数千），Java 内存差分与合并；同时统计 `sample_points`（当日总点数）与 `driving_minutes`（相邻两点 speed 均 > 0 且 Δt ≤ 30s 的 Δt 累加，分钟）。

### 3.4 扣分模型（定稿）

基础分 100，按当日事件扣分，**当日扣分封底 0**（即分数不低于 0）：

```
score = max(0, 100 − Σ事件扣分 − Σ急加减速扣分)     // 保留 1 位小数（HALF_UP）
```

| 特征 | 计数口径 | 单次扣分 | 单日扣分上限 |
|---|---|---|---|
| 高风险事件（risk_level=3：SPEED_SEVERE / FATIGUE_DRIVE / DSM_FATIGUE / ADAS_FCW / COMBO_FATIGUE_SPEED） | 每事件 1 次 | **−8** | 不设 |
| 中风险事件（risk_level=2：SPEED_GENERAL / DSM_DISTRACTION / ADAS_LDW） | 每事件 1 次 | **−4** | 不设 |
| 低风险事件（risk_level=1，当前白名单暂无） | 每事件 1 次 | −2 | 不设 |
| 急加速 HARD_ACCEL | §3.3 合并后事件数 | **−0.5** | −5（即 10 次封顶） |
| 急减速 HARD_BRAKE | §3.3 合并后事件数 | **−0.5** | −5（即 10 次封顶） |

等级映射（字典 `score_level`，同口径写入 `level` 列）：

| level | 区间 | 含义 |
|---|---|---|
| A | score ≥ 90 | 优秀 |
| B | 75 ≤ score < 90 | 良好 |
| C | 60 ≤ score < 75 | 一般 |
| D | 40 ≤ score < 60 | 较差 |
| E | score < 40 | 危险 |

`event_count` = 当日计入的白名单事件总次数（不含急加减速，不含 FALSE_ALARM 剔除量）。

### 3.5 司机归属与样本门槛

**归属链**（当日生效绑定，跨 schema 一条 SQL）：

```sql
SELECT vt.identity_code, v.vehicle_no AS plate_no, v.dept_id, vd.driver_id
FROM traj.traj_vehicle_terminal vt
JOIN traj.traj_vehicle v          ON v.id = vt.vehicle_id AND v.valid_mark = 1
JOIN traj.traj_vehicle_driver vd  ON vd.vehicle_id = v.id AND vd.driver_type = 1 AND vd.valid_mark = 1
WHERE vt.valid_mark = 1
  AND (vt.bind_time   IS NULL OR vt.bind_time   < :dayEnd)
  AND (vt.unbind_time IS NULL OR vt.unbind_time >= :dayStart)
  AND (vd.bind_time   IS NULL OR vd.bind_time   < :dayEnd)
  AND (vd.unbind_time IS NULL OR vd.unbind_time >= :dayStart)
```

- 时间窗口径（`bind_time < 日终 且 unbind_time ≥ 日初`）而非 `status=1`，保证**重算历史日期**用的是当时生效的绑定；当前数据由 04 脚本互斥部分唯一索引保证同一车同时仅一名有效主班司机。若极端情况同一 identity 命中多行（历史换班跨日），取 `bind_time` 最大的一行并在日志告警。
- **无绑定主班司机的车辆当日不评分**（事件与点仍保留，仅不产快照）；`traj_driver` 中司机行不存在或 `valid_mark=0` 的同样跳过。

**样本门槛**（防夜间少量点抖动与低数据量误评）：当日 `sample_points ≥ 200` **且** `driving_minutes ≥ 30` 才产出评分；不满足则不写入，且若重算导致原已有行（数据被清理后重算），`DELETE` 该司机该日行，保证快照与算法一致。200 点 @10s ≈ 33 分钟连续上报，与 30 分钟行驶时长门槛自洽。

### 3.6 features jsonb 结构

全量输出 10 个特征键（含 0 值，便于 F23 直接聚合与前端 top3 扣分项计算）：

```json
{
  "SPEED_GENERAL": {"count": 2, "deduct": 8.0},
  "SPEED_SEVERE": {"count": 1, "deduct": 8.0},
  "FATIGUE_DRIVE": {"count": 0, "deduct": 0},
  "DSM_FATIGUE": {"count": 1, "deduct": 8.0},
  "DSM_DISTRACTION": {"count": 3, "deduct": 12.0},
  "ADAS_FCW": {"count": 0, "deduct": 0},
  "ADAS_LDW": {"count": 2, "deduct": 8.0},
  "COMBO_FATIGUE_SPEED": {"count": 0, "deduct": 0},
  "HARD_ACCEL": {"count": 6, "deduct": 3.0},
  "HARD_BRAKE": {"count": 4, "deduct": 2.0},
  "driving_minutes": 214,
  "total_deduct": 49.0
}
```

`deduct` 为该项已计入的扣分量（急加减速含 5 分封顶后的实扣）。前端"主要扣分项 top3"按 `deduct` 降序取前 3 非零项。

---

## 4. 数据模型（14-driving-score.sql）

### 4.1 mon.driver_score DDL

列定义按分配表 §五锁定（**不改名**），补索引与注释：

```sql
CREATE TABLE IF NOT EXISTS mon.driver_score (
    id            bigserial    PRIMARY KEY,
    score_date    date         NOT NULL,
    driver_id     bigint       NOT NULL,
    identity_code varchar(100),
    plate_no      varchar(50),
    dept_id       bigint,
    score         numeric(5,1) NOT NULL,               -- 0~100，1 位小数
    level         char(1)      NOT NULL,               -- A~E（字典 score_level）
    features      jsonb        NOT NULL DEFAULT '{}'::jsonb,
    sample_points integer,
    event_count   integer,
    create_date   timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_driver_score_date_driver UNIQUE (score_date, driver_id)
);
COMMENT ON TABLE mon.driver_score IS 'F22 驾驶行为日评分快照（重算=upsert，无逻辑删除）';
CREATE INDEX IF NOT EXISTS idx_driver_score_date   ON mon.driver_score (score_date DESC);
CREATE INDEX IF NOT EXISTS idx_driver_score_driver ON mon.driver_score (driver_id, score_date DESC);
CREATE INDEX IF NOT EXISTS idx_driver_score_dept   ON mon.driver_score (dept_id, score_date DESC);
```

**不做 valid_mark 的理由**（项目逻辑删除约定本期豁免）：本表是日粒度派生快照而非主数据——(score_date, driver_id) 唯一约束下"删除"语义即"该日重算后不再满足产出条件"，用 `DELETE` 表达最直接；重算一律 `INSERT ... ON CONFLICT (score_date, driver_id) DO UPDATE SET` 覆盖全部派生列（create_date 保留首写值），upsert 天然幂等，无历史版本诉求，加 valid_mark 反而使唯一约束与查询复杂化。

### 4.2 字典 score_level 种子

```sql
INSERT INTO traj.sys_dict_type (dict_code, dict_name, status, remark)
VALUES ('score_level','驾驶行为评分等级',1,'A≥90 B≥75 C≥60 D≥40 E<40')
ON CONFLICT (dict_code) DO NOTHING;

INSERT INTO traj.sys_dict_item (dict_type_id, item_label, item_value, sort)
SELECT id,'优秀','A',1 FROM traj.sys_dict_type WHERE dict_code='score_level'
UNION ALL SELECT id,'良好','B',2 FROM traj.sys_dict_type WHERE dict_code='score_level'
UNION ALL SELECT id,'一般','C',3 FROM traj.sys_dict_type WHERE dict_code='score_level'
UNION ALL SELECT id,'较差','D',4 FROM traj.sys_dict_type WHERE dict_code='score_level'
UNION ALL SELECT id,'危险','E',5 FROM traj.sys_dict_type WHERE dict_code='score_level'
ON CONFLICT (dict_type_id, item_value) DO NOTHING;
```

### 4.3 菜单与授权种子（分配表 §3.2/§3.3 锁定）

```sql
INSERT INTO traj.sys_menu (id, parent_id, menu_name, menu_type, perm_code, path, icon, sort_no, visible, status) VALUES
  (300, 0,   '分析决策', 1, NULL,                  NULL,               'DataAnalysis', 30, 1, 1),
  (301, 300, '驾驶评分', 2, 'analysis:score:view', '/analysis/scores', NULL,           31, 1, 1)
ON CONFLICT (id) DO NOTHING;
SELECT setval(pg_get_serial_sequence('traj.sys_menu','id'), (SELECT MAX(id) FROM traj.sys_menu), true);

-- role1 全量（沿用既有幂等 SELECT 模式）；role2 SAFE_ADMIN：
INSERT INTO traj.sys_role_menu (role_id, menu_id) VALUES (2,300),(2,301) ON CONFLICT DO NOTHING;
```

role3 DISPATCHER / role4 FLEET_CAPTAIN 不授权（分配表明示 ✗）。执行方式与 F18/F20 相同：init 目录脚本首库自动执行，存量环境 docker cp 手工执行。

---

## 5. 计算任务设计

### 5.1 引擎选型定稿：Java 定时任务（module-analysis 内）

| 方案 | 结论 |
|---|---|
| **Java @Scheduled + MyBatis（定稿）** | 与 F20 RiskOrderScheduler 同栈；输入三张表（risk_event/gps/绑定）全在 PG，批量 SQL + 内存差分即可；复用 MP upsert、事务、日志、权限体系；手动重算与定时共用同一 Service，无跨进程协调问题 |
| Python 批处理（cep_engine 旁路） | 否决：cep_engine 定位是实时热路径旁路，混入 T+1 批处理污染其职责；且手动重算接口在 Java 侧，跨语言触发需引入调度耦合，无收益 |

评分不需要点写入的实时性（T+1 足够），不碰 processing-service。

### 5.2 定时任务

`ScoreScheduler`（@Component，仿 RiskOrderScheduler 写法；**@EnableScheduling 已在 PlatformApplication，不重复加**）：

```java
@Scheduled(cron = "0 0 1 * * ?")   // 每日 01:00
public void dailyT1() {
    // try/catch 包裹，失败仅 log.warn；计算前一日全量
    scoreCalcService.recalc(LocalDate.now().minusDays(1), LocalDate.now().minusDays(1), null);
}
```

单实例约束与 F20 相同（多实例需 ShedLock，本期不做）。

### 5.3 手动重算与并发互斥

- `POST /api/analysis/score/recalc`，body `{"start":"2026-09-01","end":"2026-09-30","driverId":"123"}`（driverId 可选，String 传雪花/bigserial 均可——本表 driver_id 实为 traj_driver 的 bigserial）。
- 校验（不通过 → `BizException(46001, …)`）：start ≤ end；跨度 ≤ 92 天；end ≤ 今天。
- **异步执行**：任务提交单线程 `Executor`（模块内 `ThreadPoolTaskExecutor`，core=1），接口立即返回 `{accepted:true, days:30}`；月级重算逐日逐车 O(千) 点内存计算，预估数十秒~分钟级，同步会超时。
- **并发互斥**：`AtomicBoolean running`（定时与手动共用同一把锁，`compareAndSet` 失败 → `BizException(46002, "评分计算任务进行中，请稍后再试")`）。执行完毕 finally 释放；任务级异常 log.error，不重试（人工再触发即可，upsert 幂等）。
- 权限（分配表 §五注：**不新建按钮权限**）：接口标 `@RequiresPerm("analysis:score:view")`（天然限定 role1/role2 持有者）+ Service 层角色校验：`UserContext` 取 userId，SQL 查 `traj.sys_user_role ⋈ traj.sys_role` 的 role_code，仅 `superAdmin()==true` 或含 `SUPER_ADMIN`/`SAFE_ADMIN` 放行，否则 40301。

### 5.4 单日计算流程（ScoreCalcService.calcDay(date)）

```
1. bindings = 绑定 SQL（§3.5）→ Map<identity_code, {driverId, plateNo, deptId}>；无绑定的车跳过
2. events   = 一条聚合 SQL（§3.2 剔除 FALSE_ALARM）：
   SELECT e.identity_code, e.event_code, MAX(e.risk_level) lv, COUNT(*) cnt
   FROM mon.risk_event e
   WHERE e.event_time >= :d0 AND e.event_time < :d1
     AND e.event_code = ANY(:whitelist)
     AND NOT EXISTS (工单 FALSE_ALARM)
   GROUP BY 1,2
3. 逐 binding 车辆：
   a. gps 点 SQL（identity + 当日，走 idx_traj_gps_point_identity_time）→ 差分/合并/样本统计
   b. 门槛检查（§3.5）：不过 → DELETE 该司机该日行（若存在）→ 下一车
   c. 扣分表 → score/level/features/event_count
   d. upsert（ON CONFLICT DO UPDATE，driver 维度；同司机多车当日取"合计"——
      当前种子数据一司机一车，多车场景按 driver_id 聚合各车特征后统一扣分，写明口径）
4. 日志：产出 N 行 / 跳过 M 车（无绑定 K、门槛不足 L）
```

---

## 6. 接口设计

统一响应 `Result<T>{code,message,data}`（HTTP 200 + 非 0 code 表业务失败）；Long id 出参一律 ToStringSerializer（VO 字段 String）。全部接口 `@RequiresPerm("analysis:score:view")`。

**数据范围**（与 MOD-IAM-001 车辆列表同口径）：page/summary/trend 均读 `UserContext.get().deptScope()`——`null` 不限制；空集合返回空结果；非空追加 `dept_id IN (scope)`。评分行 dept_id 快照自车辆，绑定变更后历史分数不迁移。

### 6.1 GET /api/analysis/score/page — 评分分页

参数：`page`(默认1)、`size`(默认10，≤100)、`startDate`/`endDate`(yyyy-MM-dd，缺省=最近 7 天)、`driverId`(可选，String)、`plateNo`(可选，模糊)、`level`(可选，A~E)。排序 `score_date DESC, score ASC`。

```json
{ "code": 0, "data": { "total": 123, "rows": [ {
  "id": "1", "scoreDate": "2026-10-02", "driverId": "1", "driverName": "张伟",
  "identityCode": "TERM_001", "plateNo": "京A12345", "deptId": "1",
  "score": 51.0, "level": "D", "samplePoints": 1820, "eventCount": 7,
  "features": { "SPEED_GENERAL": {"count":2,"deduct":8.0}, "…": {} }
} ] } }
```

driverName 由列表 SQL `LEFT JOIN traj.traj_driver` 带出（表不加列）。

### 6.2 GET /api/analysis/score/trend — 单人趋势

参数：`driverId`(必填)、`start`/`end`(缺省最近 30 天，跨度 ≤92)。返回按日期升序数组（无评分日缺项，前端断线）：

```json
{ "code": 0, "data": [ { "scoreDate": "2026-09-03", "score": 82.0, "level": "B" }, … ] }
```

### 6.3 GET /api/analysis/score/summary — 等级分布汇总

参数：`start`/`end`(缺省最近 7 天)。供本页顶部卡与 F15 大屏引用。口径：区间内"司机×日"快照计数（同一司机多日重复计）。

```json
{ "code": 0, "data": { "avgScore": 78.4, "recordCount": 412, "driverCount": 5,
  "levelDist": [ {"level":"A","count":80}, {"level":"B","count":150},
                 {"level":"C","count":100}, {"level":"D","count":62}, {"level":"E","count":20} ] } }
```

### 6.4 POST /api/analysis/score/recalc — 手动重算

请求/响应与错误见 §5.3：参数非法 46001；已有任务 46002；非 admin/SAFE_ADMIN 40301。成功 `{"code":0,"data":{"accepted":true,"start":"2026-09-01","end":"2026-09-30"}}`。加 `@AuditLog(module="ANALYSIS", action="UPDATE", actionName="评分重算", objectType="DRIVER_SCORE")`。

---

## 7. 后端实现结构（module-analysis）

```
backend-java/module-analysis/
├── pom.xml                         # parent=backend-java；依赖仅 platform-common + lombok（对齐 module-risk 样板，不依赖 module-notify）
└── src/main/java/com/mydbd/analysis/
    ├── entity/DriverScore.java     # @TableName(value="mon.driver_score", autoResultMap=true)；
    │                               #   features 用 @TableField(typeHandler=JacksonTypeHandler.class) Map<String,Object>；
    │                               #   id IdType.AUTO；score BigDecimal；无审计四列（表无 updater/update_date，
    │                               #   MybatisMetaObjectHandler 按实体字段填充，不受影响）
    ├── mapper/DriverScoreMapper.java       # upsert（@Insert ON CONFLICT DO UPDATE）、pageQuery(join driver)、
    │                                       # trend、summary（GROUP BY level + AVG）、deleteDayDriver
    ├── mapper/ScoreCalcMapper.java         # 跨 schema 只读输入：bindings、eventAgg(含 FALSE_ALARM 剔除)、gpsPoints
    ├── service/ScoreCalcService.java       # 算法核心（§3.4/§3.5/§5.4）+ AtomicBoolean 互斥 + 单线程重算执行器
    ├── service/ScoreQueryService.java      # page/trend/summary + deptScope 过滤
    ├── service/ScoreScheduler.java         # §5.2 每日 01:00
    ├── controller/ScoreController.java     # /api/analysis/score/**（§6）
    ├── dto/RecalcRequest.java              # {start,end,driverId?}，Bean Validation
    ├── vo/ScoreVO.java / TrendVO.java / SummaryVO.java    # id/driverId/deptId String
    └── constant/ScoreConstants.java        # 白名单、扣分表、阈值（2.5/30/200/30/封顶）、等级边界
```

时间参数 Java 侧 LocalDateTime/LocalDate 直传（无 psycopg2 转型问题）；`score_date` 边界用 `[dayStart, dayEnd)` 半开区间。

---

## 8. 前端设计

### 8.1 文件与路由（分配表 §3.2/§六边界）

- 新建 `api/analysis.ts`：`getScorePage / getScoreTrend / getScoreSummary / recalcScore`（id 一律 string）。
- 新建 `views/analysis/ScoreList.vue`；`router/index.ts` MainLayout 子路由追加 `/analysis/scores`（meta `{title:'驾驶评分', perm:'analysis:score:view'}`），只加自己的路由项。
- 菜单由 300/301 动态驱动；MainLayout 图标 map 增加 `DataAnalysis`（@element-plus/icons-vue，300 目录 icon）。

### 8.2 ScoreList.vue

- **顶部**：summary 卡（近 7 天均分、记录数、E 级人数）+ 等级分布迷你饼图 + "重算"按钮（仅 admin/SAFE_ADMIN 可见：`UserContext` 前端以 `userStore.superAdmin || perms` 判断——重算按钮显示条件 `superAdmin===true`，SAFE_ADMIN 用户由后端 40301 兜底，前端不做角色码判断）。重算弹对话框（日期区间 + 可选司机），提交后提示"任务已提交，稍后刷新"；46002 时 message 提示。
- **筛选**：日期范围（el-date-picker daterange，默认近 7 天）、司机（下拉，复用 MDM 司机列表接口，实现时确认 `api/mdm.ts` 现有 getDrivers 签名）、车牌（输入模糊）、等级（el-select，选项来自 `useDict('score_level')`）。
- **表格列**：日期、司机（driverName）、车牌、分数（数字着色：A/B 绿、C 橙、D/E 红）、等级（el-tag，type 映射 A=success B=primary C=warning D=warning E=danger，label 用字典）、主要扣分项 top3（features 按 deduct 降序前 3 非零项，渲染小 tag："超速×2 −8"）、事件数、样本点。
- **行点击 → 详情抽屉**（el-drawer 640px）：
  - features 明细表（10 特征 × 次数/扣分，全量含 0 项）；
  - **30 天趋势 sparkline**：调 trend 接口（driverId + 近 30 天）。定稿用 **ECharts**——`frontend/package.json` 已含 `echarts ^5.5.0` 且 RiskEvents.vue 已在用，无新增依赖风险；配置极简折线（无轴标签网格、高 60px、按分数值 visualMap 变色），不引轻量 SVG 方案以免双轨维护。

### 8.3 字典

`score_level` 走既有 `useDict(code)` composable（api/system getDictItemsByCode），无需新增字典接口。

---

## 9. 与 F23 的契约（分配表 §五）

- F23 **只读** `mon.driver_score`（+ risk_event/工单/干预），不回写本表。
- 消费面：`level` 与 `features`（含各特征 count/deduct 与 driving_minutes）即画像输入；周/月聚合、司机排名、等级迁移由 F23 对本表 GROUP BY 实现，F22 不预聚合。
- 接口面：F23 可直接引用 §6.1~6.3 三接口（同 perm），或同库 SQL 自取；`summary` 的 levelDist 结构冻结。
- 列与约束以分配表 §五为准，F22 后续加列需先改分配表。

---

## 10. 实施任务拆解

| 任务 | 内容 | 产出/验收 |
|---|---|---|
| T1 | `14-driving-score.sql`（表+索引+字典+菜单 300/301+role1/2 授权+setval）并手工执行；**module-analysis 注册三处**（父 pom modules+dependencyManagement、platform-boot 依赖）+ 模块骨架（pom/包结构） | 表与菜单落库；`mvn -pl module-analysis -am compile` 通过 |
| T2 | 后端：实体/Mapper（upsert+跨 schema 输入 SQL）/ScoreCalcService 算法/Scheduler/QueryService/Controller/VO/常量 | 接口可联调；单测覆盖差分合并与扣分表 |
| T3 | 前端：api/analysis.ts、ScoreList.vue（筛选/列表/抽屉/ECharts sparkline）、路由、图标 map、重算对话框 | `npm run build` 通过；页面可用 |
| T4 | 自测：造 risk_event + gps 点验证扣分正确性（§11） | 全项通过 |
| T5 | 重建 platform-app（必要时重启 portal-nginx，F18 教训）+ 实施记录回填本文档 | 环境可运行 |

## 11. 验收清单

1. **SQL**：重复执行 14 脚本幂等；driver_score 三索引存在；score_level 字典 5 项；菜单 300/301 仅 role1/2 可见。
2. **算法正确性（受控数据）**：为某 identity 造当日 220 点（含 4 次连续急加速段、3 次急减速段、speed 全程 >0）+ 2 条 SPEED_GENERAL + 1 条 SPEED_SEVERE + 1 条 DSM_DISTRACTION + 1 条 DSM_FATIGUE（其工单 close_result=FALSE_ALARM）→ 期望扣分 = 8+8+4（事件，疲劳误报剔除）+ 4×0.5（急加合并后段数按造数设计）+ 3×0.5 → score/level/features/event_count 逐项对账；急加减速连续段只计 1 次。
3. **门槛**：仅 150 点的司机当日无评分行；先产分后删点重算 → 旧行被删除。
4. **归属**：解绑主班司机后重算 → 该车当日不产分；换绑后重算历史日按当时绑定生效。
5. **重算**：recalc 正常区间返回 accepted；start>end / 跨度 93 天 → 46001；任务执行中再次触发 → 46002；DISPATCHER 用户调用 → 40301；upsert 幂等（重算两次结果一致）。
6. **查询与数据范围**：page/trend/summary 参数与响应符合 §6；deptScope=本部门及以下 的测试角色只见本企业子树司机的评分（对照 IAM 车辆列表口径）。
7. **定时**：临时把 cron 改为每 1 分钟 → 前一日评分自动产出；恢复 `0 0 1 * * ?`。
8. **前端**：admin 见"分析决策/驾驶评分"菜单；筛选、top3 扣分列、抽屉明细与 30 天 sparkline 渲染正常；重算按钮仅 admin 可见、SAFE_ADMIN 直调 API 成功；无权限用户直访 `/analysis/scores` 被路由守卫弹回。
9. **回归**：F18/F20 既有页面与接口不受影响（新模块零 Maven 依赖）。

## 12. 风险与注意事项

1. **@Scheduled 单实例**：与 F20 同——多实例部署需 ShedLock，本期演示单机不做。
2. **jsonb**：`autoResultMap=true` + JacksonTypeHandler 必须同时具备（项目既有经验，否则读出为字符串）。
3. **历史重算绑定漂移**：绑定表 status 为当前态，重算历史日一律用时间窗口径（§3.5），不依赖 status；此为口径约定，写死在 SQL 注释。
4. **模拟器 2s 采样**：急加减速阈值在 2s 间隔下 Δv 门槛 18 km/h，模拟器随机速度可能触发——演示数据分数偏低属预期，不做频率特化补偿。
5. **大区间重算耗时**：92 天 × 全量车逐日重算为分钟级，异步 + 互斥已覆盖；若未来数据量增长，先按 driverId 分批，不引入分布式调度。
6. **level 边界与字典一致性**：等级边界（90/75/60/40）在 ScoreConstants 与 14 脚本 remark、字典 label 三处同值，改动需同步。

## 13. 实施记录（v1.1，2026-10-03 完成）

### 13.1 落地清单

- **SQL**：`infra/postgres/init/14-driving-score.sql` 已执行入库（mon.driver_score + 3 索引 + uk、score_level 字典 5 项、菜单 300/301、role1 全量 + role2 授权）。
- **后端**：`module-analysis`（com.mydbd.analysis）三处 pom 注册完成；constant/entity/dto/vo/mapper×2/ScoreCalcService/ScoreQueryService/ScoreScheduler/ScoreController 全部落地；`mvn clean install` + `-pl platform-boot package` 通过，fat jar 部署 `mydbd-platform-app-1:/app/platform-app.jar`。
- **前端**：`api/analysis.ts`、`views/analysis/ScoreList.vue`（汇总卡/筛选/表格 Top3 扣分列/详情抽屉 ECharts 30 天趋势/重算对话框）、路由 `/analysis/scores`；`npm run build` 通过。
- **nginx**：`portal.conf` 新增 `location /api/analysis/score/` 指向 platform-app（更长前缀优先于既有 `/api/analysis/`→processing-app）。

### 13.2 设计偏差与修正（重要）

1. **关联键由 identity_code 改为 plate_no（vehicle_no）**：实测 `traj_vehicle_terminal` 无 identity_code 列（该列在 `traj_terminal`），且 risk_event.identity_code 多为空、GPS/事件的 identity（SIM_xxx/VEH_xxx）与终端 identity（TERM_xxx）体系不一致。车牌是三方唯一稳定业务键，`ScoreCalcMapper` 三条输入 SQL 全部按 plate_no 关联；GPS 走 `idx_traj_gps_point_plate_time`，事件聚合 GROUP BY plate_no。
2. **全日重算清扫**：`DriverScoreMapper.deleteDayExcept(date, keepIds)`——仅 `onlyDriverId==null` 的全日重算末尾删除该日不在保留司机集的旧行（覆盖解绑/换车后"不产分"，验收项 4）；driverId 过滤重算跳过清扫防误删他人。bindings 为空时同样清扫。
3. **互斥锁重构**：`ScoreCalcService.tryAcquire()/release()` 公开；控制器同步抢占判 46002，异步执行 finally release；`ScoreScheduler.dailyT1` 同样先 tryAcquire 失败即跳过（定时与手动共用一把锁）。

### 13.3 自测结果（T4 全过）

- 受控造数（隔离车牌 TESTPLT，221 点 @10s：4 连续急加速段 + 3 急减速段 + 200 巡航；2×SPEED_GENERAL + 1×SPEED_SEVERE + 1×DSM_DISTRACTION + 1×DSM_FATIGUE 误报工单）：score 76.5 / B，HARD_ACCEL×4=−2、HARD_BRAKE×3=−1.5、事件 −8−8−4、误报剔除、total_deduct 23.5、driving_minutes 35——逐项对账一致，连续段合并正确。
- 门槛：删点至 150 重算 → 旧行删除（total 0）。归属：解绑 driver1 全日重算 → 京A12345 行消失，恢复后重现。
- 重算：accepted 正常；start>end / 跨度 93 / end>今天 → 46001；执行中二次提交 → 46002；DISPATCHER（绑 menu301 后）调用 → 40301；32 天区间重算两次全表 md5 一致（upsert 幂等）。
- 查询：page/trend/summary 符合 §6；DISPATCHER 只见本部门子树（total 2）。
- 前端浏览器 E2E：菜单/汇总卡（85.2、5、5、A1 B4）/表格着色/Top3 tag/抽屉趋势图与特征明细/重算对话框，全 PASS，无 console error。
- 测试数据已全部清理还原（TESTPLT 相关行删除、disp01 还原 valid_mark=0、role3 的 300/301 授权删除）。

### 13.4 当前库态

`mon.driver_score` 5 行（均 2026-10-02，SIM_001~005 对应 5 台绑定车，分数 82~90，B/A）。09-01 的 VEH000xx 无主班绑定不产分。
