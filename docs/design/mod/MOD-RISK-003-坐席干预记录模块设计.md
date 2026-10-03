# MOD-RISK-003 坐席干预记录（F21）模块设计

> 对应 PRD：docs/html/PRD-RISK-003-坐席干预记录产品设计.html
> 上游：F20 处置工单流转　版本 v1.0　2026-10-02

## 1. 方案总览

在 `module-risk` 内新增干预动作记录模型 `mon.risk_intervention`，挂在工单下。坐席可在工单详情追加干预记录；工单闭环（非误报）时自动补记一条系统干预。提供"感知→预警→干预→闭环"漏斗统计接口。

## 2. 数据库设计（10-risk-intervention.sql，schema `mon`）

### mon.risk_intervention
| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigserial PK | |
| order_id | bigint NOT NULL | 关联工单 |
| event_id | bigint | 冗余事件 id |
| plate_no | varchar(40) | 冗余车牌 |
| identity_code | varchar(100) | 冗余终端 |
| action_type | varchar(20) | PHONE_REMIND/EDUCATION/STOP/PENALTY/OTHER |
| action_result | varchar(20) | SUCCESS/FAILED/NO_ANSWER/PENDING |
| operator_id | bigint | 坐席 id |
| operator_name | varchar(64) | 坐席名 |
| source | varchar(16) | MANUAL（坐席手动）/SYSTEM（闭环自动） |
| remark | varchar(500) | 说明 |
| create_date | timestamp | |

索引：idx_intervention_order(order_id)、idx_intervention_event(event_id)、idx_intervention_time(create_date)。

## 3. 后端设计（module-risk）

### 3.1 entity/mapper
- `RiskIntervention`（@TableName("mon.risk_intervention")）
- `RiskInterventionMapper`（BaseMapper）

### 3.2 InterventionService
- `listByOrderId(orderId)`：按工单查干预记录，按 create_date 升序。
- `add(RiskIntervention)`：坐席手动新增，source=MANUAL，补 operator 与 create_date。
- `autoFromClose(order)`：闭环时调用，若 close_result != FALSE_ALARM，映射 action_type（PHONE_REMIND→PHONE_REMIND、EDUCATION→EDUCATION、REPORT_PENALTY/TRAFFIC_VIOLATION→PENALTY），插入 source=SYSTEM、action_result=SUCCESS。

### 3.3 RiskOrderService 改造
- `close()` 内调用 `interventionService.autoFromClose(order)`（同事务）。
- 新增 `funnel(startDate, endDate)`：
  - eventTotal：risk_event create_date 在范围内且 valid_mark=1 的数量
  - orderTotal：risk_work_order create_date 范围内 valid_mark=1
  - interventionTotal：有 ≥1 条 intervention 的工单数（distinct order_id）
  - closedTotal：status=CLOSED 的工单数
  - interventionRate = interventionTotal/orderTotal，closeRate = closedTotal/orderTotal（double，保留2位）

### 3.4 Controller（RiskOrderController 扩展）
- `GET /{id}/interventions` → List<RiskIntervention>（perm risk:order:view）
- `POST /interventions` → 新增（perm risk:order:handle，@AuditLog HANDLE）
- `GET /funnel?startDate=&endDate=` → FunnelVO（perm risk:order:view）

## 4. 前端设计

### 4.1 api/risk.ts 扩展
- `getInterventions(orderId)`、`addIntervention(data)`、`getOrderFunnel(start,end)`
- 类型 `Intervention { id, orderId, actionType, actionResult, operatorName, source, remark, createDate }`
- 类型 `OrderFunnel { eventTotal, orderTotal, interventionTotal, closedTotal, interventionRate, closeRate }`

### 4.2 OrderDetailDrawer.vue
- 在流转时间线下方加"干预记录"区块：el-table 列（时间/动作类型/结果/坐席/来源/备注）+ 新增按钮（v-perm risk:order:handle）。
- 新增弹窗：动作类型 select、结果 select、备注 textarea。
- SYSTEM 来源显示"系统"标签。

### 4.3 RiskEvents.vue（风险预警分析页）
- 顶部统计条加漏斗卡片：事件 / 工单 / 干预 / 闭环 + 干预率、闭环率。
- 调用 getOrderFunnel(今日0点, 明日0点)。

## 5. 任务分解

| 任务 | 内容 |
|---|---|
| T1 | 10-risk-intervention.sql，执行 |
| T2 | Intervention entity/mapper/service，RiskOrderService.close 自动干预，funnel 统计 |
| T3 | RiskOrderController 干预接口 + funnel，编译部署 |
| T4 | 前端 api + OrderDetailDrawer 干预区 + RiskEvents 漏斗，build 部署 |
| T5 | 自测（§7） |
| T6 | MOD 实施记录、项目记忆 |

## 6. 风险与注意事项

1. **闭环自动干预幂等**：reopen 再 close 会再生成一条 SYSTEM 干预（合理，每次闭环都留痕）。
2. **干预不可删**：仅追加，不提供 delete 接口；工单逻辑删时干预保留。
3. **漏斗统计性能**：distinct order_id 可接受（单日内工单量小）；未来量大加索引。
4. **动作类型映射**：close_result 与 action_type 非一一对应（两个处罚类都映射 PENALTY），在 InterventionService 内集中维护映射。

## 7. 自测计划

1. 工单详情新增干预：列表显示正确，source=MANUAL。
2. 工单闭环（PHONE_REMIND）：自动生成 PHONE_REMIND 干预，source=SYSTEM，action_result=SUCCESS。
3. 工单闭环（FALSE_ALARM）：不生成干预。
4. funnel 接口：数字与库表 count 一致，比率计算正确。
5. 无 risk:order:handle 权限：新增按钮不显示，POST 返回 40301。
6. 前端：RiskEvents 漏斗卡片渲染，OrderDetailDrawer 干预列表与新增弹窗。

## 8. 实施记录（2026-10-02）

### 8.1 后端
- SQL `infra/postgres/init/10-risk-intervention.sql`：建 `mon.risk_intervention` 表及 3 个索引。
- entity `RiskIntervention`、mapper `RiskInterventionMapper`（含自定义 `countIntervenedOrders`）。
- `InterventionService`：listByOrderId / add(MANUAL) / autoFromClose（非误报闭环自动生成 SYSTEM 干预，映射 PHONE_REMIND→PHONE_REMIND、EDUCATION→EDUCATION、REPORT_PENALTY→PENALTY、TRAFFIC_VIOLATION→PENALTY）/ countIntervenedOrders。
- `RiskOrderService.close()` 末尾调用 `interventionService.autoFromClose(order)`；新增 `funnel(start,end)` 返回 OrderFunnelVO（eventTotal/orderTotal/interventionTotal/closedTotal + interventionRate/closeRate 保留1位）。
- `RiskOrderController`：GET `/{id}/interventions`、POST `/interventions`（risk:order:handle）、GET `/funnel?start=&end=`（ISO_DATE_TIME）。
- **修正**：`RiskOrderService.RESULT_NAMES` 原 SUSPEND/OTHER → 改为 REPORT_PENALTY/TRAFFIC_VIOLATION，与字典 `close_result` 一致；`VALID_RESULTS` 同步。

### 8.2 前端
- `api/risk.ts`：新增 InterventionAction/InterventionResult 类型、RiskIntervention/OrderFunnel 接口、getInterventions/addIntervention/getOrderFunnel；**修正** CloseResult 为 PHONE_REMIND/EDUCATION/REPORT_PENALTY/TRAFFIC_VIOLATION/FALSE_ALARM。
- `views/risk/OrderDetailDrawer.vue`：流转记录后加"干预记录"卡片（时间/动作/结果/坐席/来源/说明），新增干预弹窗（actionType/actionResult/remark），打开抽屉与闭环后刷新列表；闭环结果枚举改为新值。
- `views/risk/RiskEvents.vue`：总览下加"感知→预警→干预→闭环"漏斗卡片，调用 getOrderFunnel（今日0点~明日0点）。
- `views/risk/RiskOrders.vue`：筛选处置结果选项改为新枚举。

### 8.3 自测结果
1. 手动新增干预（order 10011）：返回成功，DB source=MANUAL。 ✅
2. 闭环 order 2971（EDUCATION）：自动生成 SYSTEM 干预 action_type=EDUCATION，列表2条。 ✅
3. funnel 接口：eventTotal=10015/orderTotal=10003/interventionTotal=3/closedTotal=3，比率计算正确。 ✅
4. 前端：RiskEvents 漏斗卡片渲染正常；OrderDetailDrawer 干预列表与新增弹窗正常。 ✅
