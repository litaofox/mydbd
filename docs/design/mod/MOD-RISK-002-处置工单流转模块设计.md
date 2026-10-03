# MOD-RISK-002 处置工单流转（F20）模块设计

> 上游：F18 CEP（mon.risk_event）　下游预留：F19（多渠道通知）、F21（干预记录）、F14（WebSocket）
> 对应 PRD：docs/html/PRD-RISK-002-处置工单流转产品设计.html
> 版本 v1.0　2026-10-02

## 1. 方案总览

工单是风险事件的 1:1 运营视图。事件仍由 Python CEP 写入 `mon.risk_event`，在同一事务内补建 `mon.risk_work_order`；状态机、分派、SLA、闭环全部在 Java 侧 module-risk 内新增（不动既有规则/围栏代码）。前端新增 /risk/orders 页与工单详情抽屉，F18 风险事件页"处置"改为打开工单抽屉。

```
Python cep_engine ──同事务──> mon.risk_event + mon.risk_work_order(PENDING，deadline=事件时间+SLA)
                                        │
Java module-risk  RiskOrderService ─────┤ 认领/分派/转派/闭环/重开
              ├─ mon.risk_work_order    │ 每动作写 mon.risk_order_log
              ├─ mon.risk_order_log     │ 闭环回写 risk_event.handle_status
              └─ mon.risk_order_sla     │ @Scheduled 60s：overdue / escalated 物化扫描
```

设计约束：
- 复用 F18 模块 module-risk 与权限/审计框架（@AuditLog module="RISK"、MybatisMetaObjectHandler、雪花/小号 id 约定）。
- 跨 schema 只读：分派候选人直接 SQL 查 traj.sys_user / sys_user_role / sys_role_menu（不依赖 module-iam 代码）。
- 本期不接数据范围裁剪（与 F18/监控页同口径）。
- 不引入新中间件；超时扫描用 Spring `@Scheduled`（platform-boot 已开调度能力，若未启用则加 @EnableScheduling 于模块 config）。

## 2. 数据库设计（新文件 infra/postgres/init/08-work-order.sql）

### 2.1 mon.risk_work_order 处置工单

```sql
CREATE SEQUENCE IF NOT EXISTS mon.seq_risk_order_no;

CREATE TABLE IF NOT EXISTS mon.risk_work_order (
    id             bigserial    PRIMARY KEY,
    order_no       varchar(24)  NOT NULL,                       -- WO+yyyyMMdd+6 位流水
    event_id       bigint       NOT NULL,
    -- 事件快照（列表免 join；详情仍取 risk_event 全量）
    event_title    varchar(100),
    event_code     varchar(40),
    event_source   varchar(16),
    plate_no       varchar(40),
    identity_code  varchar(100),
    risk_level     smallint     NOT NULL DEFAULT 2,
    event_time     timestamp,
    -- 状态机
    status         varchar(16)  NOT NULL DEFAULT 'PENDING',    -- PENDING / PROCESSING / CLOSED
    assignee_id    bigint,                                      -- 当前负责人（雪花 id，存 bigint）
    assignee_name  varchar(64),
    assign_time    timestamp,                                   -- 最近一次分派时间
    claim_time     timestamp,                                   -- 认领/接单时间
    close_time     timestamp,
    close_result   varchar(20),                                 -- PHONE_REMIND/EDUCATION/SUSPEND/FALSE_ALARM/OTHER
    close_remark   varchar(255),
    -- SLA
    deadline       timestamp,                                   -- event_time + limit_min
    sla_limit_min  integer,
    grace_min      integer,
    overdue        smallint     NOT NULL DEFAULT 0,
    escalated      smallint     NOT NULL DEFAULT 0,
    escalate_time  timestamp,
    reopen_count   smallint     NOT NULL DEFAULT 0,
    valid_mark     smallint     NOT NULL DEFAULT 1,
    creator        varchar(64),
    create_date    timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater        varchar(64),
    update_date    timestamp,
    CONSTRAINT uk_risk_order_event UNIQUE (event_id)
);
COMMENT ON TABLE mon.risk_work_order IS 'F20 处置工单（与 risk_event 1:1）';

CREATE UNIQUE INDEX IF NOT EXISTS uk_risk_order_no ON mon.risk_work_order (order_no);
CREATE INDEX IF NOT EXISTS idx_risk_order_status   ON mon.risk_work_order (status, valid_mark);
CREATE INDEX IF NOT EXISTS idx_risk_order_assignee ON mon.risk_work_order (assignee_id);
CREATE INDEX IF NOT EXISTS idx_risk_order_deadline ON mon.risk_work_order (deadline);
CREATE INDEX IF NOT EXISTS idx_risk_order_escal   ON mon.risk_work_order (escalated, overdue);
```

order_no 生成：不依赖默认值（Python 批量建单也要用），统一由 SQL 函数：

```sql
CREATE OR REPLACE FUNCTION mon.fmt_order_no() RETURNS text AS $$
  SELECT 'WO' || to_char(CURRENT_TIMESTAMP,'YYYYMMDD') ||
         lpad((nextval('mon.seq_risk_order_no') % 1000000)::text, 6, '0');
$$ LANGUAGE sql;
```

（流水全局递增、仅展示用，不追求按日归零。）

### 2.2 mon.risk_order_log 流转日志

```sql
CREATE TABLE IF NOT EXISTS mon.risk_order_log (
    id           bigserial   PRIMARY KEY,
    order_id     bigint      NOT NULL,
    action       varchar(16) NOT NULL,   -- CREATE/ASSIGN/CLAIM/TRANSFER/ACCEPT/CLOSE/REOPEN/ESCALATE
    from_status  varchar(16),
    to_status    varchar(16),
    from_user_id bigint,
    to_user_id   bigint,
    from_user_name varchar(64),
    to_user_name   varchar(64),
    remark       varchar(255),
    operator_id  bigint,
    operator_name varchar(64),
    create_date  timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_risk_order_log_order ON mon.risk_order_log (order_id, id);
```

ESCALATE 由扫描任务写入（operator_name='SYSTEM'）。

### 2.3 mon.risk_order_sla 时限配置（3 行种子）

```sql
CREATE TABLE IF NOT EXISTS mon.risk_order_sla (
    risk_level   smallint PRIMARY KEY,   -- 1低 2中 3高
    limit_min    integer NOT NULL,       -- 处置时限 1~10080
    grace_min    integer NOT NULL,       -- 升级宽限 0~1440
    update_date  timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO mon.risk_order_sla (risk_level, limit_min, grace_min) VALUES
  (3, 15, 10), (2, 60, 30), (1, 240, 60)
ON CONFLICT (risk_level) DO NOTHING;
```

修改只影响新单；工单上快照 sla_limit_min/grace_min。

### 2.4 存量数据迁移（幂等）

```sql
-- 存量未处置事件补单（含 CREATE 日志）
INSERT INTO mon.risk_work_order
  (order_no, event_id, event_title, event_code, event_source, plate_no, identity_code,
   risk_level, event_time, status, deadline, sla_limit_min, grace_min, creator, create_date)
SELECT mon.fmt_order_no(), e.id, e.title, e.event_code, e.event_source, e.plate_no, e.identity_code,
       e.risk_level, COALESCE(e.event_time, e.create_date), 'PENDING',
       COALESCE(e.event_time, e.create_date) + s.limit_min * interval '1 minute',
       s.limit_min, s.grace_min, 'system', CURRENT_TIMESTAMP
FROM mon.risk_event e
JOIN mon.risk_order_sla s ON s.risk_level = e.risk_level
WHERE e.handle_status = 0
ON CONFLICT (event_id) DO NOTHING;

INSERT INTO mon.risk_order_log (order_id, action, to_status, operator_name, remark)
SELECT w.id, 'CREATE', 'PENDING', 'SYSTEM', '存量未处置事件迁移补单'
FROM mon.risk_work_order w
WHERE NOT EXISTS (SELECT 1 FROM mon.risk_order_log l WHERE l.order_id = w.id AND l.action='CREATE');
```

已处置（handle_status=1）历史事件不补单。

### 2.5 菜单/权限（显式 id）

```sql
INSERT INTO traj.sys_menu (id, parent_id, menu_name, menu_type, perm_code, path, icon, sort_no, visible, status) VALUES
  (14,  10, '处置工单', 2, 'risk:order:view',   '/risk/orders', 'Tickets', 14, 1, 1),
  (915, 14, '工单处置', 3, 'risk:order:handle', NULL, NULL, 1, 1, 1),
  (916, 14, '工单分派/督办', 3, 'risk:order:assign', NULL, NULL, 2, 1, 1)
ON CONFLICT (id) DO NOTHING;
SELECT setval(pg_get_serial_sequence('traj.sys_menu','id'), (SELECT MAX(id) FROM traj.sys_menu), true);
```

授权：role 1 全量（既有 SELECT 模式幂等补）；role 2 SAFE_ADMIN → 14/915/916；role 3 DISPATCHER → 14/915；role 4 FLEET_CAPTAIN → 14/915。

## 3. Python 侧改动（processing-service/app/db.py）

`insert_risk_events(rows)` 改为单事务两步（execute_values + RETURNING 不可与 execute_values 直接组合，分两条 SQL 同一 conn）：

1. 先 `SELECT risk_level, limit_min, grace_min FROM mon.risk_order_sla`（3 行，dict 缓存于模块级，TTL 60s）；
2. INSERT 事件 `... VALUES %s RETURNING id, risk_level, COALESCE(event_time, CURRENT_TIMESTAMP)`；
3. 对返回行批量 `INSERT INTO mon.risk_work_order (...) VALUES %s`，order_no 在 SQL 内调 `mon.fmt_order_no()`（VALUES 中写 `mon.fmt_order_no()` 函数，每行执行一次），deadline 用 Python 按 `event_time + timedelta(minutes=limit_min)` 计算（时间参数统一 `::timestamp`，F18 教训）；
4. 批量写 CREATE 日志（execute_values）。

整个函数一个事务：工单失败则事件一并回滚，由 cep_engine 外层 try/except 记录错误（与现状一致：引擎旁路不阻断轨迹入库；CEP 评估本身失败时事件根本未生成，不存在"有事件无工单"）。SLA 行缺失时兜底 limit 60/grace 30。

## 4. Java 侧（module-risk 新增包内类）

### 4.1 实体

- `RiskWorkOrder`（@TableName("mon.risk_work_order")）：字段与表一一；id IdType.AUTO；assigneeId Long（雪花 id，前端字符串展示由 ToStringSerializer？——工单行内列表接口 assigneeId 不回显给前端做数值运算，VO 中统一转 String）；审计四字段同 RiskRule 写法。
- `RiskOrderLog`、`RiskOrderSla`（简单映射）。

### 4.2 Mapper

- `RiskWorkOrderMapper extends BaseMapper` + 自定义：
  - `pageOrders`：MP QueryWrapper 动态条件（keyword 模糊 order_no/plate_no/event_title、status、riskLevel、closeResult、assigneeId、事件时间区间；overdue/escalated 标记位；排序 `escalated DESC, (CASE status WHEN 'PENDING' THEN 0 WHEN 'PROCESSING' THEN 1 ELSE 2 END), (deadline IS NOT NULL AND deadline<now) DESC, risk_level DESC, event_time`）。
  - `int markOverdue(now)` / `int markEscalated(now)` 两条批量 UPDATE（只更新 status<>'CLOSED' 且未标记的行；escalated 条件 now > deadline + grace interval；参数 ::timestamp 通过 MyBatis 传 LocalDateTime 无 psycopg2 问题）。
  - `selectAssignableUsers()`：@Select 跨 schema：
    ```sql
    SELECT DISTINCT u.id, u.real_name AS name, u.username
    FROM traj.sys_user u
    JOIN traj.sys_user_role ur ON ur.user_id = u.id AND ur.valid_mark = 1
    JOIN traj.sys_role r ON r.id = ur.role_id AND r.status = 1 AND r.valid_mark = 1
    WHERE u.valid_mark = 1 AND u.status = 1
      AND (r.id = 1 OR EXISTS (
        SELECT 1 FROM traj.sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = 915))
    ORDER BY u.real_name
    ```
    （需核对 sys_user_role 实际列名：F33 表为 user_id/role_id/valid_mark，落地时以 06-iam-tables.sql 为准。）
  - 统计：`selectStats`（待处理、处理中、超时未闭环、已升级、今日闭环、平均闭环秒数 AVG(EXTRACT(EPOCH FROM close_time - create_date))）。
- `RiskOrderLogMapper extends BaseMapper`（按 order_id, id 查询）。
- `RiskOrderSlaMapper extends BaseMapper`。

### 4.3 Service：RiskOrderService（状态机核心）

统一方法签名 + 私有 `log(order, action, from, to, fromUser, toUser, remark)`。

| 方法 | 规则 |
|---|---|
| `page(q)` | 先调 `recomputeFlags()`（见 4.4）再分页；VO 附加实时字段 overdueNow/remainingSec/overdueSec（Java 端按 deadline 计算，避免列表与物化标记短暂不一致） |
| `detail(id)` | 工单 + 事件全量（RiskEvent mapper 按 event_id 查）+ 日志时间线（按 id 升序） |
| `assign(id, userId, remark)` | risk:order:assign；目标用户必须在 selectAssignableUsers 内（40001）；status→PROCESSING，assignee/assign_time；日志 ASSIGN |
| `claim(id)` | risk:order:handle；assignee=当前用户、claim_time；PENDING→PROCESSING；已有负责人且非本人 → 40901（请走转派） |
| `transfer(id, userId, remark)` | risk:order:handle；remark 必填 5~255；保持 PROCESSING；日志 TRANSFER（from/to user 均记） |
| `close(id, result, remark)` | risk:order:handle（assignee 本人或有 assign 权限者可关）；result ∈ 5 枚举；remark 10~255（40001）；status=CLOSED、close_time；**同事务回写** mon.risk_event：handle_status=1、handle_remark=结果中文名+':'+remark（截断 255）；日志 CLOSE |
| `reopen(id, remark)` | risk:order:assign；仅 CLOSED 可重开；remark 必填；status=PROCESSING（回到原负责人，无则 PENDING）、close_* 清空、reopen_count+1；事件 handle_status=0；日志 REOPEN |
| `ensureOrder(eventId)` | 事件页"去处理"调用：无工单则按 2.4 同逻辑补建（行锁 `SELECT ... FOR UPDATE` 防并发，唯一约束兜底） |
| `listSla/updateSla(list)` | risk:order:assign；limit 1~10080、grace 0~1440，3 级齐全；审计 |
| `assignableUsers()` | 供下拉 |

所有写方法 @Transactional，@AuditLog(action=CREATE/UPDATE 等，objectId SpEL 取 id，module="RISK")。

### 4.4 超时扫描

`RiskOrderScheduler`（@Component，@Scheduled(fixedDelay=60_000, initialDelay=15_000)）：
1. markOverdue：`UPDATE ... SET overdue=1 WHERE status<>'CLOSED' AND overdue=0 AND deadline < now()`；
2. markEscalated：`... SET escalated=1, escalate_time=now() WHERE status<>'CLOSED' AND escalated=0 AND deadline + grace_min*interval < now()`，并对新升级行批量补 ESCALATE 日志（INSERT ... SELECT，operator_name='SYSTEM'）。

平台启动类确认 @EnableScheduling；若已有则只加组件。

### 4.5 Controller：RiskOrderController `/api/risk/orders`

类级 @PreAuthorize 风格沿用 F18 的权限注解（检查 module-risk 现有写法，可能是自定义 @RequirePerm）。

| 方法 | 路径 | 权限 |
|---|---|---|
| GET | /api/risk/orders（分页+筛选） | risk:order:view |
| GET | /api/risk/orders/{id} | risk:order:view |
| GET | /api/risk/orders/stats | risk:order:view |
| GET | /api/risk/orders/assignable-users | risk:order:view |
| POST | /api/risk/orders/ensure/{eventId} | risk:order:handle |
| POST | /api/risk/orders/{id}/claim | risk:order:handle |
| POST | /api/risk/orders/{id}/assign | risk:order:assign |
| POST | /api/risk/orders/{id}/transfer | risk:order:handle |
| POST | /api/risk/orders/{id}/close | risk:order:handle |
| POST | /api/risk/orders/{id}/reopen | risk:order:assign |
| GET/PUT | /api/risk/order-sla | risk:order:assign |

module-monitor 不改动（/api/monitor/risks 仍可只读）；事件处置旧接口 POST /api/monitor/risks/{id}/handle 保留但内部改为"ensureOrder + 返回工单 id"（前端切走后可视为兼容层；F18 前端会同步改，旧接口直接复用 ensureOrder 逻辑并返回 orderId，不再直接置 handle_status，闭环统一走工单）。

## 5. 前端设计

### 5.1 api/risk.ts 增补

`getOrders(params)/getOrder(id)/getOrderStats/getAssignableUsers/ensureOrder(eventId)/claimOrder/assignOrder/transferOrder/closeOrder/reopenOrder/getSla/updateSla`。

### 5.2 新增 views/risk/RiskOrders.vue（/risk/orders，菜单 14）

- 统计条 6 卡（30s 轮询；F14 上线后改推送）；
- 筛选：keyword、状态、等级、结果、超时档位（all/due/overdue/escalated）、负责人（assignable-users）、时间范围；
- 表格：order_no + 事件名称/事件码副标题、车牌、等级、来源、事件时间、状态 tag、负责人、剩余时限（计算着色：正常蓝/临期橙/超时红负数/升级加图标）、操作；
- 排序由后端保证；
- 操作按钮按权限 v-perm 与状态显隐。

### 5.3 工单详情抽屉 components/OrderDetailDrawer.vue（720px，两处复用：工单列表、事件页）

- 事件快照卡（含经纬度、media 链接，小地图用 Leaflet 单点即可，复用 GeoFenceList 瓦片配置）；
- 处置卡 + el-timeline（日志 action 中文化、操作人、备注、时间）；
- 操作表单：分派（el-select 用户+备注）、转派（同上+原因必填）、闭环（el-radio-group 5 结果 + textarea 10~255 字计数校验）；
- 重开（仅 risk:order:assign，危险确认）；
- 预留"干预记录"空区块（F21）。

### 5.4 RiskEvents.vue 改造

- 未处置按钮文案"去处理"→ `ensureOrder(eventId)` → 打开 OrderDetailDrawer；
- 已处置列展示 close_result 中文名 tag + title 悬浮 close_remark（无工单的老数据仍显示 handle_remark）；
- 去掉原内联处置对话框与 handleRisk 调用（兼容接口保留）。

### 5.5 SLA 配置弹窗（RiskOrders 页头部，v-perm risk:order:assign）

三行等级 × 时限/宽限 InputNumber，保存提示"仅对新工单生效"。

## 6. 端到端时序

1. CEP 落事件 → 同事务工单 PENDING（deadline=事件时间+15/60/240 分）→ CREATE 日志。
2. 坐席打开工单页：默认排序升级/超时在前 → 认领（PROCESSING，日志 CLAIM）→ 电话干预 → 闭环 PHONE_REMIND + 说明 → 事件 handle_status=1 → 统计条今日闭环 +1、平均时长更新。
3. 主管在分派视图把单分派给某坐席（ASSIGN 日志）→ 坐席转派他人（TRANSFER 留痕）→ 闭环。
4. 无人处理：deadline 到 → overdue=1（列表红色）→ +宽限 → escalated=1 + ESCALATE 日志 → 主管督办队列置顶。

## 7. 任务分解

| 任务 | 内容 |
|---|---|
| T1 | 08-work-order.sql 编写并执行（表/函数/迁移/菜单/授权），核对存量补单数量 |
| T2 | Python db.insert_risk_events 同事务建单 + CREATE 日志；模拟器验证新事件自动有单 |
| T3 | Java 实体/mapper/service/controller/scheduler；mvn compile |
| T4 | 前端 api + RiskOrders + OrderDetailDrawer + RiskEvents 改造 + SLA 弹窗 + 路由；npm build |
| T5 | 重建 processing-app/platform-app（必要时重启 portal-nginx，F18 教训） |
| T6 | 接口自测（§8.1） |
| T7 | E2E + 浏览器（§8.2） |
| T8 | MOD 实施记录回填、项目记忆更新 |

## 8. 自测计划

### 8.1 接口层（curl.exe + admin / disp01 / SAFE_ADMIN）

1. 迁移：未处置事件全部有工单（count 对账），已处置无工单；重复执行 SQL 不重复建单。
2. ensureOrder：对有单事件调用幂等；对无单未处置事件补建成功。
3. 状态机：claim 正常；已 claim 单他人再 claim → 40901；close 缺结果/说明 <10 字 → 40001；close 后再 close → 40901；reopen 后 close_* 清空、事件回到未处置、reopen_count=1。
4. 分派：assign 给停用用户/不存在 id → 40001；transfer 无原因 → 40001；日志 from/to 正确。
5. SLA：越界值 40001；修改后新建工单 deadline 按新值，旧单不变。
6. 权限：DISPATCHER 可 claim/close、PUT /order-sla → 40301；无权限角色 GET /orders → 40301。
7. stats 与库内 count/均值一致；assignable-users 只含有效且持 915 的用户。
8. 审计：ASSIGN/CLOSE/REOPEN/SLA 修改在 sys_audit_log 留痕。

### 8.2 E2E（模拟器 + 构造数据 + 浏览器）

1. 启动模拟器产生新事件 → 1 秒内工单列表出现 PENDING 单，deadline 正确，时间线有 CREATE。
2. 浏览器坐席：认领→闭环（电话提醒+说明）→ 事件页同步"已处置/电话提醒"；统计变化。
3. 主管分派 → 另一坐席转派 → 闭环，时间线 4 条记录齐全。
4. 构造 deadline=now-20min（高风险宽限 10）：扫描后 overdue=1；构造 deadline=now-30min：escalated=1 且有 ESCALATE 日志、督办置顶。
5. SLA 改为 1 分钟 → 新单 60 秒后变红（配合扫描周期）；改回 15/60/240。
6. 浏览器：admin/SAFE_ADMIN/DISPATCHER 菜单与按钮显隐正确；无权限用户直访 /risk/orders 被守卫弹回。
7. 验证后清理：恢复 SLA、删除测试工单（及对应测试事件）、停模拟器。

## 9. 风险与注意事项

1. **同事务回滚语义**：工单建不出来意味着该事件也不落库（引擎旁路记录错误，轨迹入库不受影响）；保证 1:1 强一致优先于"事件优先"。
2. **assignee_id 雪花 id**：库内 bigint，Java Long，出前端 VO 转 String（F33 教训）；但下拉 value 用字符串、提交回字符串，后端转 Long。
3. **overdue/escalated 双口径**：列表实时算（remainingSec）与物化标记可能差一个扫描周期，UI 以实时值着色、以 escalated 标记做"升级"身份与筛选；不允许出现 escalated=1 但实时未超时的展示矛盾（escalated 必然 overdue）。
4. **@Scheduled 单实例**：演示单机部署无并发问题；多实例部署需加锁（ShedLock），本期不做。
5. **时间参数**：Python 侧继续统一 `::timestamp`；Java 侧 LocalDateTime 直传。
6. **order_no 字符集与长度**：WO+8 位日期+6 位流水=16 字符，varchar(24) 充足；sequence 不复用、不按日归零。
7. **回写 handle_remark 截断**：中文说明拼前缀后按字符安全截断 255，避免超长报错回滚闭环。

## 10. 实施记录（2026-10-02）

### 10.1 产出物
- SQL：`infra/postgres/init/08-work-order.sql`（表/函数/迁移/菜单/授权，已执行）
- Python：`processing-service/app/db.py`（同事务建单 + CREATE 日志 + SLA 60s 缓存）
- Java：`module-risk` 下 entity/mapper/service/scheduler/controller 全套（mvn package 通过）
- 前端：`api/risk.ts`、`views/risk/RiskOrders.vue`、`views/risk/OrderDetailDrawer.vue`、`views/risk/RiskEvents.vue` 改造、`router/index.ts` 路由（npm build 通过）
- 部署：postgres/platform-app/processing-app/portal-nginx 已重建重启

### 10.2 自测结果
- 迁移：10001 条未处置事件全部补建 PENDING 工单（count 对账通过）
- 状态机：claim 200 / 重复 claim 40901 / close 短说明 40001 / close 成功回写 event handle_status=1 / 重复 close 40901 / reopen 短原因 40001 / reopen 成功状态回 PROCESSING、reopen_count=1、事件 handle_status=0
- 分派/转派：assign 给 SAFE_ADMIN 用户成功（日志 from 超级管理员→to 安全主管测试）；transfer 回 admin 成功（日志 from/to 正确）
- SLA：越界 limitMin=99999 → 40001
- 自动建单：Python 信号事件触发，同事务生成 PENDING 工单，deadline 按风险等级 SLA 计算正确，CREATE 日志 operator=CEP
- 权限/菜单：admin 可见"处置工单"菜单，统计卡+列表+详情抽屉浏览器验证通过

### 10.3 避坑
- **PostgreSQL FILTER 子句位置**：`EXTRACT(EPOCH FROM AVG(x)) FILTER (WHERE ...)` 语法错误，FILTER 必须紧跟聚合函数，正确写法 `EXTRACT(EPOCH FROM AVG(x) FILTER (WHERE ...))`
- 重建 platform-app 后必须 `docker restart portal-nginx`，否则 502（F18 已知，本次已遵守）
- PowerShell 复杂 JSON body 用 `Invoke-RestMethod` + `ConvertTo-Json -Compress`，避免 curl.exe 转义陷阱
