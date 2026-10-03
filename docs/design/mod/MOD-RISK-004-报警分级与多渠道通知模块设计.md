# MOD-RISK-004 报警分级与多渠道通知（F19）模块设计

> 对应 PRD：docs/html/PRD-RISK-004-报警分级与多渠道通知产品设计.html
> 上游：F18/F20/F14/F35　版本 v1.0　2026-10-03

## 1. 方案总览

新建 `module-notify`（`com.mydbd.notify`）承载通知派发与站内消息。业务侧（module-risk 工单状态机）在状态变更后调用 `NotifyService.dispatch(...)`，服务内：解析接收人 → 按级别策略选渠道 → 站内消息落库（`traj.sys_message`）→ 通过 `RealtimePusher` 接口定向 WebSocket 推送。外部渠道（短信/语音/App 推送）以 `NotifyChannel` 接口 + 配置开关预留，未启用时仅记日志。

依赖方向（无环）：
- `module-risk → module-notify`（业务触发通知）
- `module-monitor → module-notify`（SessionRegistry 实现 RealtimePusher）
- `module-notify → module-system`（读 ConfigService 参数）、`→ platform-common`

## 2. 数据库设计（11-notify.sql，schema `traj`）

### 2.1 扩展 sys_message（F35 预留表补列）

```sql
ALTER TABLE traj.sys_message ADD COLUMN IF NOT EXISTS biz_type   varchar(32);   -- WORK_ORDER / SYSTEM
ALTER TABLE traj.sys_message ADD COLUMN IF NOT EXISTS biz_id     bigint;        -- 关联工单 id
ALTER TABLE traj.sys_message ADD COLUMN IF NOT EXISTS level      smallint NOT NULL DEFAULT 1;  -- 1低 2中 3高
ALTER TABLE traj.sys_message ADD COLUMN IF NOT EXISTS event_type varchar(32);   -- ORDER_CREATE/ORDER_ASSIGN/ORDER_ESCALATE/ORDER_CLOSE
CREATE INDEX IF NOT EXISTS idx_message_user_read ON traj.sys_message(user_id, is_read, id DESC);
```

现有列：id, user_id, title, content, msg_type, is_read, creator, create_date。msg_type 与 event_type 语义重复，统一用 **event_type** 作业务类型，msg_type 保留但写 INBOX。

### 2.2 通知发送日志 notify_send_log

```sql
CREATE TABLE IF NOT EXISTS traj.notify_send_log (
  id bigserial PRIMARY KEY,
  event_type varchar(32), biz_id bigint, channel varchar(20),   -- INBOX/WEBSOCKET/SMS/VOICE/PUSH
  receiver_id bigint, title varchar(255), level smallint,
  status varchar(16),      -- SENT / SKIPPED / FAILED
  detail varchar(500),
  create_date timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_notify_log_biz ON traj.notify_send_log(biz_id, event_type);
```

用途：外部渠道"发送意图"留痕（SKIPPED+原因）；WS 推送结果（SENT/SKIPPED 不在线）。站内消息本身即 sys_message，不重复记 INBOX 日志（只记 WEBSOCKET 与外部渠道）。

### 2.3 系统参数种子（sys_config）

| config_key | 值 | 类型 | 说明 |
|---|---|---|---|
| notify.popup.min_level | 3 | INT | 弹窗最低级别（≥该值弹卡片+响铃） |
| notify.sound.enabled | true | BOOL | 提示音开关 |
| notify.sms.enabled | false | BOOL | 短信渠道开关（预留） |
| notify.voice.enabled | false | BOOL | 语音外呼开关（预留） |
| notify.push.enabled | false | BOOL | App 推送开关（预留） |

### 2.4 菜单

- 924 消息中心 `/system/messages`，perm `system:message:view`，menu_type=2，sort 920，visible=1；授权 role 1/2/3/4（全员）。
- 925 通知配置查看按钮权限 `notify:config:view`（挂 921 系统参数下，仅 admin/SAFE_ADMIN）。本期配置读接口复用 /api/system/config，925 暂不单独建，避免冗余。

## 3. 后端设计（module-notify）

### 3.1 包结构

```
com.mydbd.notify
├── entity/NotifyMessage.java          @TableName("traj.sys_message")
├── entity/NotifySendLog.java          @TableName("traj.notify_send_log")
├── mapper/NotifyMessageMapper.java    extends BaseMapper + 自定义 SQL
├── mapper/NotifySendLogMapper.java
├── api/NotifyEvent.java               record(eventType, bizId, bizType, title, content, level, plateNo, eventTitle)
├── api/NotifyService.java             dispatch(NotifyEvent, receivers) / dispatchRoles(...)
├── channel/NotifyChannel.java         接口：name() + send(NotifyMessage)
├── channel/SmsChannel.java            预留：读 notify.sms.enabled，未启用记 SKIPPED 日志
├── channel/VoiceChannel.java          预留
├── channel/PushChannel.java           预留
├── RealtimePusher.java                接口：sendToUsers(Collection<Long> uids, String type, Object data)；默认 no-op @ConditionalOnMissingBean
├── NotifyProperties.java              读 ConfigService（getInt/getBool），带 30s 缓存
└── controller/NotifyController.java   /api/notify/**
```

### 3.2 核心流程 NotifyService.dispatch

```java
public void dispatch(NotifyEvent event, List<Long> receiverIds) {
    // 1. 去重、空接收人直接返回
    // 2. 对每个接收人：insert sys_message（is_read=0）
    // 3. 组装 WS payload {id,title,content,level,eventType,bizId,plateNo,eventTime}
    //    pusher.sendToUsers(onlineReceivers, "NOTIFY", payload)
    // 4. 遍历外部渠道 List<NotifyChannel>，各自按开关 SKIPPED/SENT 记 notify_send_log
    // 全程 try/catch，失败仅 warn 日志，不抛出（不影响主事务）
}
```

接收人解析（`ReceiverResolver`，直接 SQL 查 traj 库）：
- 坐席 = 持 menu 915（risk:order:handle）的启用用户 + role1 成员；
- 主管 = 持 menu 916（risk:order:assign）的启用用户 + role1 成员；
- 复用 RiskWorkOrderMapper.selectAssignableUsers 同款 SQL，放 notify 自己的 mapper（`selectUserIdsByMenu(menuId)`）。

### 3.3 触发点（module-risk）

| 方法 | event_type | 接收人 | level |
|---|---|---|---|
| `assign()` / `transfer()` | ORDER_ASSIGN | 目标坐席 userId | 工单 riskLevel |
| `claim()` | —（不发，本人操作已知晓） | | |
| `close()` | ORDER_CLOSE | 分派人（若与操作人不同） | 1 |
| `runTimeoutScan()` 内对本轮新升级工单 | ORDER_ESCALATE | 负责人 + 主管 | 强制 3 |

- 升级去重：`markEscalated()` 返回受影响行数后，用 `selectEscalatedRecent()`（escalate_time ≥ now-2min 且 escalated=1）取工单列表逐个 dispatch；sys_message 侧按 (user_id, biz_id, event_type) 不重复——由 `escalated` 标记本身保证只触发一次（markEscalated 只更新 escalated=0 的行）。
- 注入方式：`RiskOrderService` 构造注入 `NotifyService`（module-risk pom 加 module-notify）。

### 3.4 WebSocket 定向推送（module-monitor 改造）

- `SessionRegistry` 增加 `Map<Long, Set<String>> uidSessions`；`add()` 时从 `session.getAttributes().get("uid")` 取 uid 登记，`remove()`/发送失败时清理。
- 实现 `RealtimePusher.sendToUsers(uids, type, data)`：仅向在线会话发送，序列化一次复用 TextMessage。
- 既有 broadcast 行为不变（POINTS/RISK/ALARM 仍广播）。

### 3.5 接口（/api/notify，均需登录）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/notify/messages?page=&size=&unreadOnly=` | 当前用户消息分页（user_id 强制取 UserContext，不可指定他人） |
| GET | `/api/notify/unread-count` | 未读数（int） |
| POST | `/api/notify/{id}/read` | 标记已读（校验归属） |
| POST | `/api/notify/read-all` | 全部已读 |
| DELETE | `/api/notify/{id}` | 删除自己的消息（可选，本期实现） |

返回统一 `Result`；id 为雪花 bigserial（当前库为自增小号，仍按项目约定加 `@JsonSerialize(ToStringSerializer)` 防溢出）。

## 4. 前端设计

### 4.1 api/notify.ts
`listMessages(params)`、`unreadCount()`、`markRead(id)`、`readAll()`、类型 `NotifyMessage`。

### 4.2 composables/useNotify.ts（全局，MainLayout 挂载）
- 复用 F14 的 WS 连接思路：独立建立 `/ws/realtime?token=` 连接，仅处理 `type === 'NOTIFY'` 消息（忽略 POINTS/RISK/ALARM，避免与 Monitor 页重复消费）。
- 收到消息：`unread++`；level ≥ notify.popup.min_level（前端常量 3，或从 /api/system/config 读）→ 右上角弹出通知卡片（ElNotification，danger 类型，duration=0 需手动关闭）+ 提示音（new Audio('/notify.mp3')，失败静默）；level==2 → 浏览器 Notification（已授权时）。
- 卡片点击"去处理"→ `router.push('/risk/orders')` 并携带 `openOrder=bizId` query；RiskOrders.vue 读取该 query 自动打开详情抽屉。
- 暴露 `unread`、`latest`（最近 10 条，供铃铛下拉）、`refresh()`。

### 4.3 MainLayout.vue
- 顶栏标题右侧、用户下拉左侧加铃铛：`el-badge :value="unread" :max="99"` + `el-popover` 下拉展示 latest 列表（标题+时间+未读点），底部"消息中心"链接跳 `/system/messages`。
- 点击列表项：markRead + 跳转工单。

### 4.4 views/system/MessageCenter.vue（/system/messages）
- 筛选：全部/未读；表格列：级别标签、标题、内容、时间、状态；行点击已读+跳转；顶部"全部已读"按钮；分页。

### 4.5 router/index.ts
新增 `/system/messages` → MessageCenter.vue，meta.title=消息中心。

## 5. 任务分解

- T1 SQL：11-notify.sql（sys_message 扩展列 + notify_send_log + 参数种子 + 菜单 924 + 角色授权），执行入库。
- T2 module-notify 骨架：pom、entity、mapper、NotifyEvent/NotifyService、ReceiverResolver、channel 三预留类、NotifyProperties。
- T3 RealtimePusher 接口 + SessionRegistry 实现与 uid 登记。
- T4 NotifyController（消息 CRUD）。
- T5 module-risk 触发点接入（assign/transfer/close/escalate scan）+ pom 依赖。
- T6 打包部署：父 pom modules + dependencyManagement、platform-boot pom 依赖、`mvn install -DskipTests` → `mvn -pl platform-boot package`、cp 到 /app/platform-app.jar、重启容器。
- T7 前端：api/notify.ts、useNotify.ts、MainLayout 铃铛、MessageCenter.vue、router、RiskOrders openOrder query、build。
- T8 自测（API + 浏览器）+ 文档回填。

## 6. 风险与注意事项

- **通知不得影响主事务**：dispatch 全程 try/catch 吞异常；不加 @Transactional（sys_message 独立提交）。
- **避免通知风暴**：升级扫描每轮上限 200 条；ORDER_CLOSE 仅在分派人≠操作人时发。
- **WS 单连接多消费者**：Monitor 页 useRealtime 与全局 useNotify 各建一条连接，服务端 sessions 以 sessionId 为键、uid 多值集合，互不影响。
- **雪花/自增 id 前端精度**：sys_message.id 当前 smallserial 量级，仍统一 ToStringSerializer。
- **ConfigService 缓存**：参数改动 30s 内生效即可，不做主动失效。

## 7. 自测计划

1. 分派工单给坐席 B → B 的 /api/notify/unread-count +1，列表出现 ORDER_ASSIGN 消息。
2. 闭环工单（分派人≠操作人）→ 分派人收到 ORDER_CLOSE 低级别消息。
3. 构造 deadline 已过 + 宽限已过的工单，等扫描 → 负责人与主管收到 ORDER_ESCALATE（level=3），重复扫描不重复发。
4. 浏览器：坐席登录后分派给自己所在账号 → 右上角弹窗+角标+提示音；点击去处理直达工单详情。
5. 消息中心：未读筛选、单条已读、全部已读、角标同步归零。
6. 越权：用户 A 用 B 的消息 id 调 /read → 40401/40301。
7. 外部渠道开关 false → notify_send_log 记 SKIPPED，无异常。
8. WS 断开时通知：sys_message 仍落库，send_log 记 SKIPPED（不在线），主流程 200。

## 8. 实施记录（2026-10-03，T1~T8 全部完成）

### 8.1 交付物
- SQL：`infra/postgres/init/11-notify.sql`（sys_message 扩展列 biz_type/biz_id/level/event_type/read_date + 2 索引、notify_send_log、5 个 notify.* 参数、notify_event_type 字典、菜单 924 消息中心授权 role 1/2/3/4），已执行入库。
- 后端新模块 `module-notify`（com.mydbd.notify）：entity/mapper/api/channel/service/controller/config 全套；NotifyService.dispatch 落库 sys_message → RealtimePusher 定向 WS → 外部渠道 SKIPPED/SENT 日志；NotifyController /api/notify/**（messages/latest/unread-count/settings/read/read-all/delete）。
- `module-monitor` SessionRegistry 实现 RealtimePusher（uidSessions 定向推送，send 失败清理索引）。
- `module-risk` 四触发点接入：assign/transfer→ORDER_ASSIGN、close(分派人≠操作人)→ORDER_CLOSE、runTimeoutScan 新升级→ORDER_ESCALATE（先 selectNewlyEscalated 再补日志去重）、RiskOrderScheduler id 游标扫描新工单→ORDER_CREATE（重启初始化 max id 不回放）。
- 前端：`api/notify.ts`、`composables/useNotify.ts`（独立 WS 仅消费 NOTIFY，Web Audio 蜂鸣 + ElNotification 弹窗 + 浏览器 Notification）、`MainLayout.vue` 铃铛角标+下拉面板、`views/system/MessageCenter.vue`、router `/system/messages`、RiskOrders.vue openOrder query（onMounted + watch 双触发）。

### 8.2 自测结果（全过）
- API：assign→ORDER_ASSIGN 落库+未读+1；admin 自闭环不发、admin 闭环 disp01 工单→disp01 收 ORDER_CLOSE(level=1)；deadline 改超时+宽限→扫描发 ORDER_ESCALATE(level=3) 给负责人+主管；SQL 补建工单→调度器 60s 内发 ORDER_CREATE 给全体坐席；markRead/readAll/unread/latest/delete 正常；非本人 id /read→40401；外部渠道 SMS/VOICE/PUSH 记 SKIPPED、WS 不在线记 SKIPPED。
- 浏览器：铃铛角标、下拉面板（全部已读/暂无消息/消息中心链接）、消息中心表格列齐全、/risk/orders?openOrder=2394 自动开抽屉（含 WO20261002002393/苏B00417）全 PASS，无阻断性 JS 报错。
- 测试数据已清理（sys_message/notify_send_log 归零，临时启用并绑角色的 disp01 已还原 valid_mark=0，测试工单 10012 已删）。

### 8.3 偏差与说明
- 弹窗提示音不引入音频资源，改用 Web Audio API 合成 880Hz 短蜂鸣（无 public 目录，避免静态依赖）。
- notify:config:view（925）本期不单独建，配置读复用 /api/system/config，与 §2.4 设计一致。
- WS NOTIFY 的 data 为单对象（非数组），前端按对象解析；与 F14 broadcast 的数组 data 不同，两条连接互不影响。
