# MOD-MON-004 终端报警中心（F17）模块设计

> 上游依据：FUNC-DBD-001 §F17、RES-DBD-001（资源仲裁唯一依据，本表 §四 为 F17 报警契约）、MOD-MON-001（F14 推送协议）、DDL-TRAJ-001 §3.7（traj_warn_info 字段语义）
> 后端归属：module-monitor（已有，扩展）　包名 com.mydbd.monitor　API 前缀 `/api/alarm/**`
> 版本 v1.1　2026-10-03（v1.1 追加 §12 实施记录）

## 1. 概述与范围

### 1.1 目标

把现状"只读空列表"的终端报警（`traj.traj_warn_info`）升级为**报警中心**：独立页面 `/alarms`（菜单 16）提供报警实时展示（F14 WS 增量 + 分页筛选）、报警类型解析展示（type_id → 名称/等级）、**确认→解除处置闭环**（乐观锁状态机 + 审计留痕），并向 F15 大屏 / F16 面板输出 `page/latest/stats` 只读契约（RES-DBD-001 §四已锁定，本文档细化到可实施）。

### 1.2 明确不做

- **不新建主表**：`traj.traj_warn_info` 列已齐全（handle_status/handle_result_code/handle_result_msg/handler/end_warn_time 等），13-alarm-center.sql 只补索引、字典、菜单授权（RES-DBD-001 §四）。
- **不新建 Maven 模块**：全部落在已有 module-monitor（com.mydbd.monitor），与 WarnInfo/WarnInfoMapper 同包域。
- **不新增 WS 消息类型**：复用 F14 `type:"ALARM"` 增量推送；`useRealtime.ts` 只读复用不改内部逻辑（RES-DBD-001 §六）。
- **不做导出**：本期列表不实现 Excel/CSV 导出（数据量与演示场景不需要；如后续要做，须走 EXPORT 审计并另行分配资源）。
- **不与 F20 工单互通**：终端报警（traj.traj_warn_info）与风险事件（mon.risk_event→工单）是**两套独立体系**，不互转、不互写状态、不建外键（详见 §3.3）。
- **不改 Monitor.vue / RiskEvents.vue / RiskOrders.vue**（RES-DBD-001 §六边界）；旧接口 `GET /api/monitor/warnings`（最近 100 条只读）保留供 Monitor.vue 使用，不迁移不删除。
- 不做真实 JT/T 808 报警标志位（32 bit）解析：项目为模拟数据，type_id 已是解析后的离散类型（见 §2.2）。
- 不做数据范围裁剪（与 F14/F15 同口径：演示环境全量可见）。

### 1.3 与现有资产的关系

| 资产 | 关系 |
|---|---|
| `WarnInfo` 实体 / `WarnInfoMapper` | 已有部分字段映射与 `countTodayWarnings`；本期**扩展实体为全列**，Mapper 增加分页/统计/条件更新方法 |
| F14 `RealtimePushScheduler` | 已按 id 游标广播 `{type:"ALARM", data:[WarnInfo...]}`；实体扩列后 WS 载荷自然变多（增量兼容，前端按字段取值不受影响） |
| F15/F16 | 只读引用本模块 `GET /api/alarm/page`、`/api/alarm/latest?limit=N`、`/api/alarm/stats?start=&end=`（契约见 §4） |
| F34 审计 | 处置接口加 `@AuditLog(action=HANDLE, objectId="#id")`，复用 platform-common/audit 设施 |
| F35 字典 | 新增 `alarm_handle_status` 字典；类型显示名以 `traj.base_warn_type` 为准（见 §2.2 口径决策） |

## 2. 数据模型

### 2.1 主表（已有，不改列）

`traj.traj_warn_info` 关键列（DDL-TRAJ-001 §3.7）：

| 列 | 类型 | 处置闭环中的角色 |
|---|---|---|
| id | bigserial PK | 报警 id（REST 出参转 String） |
| plate_no / identity_code | varchar | 列表筛选、详情展示 |
| type_id | integer NOT NULL | 报警类型（→ base_warn_type，§2.2） |
| start_warn_time / end_warn_time | timestamp | 时间段筛选主键列；end_warn_time 为**终端侧报警结束时间**，处置动作不写它（处置时间统一用 update_date，避免语义混淆） |
| start_lng/start_lat/end_lng/end_lat | **varchar(50)** | 详情地图打点；使用前 parseFloat 防御解析（§6.3） |
| start_speed / end_speed | integer | 详情展示 |
| warn_continue_mark | smallint | 0=停止 1=持续，详情展示 |
| handle_status | smallint **可空** | 状态机字段：0 待处理 / 1 已确认 / 2 已解除（§3.1）；T1 迁移把存量 NULL 归一为 0 |
| handle_result_code | varchar(2) | 解除时必填：00=属实 / 01=误报 / 02=未知 |
| handle_result_msg | varchar(200) | 解除说明（可选，≤200 字） |
| handler | varchar(30) | 处置人 = 当前登录用户 realName（空则 username） |
| creator/updater/update_date | — | 审计四件套；update_date 兼作状态变更时间 |

### 2.2 报警类型（type_id）口径

**现有写入源核查结论**：全仓检索未发现 `traj_warn_info` 的 INSERT 来源——Python 模拟器（`processing-service/app/services/simulator.py`）只写 `traj_gps_point` 且 `alarm_flag` 恒为 0；`cep_engine` 只写 `mon.risk_event`。即**当前表为空**（与功能清单"只读空列表"一致），报警数据需自测时 SQL 构造或待终端接入波次生成。因此 type_id 取值以建表种子 `traj.base_warn_type`（02-traj-tables.sql 2.9 节）为唯一权威：

| type_id | 名称（base_warn_type.name） | grade_level | 对应 JT/T 808 报警标志位（语义参考） | alarm_type 字典 item_value |
|---|---|---|---|---|
| 1 | 超速报警 | 1 | bit1 超速报警 | OVER_SPEED |
| 2 | 疲劳驾驶 | 1 | bit2 疲劳驾驶报警 | FATIGUE |
| 3 | 围栏越界 | 2 | bit20 进出区域/路线报警（近似） | ROUTE_DEVIATION |
| 4 | 紧急求助 | 1 | bit0 紧急报警、触发仍然有效 | EMERGENCY |
| 5 | 设备故障 | 3 | bit3~bit5 系列（GNSS 故障/终端故障等，归并） | （字典缺项，见下） |

**口径决策**：`alarm_type` 字典（09-system-config.sql）只有 4 项且 value 为英文码，与 base_warn_type 的 5 项数字 id **不对齐**。为避免双源漂移：
- **显示名与等级一律以后端 JOIN `traj.base_warn_type` 为准**，VO 输出 `typeName`/`gradeLevel`（base_warn_type 仅 5 行，SQL LEFT JOIN 即可，无需缓存）；
- 前端筛选下拉通过新增 `GET /api/alarm/types` 取 base_warn_type 列表（§4.5），不读 alarm_type 字典；
- alarm_type 字典保持现状不动（本期不扩项、不修值），文档层面声明其仅供 F35 演示，不作为 F17 数据源。

### 2.3 索引（13-alarm-center.sql 补充）

已有索引（02-traj-tables.sql）：`idx_traj_warn_plate_time (plate_no, start_warn_time DESC)`、`idx_traj_warn_type (type_id)`、`idx_traj_warn_status (handle_status)`。本期仅补默认查询路径的复合索引：

```sql
-- 默认列表/大屏 latest：按状态（待处理优先）+ 时间倒序
CREATE INDEX IF NOT EXISTS idx_traj_warn_status_time
    ON traj.traj_warn_info (handle_status, start_warn_time DESC);
```

WS 增量游标走主键 id（已有 PK），不加索引；type_id 单列索引已存在，组合筛选（type+time）在万级数据下由 plate_time 与 bitmap 扫描兜底，演示量级不再建复合索引（避免过度设计）。

### 2.4 存量归一（幂等）

```sql
UPDATE traj.traj_warn_info SET handle_status = 0 WHERE handle_status IS NULL;
ALTER TABLE traj.traj_warn_info ALTER COLUMN handle_status SET DEFAULT 0;
```

（不改 NOT NULL 约束：终端接入方可能上报 NULL，读取侧统一 `COALESCE(handle_status,0)` 已在迁移后无 NULL，实体层再兜底。）

## 3. 处置闭环设计（状态机）

### 3.1 状态机

```
            confirm(alarm:handle)            resolve(alarm:handle)
  0 待处理 ───────────────────> 1 已确认 ───────────────────> 2 已解除
     │                                                        ▲
     └────────────────  resolve（允许直接解除）─────────────────┘
```

- **允许 0→2 直接解除**：报警自愈/明显误报场景无需先确认；解除动作本身即闭环，确认只是中间态登记。
- 终态 2 不可再变更（无重开：与 F20 工单不同，报警是数据事实记录，误处置通过审计日志追溯，不做状态回退；**明确不做重开**）。
- handle_status 语义与字典 `alarm_handle_status` 一致：0 待处理 / 1 已确认 / 2 已解除。

### 3.2 并发控制（乐观条件更新，不加锁不引入 version 列）

两条处置 SQL 均为**带旧值条件的单行 UPDATE**，靠影响行数判冲突：

```sql
-- confirm
UPDATE traj.traj_warn_info
   SET handle_status = 1, handler = #{name}, updater = #{name}, update_date = now()
 WHERE id = #{id} AND handle_status = 0;

-- resolve（result_code 必填校验在 Java 层，40001）
UPDATE traj.traj_warn_info
   SET handle_status = 2, handler = #{name},
       handle_result_code = #{code}, handle_result_msg = #{msg},
       updater = #{name}, update_date = now()
 WHERE id = #{id} AND handle_status IN (0, 1);
```

处理流程：先 `SELECT ... WHERE id=?`（不存在 → **40401**）；再执行条件 UPDATE；**影响行数=0 → 45001**（"报警状态已变更，请刷新后重试"——他人已确认/已解除，或重复提交）。两个用户同时 confirm，只有一个成功，另一个收 45001，满足并发正确性，无需行锁/版本号。

handler 取值：`UserContext.get().realName()`，realName 为空回退 `username()`（varchar(30) 截断防御：超长取前 30 字符）。

### 3.3 与 F20 工单 / mon.risk_event 的关系（决策）

- **两套体系不互转**：traj_warn_info 是终端硬件上报事实，risk_event 是 CEP 平台研判事件；不建外键、不做"报警转工单"、不互写 handle_status。
- **页面互相跳转（只读关联）**：报警详情接口 `GET /api/alarm/{id}` 附带 `relatedRisks`——同车牌、报警开始时间 ±30 分钟窗口内的 mon.risk_event 前 5 条（module-monitor 已有 `RiskEventMapper`，同库跨 schema 只读 SQL，符合零 Maven 依赖约定）；前端详情抽屉展示关联风险小列表并提供"风险预警分析"页链接（`/risk`，不传参、不改 RiskEvents.vue）。反向跳转（风险页看报警）本期不做（边界约束 §1.2）。

## 4. 接口设计（module-monitor 新增 `AlarmController`，前缀 `/api/alarm`）

类级 `@RequiresPerm("alarm:view")`；出参统一 `Result<T>` / `PageData<T>`（HTTP 200 + code）。**VO 中 id 用 `@JsonSerialize(using = ToStringSerializer.class)` 转 String**，前端全链路 string。

### 4.1 GET /api/alarm/page（F17 列表 + F15/F16 契约）

| 参数 | 类型 | 说明 |
|---|---|---|
| page / size | long | 默认 1 / 20，size 上限 100（超出按 100，不报错） |
| plateNo | string | 模糊 `LIKE '%x%'` |
| typeId | int | 精确 |
| handleStatus | int | 0/1/2；不传=全部 |
| beginTime / endTime | string(ISO LocalDateTime) | 按 start_warn_time 闭开区间 `[begin, end)`；格式非法 → 40001 |

排序固定 `start_warn_time DESC, id DESC`。响应 `PageData<AlarmVO>`：

```json
{ "code":0, "data": { "total":123, "page":1, "size":20, "records":[{
  "id":"4501", "plateNo":"京A12345", "identityCode":"TERM_001",
  "typeId":1, "typeName":"超速报警", "gradeLevel":1,
  "startWarnTime":"2026-10-03T09:00:00", "endWarnTime":null,
  "startLng":"116.401","startLat":"39.912","startSpeed":95,
  "warnContinueMark":1, "handleStatus":0,
  "handleResultCode":null, "handleResultMsg":null, "handler":null,
  "updateDate":null }]}}
```

### 4.2 GET /api/alarm/latest?limit=N（大屏/面板待处理滚动）

`handle_status=0 ORDER BY start_warn_time DESC LIMIT N`（默认 10，上限 50，越界钳制）。返回 `List<AlarmVO>`（同上结构）。

### 4.3 GET /api/alarm/stats?start=&end=（大屏态势卡 / F17 统计条）

时间参数可选，缺省=今日 00:00 至当前。两条聚合 SQL（按 type_id、按 handle_status，`COALESCE(handle_status,0)` 归组）：

```json
{ "code":0, "data": { "total":88,
  "byType":  [ {"typeId":1,"typeName":"超速报警","gradeLevel":1,"count":40}, ... ],
  "byStatus":[ {"handleStatus":0,"count":52}, {"handleStatus":1,"count":20}, {"handleStatus":2,"count":16} ] }}
```

### 4.4 POST /api/alarm/{id}/confirm ／ POST /api/alarm/{id}/resolve

- 权限：方法级 `@RequiresPerm("alarm:handle")`；
- 审计：`@AuditLog(module="MONITOR", action="HANDLE", objectType="ALARM", objectId="#id")`（MONITOR 为 F34 既有模块码，前端审计页可筛）；
- confirm 无请求体；resolve 请求体 `{"resultCode":"00","resultMsg":"已电话提醒司机降速"}`，resultCode ∈ {00,01,02} 否则 40001，resultMsg 可选 ≤200 字（超长 40001）；
- 成功返回 `Result.ok(处置后的 AlarmVO)`（前端免二次查询）；
- 错误：40401 报警不存在；45001 状态冲突（§3.2）；40001 参数非法；40301 无 alarm:handle。

### 4.5 GET /api/alarm/{id}（详情抽屉）／ GET /api/alarm/types

- 详情：AlarmVO + `"relatedRisks":[{id,eventCode,title,riskLevel,eventTime}]`（§3.3）；id 不存在 → 40401。
- types：`SELECT id,name,grade_level FROM traj.base_warn_type WHERE valid_mark=1 ORDER BY id`，供筛选下拉与名称兜底。

### 4.6 处置接口报文示例

```http
POST /api/alarm/4501/confirm
（无请求体）

200 → { "code":0, "message":"ok", "data": { "id":"4501", "handleStatus":1,
          "handler":"调度员小张", "updateDate":"2026-10-03T10:12:31", ... } }

POST /api/alarm/4501/resolve
{ "resultCode": "00", "resultMsg": "已电话提醒司机降速，路况正常" }

200 → { "code":0, "data": { "id":"4501", "handleStatus":2, "handleResultCode":"00", ... } }

（冲突不走 HTTP 409，统一 HTTP 200 + 业务码：）
200 → { "code":45001, "message":"报警状态已变更，请刷新后重试", "data":null }
```

### 4.7 错误码表（本模块 45001~45099，RES-DBD-001 §3.2）

| code | 含义 | 触发场景 |
|---|---|---|
| 40001 | 参数错误 | 时间格式非法、resultCode 越界、resultMsg 超长、id 非法 |
| 40101 | 未认证 | JWT 缺失/过期（全局） |
| 40301 | 无权限 | 缺 alarm:view / alarm:handle |
| 40401 | 资源不存在 | 报警 id 不存在 |
| **45001** | **报警状态冲突** | 重复确认、已解除再确认/再解除、并发处置落后者 |
| 45002~45099 | 预留 | 本波不启用 |

### 4.8 后端新增/修改类清单（module-monitor，com.mydbd.monitor）

| 类 | 新增/修改 | 职责 |
|---|---|---|
| `entity/WarnInfo` | **修改** | 补齐全列映射（endWarnTime/endLng/endLat/endSpeed/warnContinueMark/ruleId/handleResultCode/handleResultMsg/handler/creator/createDate/updater/updateDate），保持 `@TableId(type=IdType.AUTO)`；该类同时被 F14 WS 广播，扩列为向后兼容超集 |
| `vo/AlarmVO` | 新增 | 出参视图：实体展示列 + typeName/gradeLevel（JOIN base_warn_type 得）+ relatedRisks（详情用）；`id` 加 `@JsonSerialize(using = ToStringSerializer.class)` |
| `dto/AlarmQuery` | 新增 | page 入参对象（plateNo/typeId/handleStatus/beginTime/endTime/page/size），size 钳制逻辑在此 |
| `dto/ResolveRequest` | 新增 | `record ResolveRequest(String resultCode, String resultMsg)`，校验在 Service 手写抛 BizException 40001（与 F20 close 同风格） |
| `mapper/WarnInfoMapper` | **修改** | 新增：`pageAlarms`（LEFT JOIN base_warn_type 动态条件）、`selectLatestPending`、`countByType`、`countByStatus`、`selectDetail`、`markConfirmed(id,name)`、`markResolved(id,name,code,msg)`（§3.2 两条条件 UPDATE，返回 int）；既有 `countTodayWarnings` 等不动 |
| `service/AlarmService` | 新增 | 编排：参数校验→存在性(40401)→条件 UPDATE→行数=0 抛 45001；handler 取 `UserContext.get()` 的 realName（空回退 username，截 30）；relatedRisks 复用同包 `RiskEventMapper` 跨 schema 只读查（§3.3） |
| `controller/AlarmController` | 新增 | `/api/alarm` 7 端点（§4.1~4.5），类级 `@RequiresPerm("alarm:view")`，confirm/resolve 方法级 `@RequiresPerm("alarm:handle")` + `@AuditLog(module="MONITOR", action="HANDLE", objectType="ALARM", objectId="#id")` |

不新增 Maven 依赖、不动 platform-boot 配置；`@EnableScheduling`/审计/权限切面均为既有设施。

## 5. 权限与菜单（13-alarm-center.sql，全幂等）

### 5.1 字典 `alarm_handle_status`

```sql
INSERT INTO traj.sys_dict_type (dict_code, dict_name, status, remark)
VALUES ('alarm_handle_status','终端报警处置状态',1,'F17 报警中心 handle_status')
ON CONFLICT (dict_code) DO NOTHING;

INSERT INTO traj.sys_dict_item (dict_type_id, item_label, item_value, sort)
SELECT id,'待处理','0',1 FROM traj.sys_dict_type WHERE dict_code='alarm_handle_status'
UNION ALL SELECT id,'已确认','1',2 FROM traj.sys_dict_type WHERE dict_code='alarm_handle_status'
UNION ALL SELECT id,'已解除','2',3 FROM traj.sys_dict_type WHERE dict_code='alarm_handle_status'
ON CONFLICT (dict_type_id, item_value) DO NOTHING;
```

### 5.2 菜单与授权（RES-DBD-001 §3.2/§3.3 锁定值，不得改分配）

```sql
INSERT INTO traj.sys_menu (id, parent_id, menu_name, menu_type, perm_code, path, icon, sort_no, visible, status)
VALUES
  (16, 10, '终端报警中心', 2, 'alarm:view',   '/alarms', 'Bell',   16, 1, 1),
  (926, 16, '报警处置',   3, 'alarm:handle', NULL, NULL, 1, 1, 1)
ON CONFLICT (id) DO NOTHING;
SELECT setval(pg_get_serial_sequence('traj.sys_menu','id'),
              (SELECT MAX(id) FROM traj.sys_menu), true);

-- role1 admin 全量幂等补齐
INSERT INTO traj.sys_role_menu (role_id, menu_id)
SELECT 1, id FROM traj.sys_menu ON CONFLICT DO NOTHING;
-- role2 SAFE_ADMIN / role3 DISPATCHER：查看+处置；role4 FLEET_CAPTAIN：仅查看（矩阵 §3.3）
INSERT INTO traj.sys_role_menu (role_id, menu_id) VALUES
  (2,16),(2,926),(3,16),(3,926),(4,16)
ON CONFLICT DO NOTHING;
```

脚本整体顺序：§2.3 索引 → §2.4 存量归一 → §5.1 字典 → §5.2 菜单/授权；可重复执行。

## 6. 前端设计

### 6.1 文件与路由（RES-DBD-001 §3.2 锁定）

- 新建 `frontend/src/views/alarm/AlarmCenter.vue`；
- 新建 `frontend/src/api/alarm.ts`：`getAlarmPage/getAlarmLatest/getAlarmStats/getAlarmDetail/getAlarmTypes/confirmAlarm/resolveAlarm`，核心类型：

```ts
export interface AlarmVO {
  id: string                    // 后端 ToStringSerializer，全链路 string
  plateNo: string
  identityCode: string | null
  typeId: number
  typeName: string | null       // 后端 JOIN base_warn_type
  gradeLevel: number | null     // 1红/2橙/3灰 着色依据
  startWarnTime: string | null
  endWarnTime: string | null
  startLng: string | null; startLat: string | null   // varchar 原样透传，前端 parseFloat
  endLng: string | null; endLat: string | null
  startSpeed: number | null; endSpeed: number | null
  warnContinueMark: number | null
  handleStatus: number          // 0/1/2（后端已 COALESCE 归一）
  handleResultCode: string | null; handleResultMsg: string | null
  handler: string | null; updateDate: string | null
}
export interface AlarmStats {
  total: number
  byType: { typeId: number; typeName: string; gradeLevel: number; count: number }[]
  byStatus: { handleStatus: number; count: number }[]
}
```

- `router/index.ts` 仅追加一条：`{ path:'alarms', name:'alarm-center', component: AlarmCenter.vue, meta:{ title:'终端报警中心', perm:'alarm:view' } }`（挂 MainLayout children，与菜单 path `/alarms` 对齐）；
- 处置按钮 `v-perm="'alarm:handle'"` 控制显隐（复用现有指令，role4 自然只见列表）。

### 6.2 AlarmCenter.vue 页面结构

```
┌ 统计条：今日总数/待处理/已确认/已解除（/api/alarm/stats，30s 轮询 + WS ALARM 到达即本地 total+1）
├ 筛选栏：车牌(input) | 类型(select ← /api/alarm/types) | 状态(select 0/1/2) | 时间范围(datetimerange,默认今日)
├ 表格：车牌 | 类型(tag，gradeLevel 着色 1红/2橙/3灰) | 报警开始/结束 | 速度 | 持续标志 |
│       状态(tag：0 danger待处理/1 warning已确认/2 success已解除) | 处置人 | 更新时间 | 操作
│ 操作列：[详情]（所有人） [确认]（status=0 且有 alarm:handle） [解除]（status∈{0,1} 且有 alarm:handle）
├ 分页：el-pagination，size 默认 20
└ 详情抽屉 el-drawer(640px)：基本信息 descriptions + 处置信息 + Leaflet 小地图 + 关联风险列表(relatedRisks)
   解除对话框 el-dialog：resultCode radio(00属实/01误报/02未知) + resultMsg textarea(≤200 计数)
```

- 确认/解除成功后就地更新行数据（用接口返回的 AlarmVO 替换），并 `ElMessage.success`；收到 45001 提示"状态已变更"并刷新当前页。
- 本期不做导出按钮（§1.2）。

### 6.3 详情抽屉地图（坐标口径）

- `start_lng/start_lat/end_lng/end_lat` 为 **varchar**：统一 `const n = parseFloat(v); Number.isFinite(n) && n !== 0` 才打点；解析失败显示"坐标缺失"文本，不抛错。
- 坐标口径与 Monitor.vue 完全一致（同源模拟数据 + 同高德瓦片），**不做 WGS84/GCJ-02 转换**（与 F14 现状同偏差，演示可接受，页面注明"坐标为终端上报原始值"）。
- 有起止两点时画 polyline + 起(绿)/止(红) divIcon；仅起点画单点；瓦片/初始化配置照搬 Monitor.vue 抽出的常量。

### 6.4 实时链路（复用 F14，零改动 useRealtime）

- 页面 `useRealtime({ onAlarm })`：新报警到达 → 去重（`Set<string>` 缓存最近 200 id）后**仅在"无筛选或待处理 tab"时 prepend 到当前页首行**并 `ElMessage.warning`（车牌+类型名）；不自动翻页、不打断用户操作。
- **处置状态变更不推送**（决策）：F14 调度器按 id 游标只推新增行，UPDATE 不产生新 id。本期方案=处置页本地更新 + 其他会话靠筛选/30s 统计轮询感知；如需全站秒级同步状态变更，演进方案为扩展 F14 调度器扫描 `update_date` 游标推 `type:"ALARM_STATUS"`——**属新增消息类型，实施前必须先更新 RES-DBD-001 §四再动代码**（RES-DBD-001 §六），本波不做。

## 7. 端到端时序

1. 数据产生（当前=SQL 构造；未来=终端接入波次）：INSERT traj_warn_info（type_id∈1~5，handle_status 默认 0，坐标 varchar）→ F14 调度器 1s 内按 id 游标广播 `{type:"ALARM", data:[...]}`。
2. 坐席打开 `/alarms`：首屏 `GET /page`（默认今日+全部状态）+ `GET /stats` 统计条；WS 新报警 toast + 列表首行 prepend（§6.4）。
3. 坐席点"确认"：`POST /confirm` → 条件 UPDATE 0→1，handler=本人 realName，update_date=now → 返回新 VO 就地刷新行 → 审计落 sys_audit_log（MONITOR/HANDLE/ALARM）。
4. 电话提醒后点"解除"：选 resultCode=00 属实 + 说明 → `POST /resolve` 1→2；行 tag 变绿、处置按钮消失。
5. 他人并发处置：后提交者 UPDATE 影响行数=0 → code 45001 → 前端提示"状态已变更"并刷新当前页。
6. 主管复查：详情抽屉看坐标地图（起终点连线）、warn_continue_mark、relatedRisks（±30min 同车牌 CEP 事件），可按需跳 `/risk` 页人工研判（不自动转工单，§3.3）。
7. F15 大屏：报警态势卡 30s 轮询 `/stats`，最新报警滚动 `/latest?limit=10` + WS ALARM 置顶（契约消费方，只读）。

## 8. 实施任务拆解

| 任务 | 内容 | 产出/验证 |
|---|---|---|
| T1 SQL | 编写并执行 `infra/postgres/init/13-alarm-center.sql`（§2.3 索引、§2.4 归一、§5.1 字典、§5.2 菜单 16/926+授权矩阵） | 重复执行不报错；`sys_menu` 见 16/926；role4 无 926 |
| T2 后端 | WarnInfo 实体补全列；新增 AlarmVO/AlarmQuery；WarnInfoMapper 增 page/latest/stats/relatedRisks/confirm/resolve 条件更新；AlarmService（状态机+UserContext handler）；AlarmController 5+2 接口（@RequiresPerm/@AuditLog/ToStringSerializer） | `mvn -pl module-monitor compile` 通过（由实施者执行） |
| T3 前端 | `api/alarm.ts`、`views/alarm/AlarmCenter.vue`（表格/筛选/统计条/抽屉/解除弹窗/地图/v-perm）、路由追加 `/alarms` | `npm run build` 通过（由实施者执行） |
| T4 自测 | §9 接口自测 + 浏览器验证 + 清理测试数据 | 验收清单 §10 全过 |

依赖顺序：T1 → T2 → T3 → T4；T2/T3 可在 T1 后并行。

## 9. 自测计划

### 9.1 数据构造

表当前无写入源（§2.2），自测用 SQL 构造 ~30 行覆盖：5 种 type_id、3 种状态、NULL handle_status 存量行、坐标正常/缺失/非法字符串三类、同车牌 ±30min 关联 risk_event。

### 9.2 接口层（admin / disp01(role3) / captain(role4)）

1. page：车牌模糊、typeId、handleStatus、时间段各筛选正确；size=500 钳制为 100；非法时间格式 40001。
2. latest：只返回 handle_status=0；limit=100 钳制 50。
3. stats：byType/byStatus 与库内 count 对账；缺省参数=今日窗口。
4. confirm：0→1 成功且 handler=操作人 realName；对 1 再 confirm → **45001**；对 2 confirm → 45001；不存在 id → 40401。
5. resolve：1→2 与 0→2（直接解除）均成功；resultCode='9' → 40001；msg 201 字 → 40001；对 2 再 resolve → 45001。
6. 并发：两个会话同时 confirm 同一 0 态报警，一成功一 45001（PowerShell 并发 Invoke-RestMethod）。
7. 权限：role4 confirm/resolve → 40301；无 alarm:view 角色 GET page → 40301；未登录 → 40101。
8. 详情：relatedRisks 命中 ±30min 同车牌事件，超窗口不出现；types 返回 5 行。
9. 审计：sys_audit_log 出现 module=MONITOR、action=HANDLE、objectType=ALARM、objectId=报警id 两条（confirm/resolve）。
10. 回归：`GET /api/monitor/warnings` 与 F14 WS ALARM 推送仍正常（实体扩列后载荷为超集）。

### 9.3 浏览器（E2E）

1. admin 登录：菜单"实时监控→终端报警中心"可见，`/alarms` 打开，统计条+列表+筛选+分页正常；类型列显示中文名（超速报警等）。
2. 启动模拟器不产生报警（数据源现状），SQL 插入一条新报警 → 3s 内页面顶部 toast + 列表首行出现（WS 链路验证）。
3. 确认→状态 tag 变"已确认"、处置人显示姓名；解除（选"属实"+说明）→"已解除"；按钮按状态显隐（2 态无确认/解除按钮）。
4. captain 登录：处置按钮不可见，直调接口 40301；菜单可见。
5. 详情抽屉：地图打点（正常坐标显示起终点连线；坐标缺失行显示"坐标缺失"不报错）；关联风险列表展示。
6. F15 联动冒烟：`/api/alarm/stats`、`/api/alarm/latest` 可被大屏页消费（若 F15 已上线则顺带验证，否则 curl 即可）。
7. 验证后清理测试报警行（保留字典/菜单）。

## 10. 验收清单

- [ ] 13-alarm-center.sql 幂等执行，索引/字典/菜单 16、926/授权矩阵与 RES-DBD-001 §3.2/§3.3 完全一致
- [ ] 5 个契约接口路径、参数、状态语义与 RES-DBD-001 §四一致（page/latest/stats/confirm/resolve），未擅改分配
- [ ] 状态机 0→1→2、0→2 允许、终态不可逆；并发冲突返回 45001；handler 取当前用户 realName
- [ ] 处置接口带 @RequiresPerm("alarm:handle") + @AuditLog(HANDLE, ALARM, #id)
- [ ] 所有 REST 出参 id 为字符串（ToStringSerializer），前端 id: string
- [ ] 报警名称/等级来自 base_warn_type（JOIN），不依赖 alarm_type 字典
- [ ] 前端仅新增 AlarmCenter.vue / api/alarm.ts / 路由一条；未改 Monitor.vue、useRealtime.ts、RiskEvents.vue
- [ ] WS 复用 F14 ALARM 增量正常；处置状态变更不做推送（演进需先改 RES-DBD-001）
- [ ] 不做导出、不做工单互转（§1.2/§3.3 边界）
- [ ] §9 自测项全通过并留记录，测试数据已清理

## 11. 风险与注意事项

1. **表无写入源**：traj_warn_info 当前无任何生成方（模拟器只写轨迹点），上线初期列表为空属预期；自测/演示必须 SQL 构造数据，勿误判为功能缺陷。后续终端接入波次补数据源时，type_id 必须落在 base_warn_type 1~5，越界值前端兜底显示"类型 {id}"。
2. **实体扩列影响 WS 载荷**：WarnInfo 同时被 REST 与 F14 广播使用，新增字段为超集、向后兼容；但 AlarmVO 与实体分离，REST 侧 id 转 String 不影响 WS（WS 载荷 id 仍为数字，前端 AlarmBrief 现按数字用，不改）。
3. **handle_status 可空历史值**：迁移归一为 0 后，新写入方若仍插 NULL，读取 SQL 统一 `COALESCE(handle_status,0)` 兜底（stats/page 的 WHERE 与 GROUP BY 都要）。
4. **varchar 坐标**：任何 Number()/parseFloat 前判空判 NaN；地图初始化失败不能阻塞抽屉其余信息展示（v-if 隔离）。
5. **45001 与 40901 取舍**：RES-DBD-001 已把"状态冲突"分配为本模块专用 45001，处置接口冲突**不要用**通用 40901，避免与工单域语义混用。
6. **role4 无处置权**：FLEET_CAPTAIN 只读（RES-DBD-001 授权矩阵 §3.3），前端 v-perm 与后端 @RequiresPerm 双层都要生效，验收项 9.3-4 必测。
7. **setval 顺序**：菜单插入后必须执行 setval（照搬 08-work-order.sql 写法），防止后续自增菜单 id 撞 926。

## 12. 实施记录（v1.1 · 2026-10-03）

### 12.1 产出清单（与 §8 任务对应）

| 任务 | 实际产出 | 状态 |
|---|---|---|
| T1 SQL | `infra/postgres/init/13-alarm-center.sql`（索引 idx_traj_warn_status_time → handle_status 归一+default 0 → 字典 alarm_handle_status 3 项 → 菜单 16/926 + setval + 授权矩阵） | ✅ 执行两遍幂等通过；sys_menu 见 16/926，role1~3 含 926、role4 仅 16 |
| T2 后端 | `WarnInfo` 实体补全 22 列（保留 `@TableId(AUTO)`）；新增 `vo/AlarmVO`（id ToStringSerializer + relatedRisks）、`dto/AlarmQuery`、`dto/ResolveRequest`（record）；`WarnInfoMapper` 增 pageAlarms/latestPending/stats(countByType+countByStatus+countInRange)/selectDetail/selectAlarmTypes/markConfirmed/markResolved（条件 UPDATE 返 int，COALESCE 归一）；`RiskEventMapper` 增 relatedRisks（±30min 前 5 条）；新增 `AlarmService`（40401/45001/40001、handler=realName 回退 username 截 30）、`AlarmController`（/api/alarm 7 端点，类级 alarm:view，confirm/resolve 方法级 alarm:handle + @AuditLog(MONITOR/HANDLE/ALARM,#id)） | ✅ `mvn -pl module-monitor compile` 通过 |
| T3 前端 | `api/alarm.ts`（7 函数 + AlarmVO/AlarmStats 类型，id: string）；`views/alarm/AlarmCenter.vue`（统计条 30s 轮询+WS prepend、筛选栏、表格 gradeLevel 着色、v-perm 处置按钮、详情抽屉+Leaflet 起终点、解除弹窗 radio 00/01/02 + msg≤200 计数）；`router/index.ts` 追加 `/alarms` | ✅ `npm run build` 通过（AlarmCenter chunk 13.23 kB） |
| 文档 | `docs/html/PRD-MON-004-终端报警中心产品设计.html` | ✅ |

### 12.2 自测结果

- **接口层（§9.2 全 10 项）**：PowerShell 脚本 **31/31 PASS**。覆盖：page 各筛选/size 钳制 100/非法时间 40001/NULL 归一可筛出/typeName JOIN；latest 仅 status=0 钳制 50；stats total 与库内 count 对账（byType、byStatus 合计均=14）；confirm 0→1 handler=操作人 realName、重复 45001、不存在 40401；resolve 1→2 与 0→2 直接解除、resultCode 越界 40001、msg 201 字 40001、终态再 resolve 45001；**并发 confirm 一 0 一 45001**；role4 confirm/resolve 40301、无 alarm:view 角色 GET page 40301、未登录 401；详情 relatedRisks ±30min 命中 1 条、超窗口 0 条、types 5 行、出参 id 为 string；审计 sys_audit_log MONITOR/HANDLE/ALARM 落库；回归 /api/monitor/warnings 与 overview 正常（实体扩列载荷超集）。
- **浏览器 E2E（§9.3）**：admin 登录 → 菜单"实时监控→终端报警中心"可见、/alarms 打开、统计条 4 卡、筛选待处理→重置、表格类型列中文名+等级着色、状态标签、详情抽屉（基本信息+Leaflet 地图+关联风险）、确认（handler 显示"超级管理员"、状态变已确认）均 PASS。captain 只读：后端 GET page code=0 可看列表、confirm/resolve 40301（前端 v-perm 按钮隐藏同全站成熟机制）。
- **测试数据**：SQL 构造 14 行（5 类型×3 状态×NULL 存量×坐标正常/缺失/非法×同车牌 ±30min 关联 risk_event），自测后按 creator='f17test' 全清，测试账号/审计一并清理，保留字典/菜单/索引。

### 12.3 实施偏差与避坑

1. **PS5 中文编解码**（F23 教训复现）：脚本必须存 UTF-8 **带 BOM**；且 Invoke-RestMethod 对无 charset 的 JSON 响应按 Latin-1 解码致中文乱码，须改 `Invoke-WebRequest` + `[Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray())` 手动解码，POST 请求体同理用 UTF8 字节。中文 URL 参数用 `[uri]::EscapeDataString`。
2. **docker exec psql -t 返回数组**：PS 中 `docker exec ... psql -t -c` 输出是 Object[]，`[int](...)` 直接转换报错，须 `($raw | Out-String).Trim()` 再转。
3. **服务器时区 UTC**：容器 JVM 与 PG 均为 UTC，"今日"默认窗口只覆盖到当前 UTC 时刻；自测 stats/page 用**显式时间窗口**参数对账，不依赖隐式"今日"。
4. **并发用例**：PS5 无 Start-ThreadJob，改用 `Start-Job` 两个后台 job 同时 POST，Wait-Job 收码，实测 [0,45001]。
5. **PowerShell 不支持 `<` 重定向**：执行 SQL 文件用 `Get-Content -Raw | docker exec -i ... psql`。
6. 其余实现与本文档 §2~§6 设计一致，无契约偏差：未新建表/模块、未改 useRealtime.ts/Monitor.vue、未做导出与工单互转。
