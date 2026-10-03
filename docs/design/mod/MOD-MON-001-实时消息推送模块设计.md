# MOD-MON-001 实时消息推送（F14）模块设计

> 上游：F18 CEP（mon.risk_event）、traj.traj_gps_point、traj.traj_warn_info
> 对应 PRD：docs/html/PRD-MON-001-实时消息推送产品设计.html
> 版本 v1.0　2026-10-02

## 1. 方案总览

用 Spring 原生 WebSocket（spring-boot-starter-websocket，非 STOMP）建立单端点 `/ws/realtime`，服务端维护 `SessionRegistry`，由一个 `@Scheduled` 任务每秒向所有在线会话推送 POINTS 全量快照，并扫描最近 2 秒新增的风险事件/终端报警做增量推送。前端 `useRealtime` composable 建立连接，收到消息分发给 Monitor.vue；断连指数退避重连，期间降级回 5s 轮询。

不引入消息中间件（单机演示）；不做用户级定向（所有登录用户接收相同推送，数据范围依赖既有权限在前端/接口层过滤）。

## 2. 后端设计（module-monitor）

### 2.1 依赖

platform-boot/pom.xml 增加：

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>
```

（由 platform-boot 依赖管理，module-monitor 不重复声明）

### 2.2 WebSocket 配置 `RealtimeWebSocketConfig`

- `@Configuration` + `@EnableWebSocket`
- 实现 `WebSocketConfigurer`，注册端点 `/ws/realtime` 到 `RealtimeWebSocketHandler`，挂载 `AuthHandshakeInterceptor`
- 设置 `setAllowedOriginPatterns("*")`（演示环境）

### 2.3 鉴权握手 `AuthHandshakeInterceptor`

`beforeHandshake` 中：
1. 从 `query.get("token")` 取 JWT；
2. 复用 `JwtTokenProvider` 解析校验，失败返回 false（握手 401）；
3. 把解析出的 userId / username 放入 attributes，供 handler 识别会话。

> JwtTokenProvider 已在 common-security 模块提供，直接注入使用。

### 2.4 会话注册 `SessionRegistry`

- `ConcurrentHashMap<String, WebSocketSession>`（key=sessionId，value=session）
- 提供 `add/remove/getAll/broadcast(String)`
- 广播时遍历 session，单个发送异常不影响其他（try/catch 并移除失效 session）
- 消息统一 `ObjectMapper` 序列化为 JSON text

### 2.5 Handler `RealtimeWebSocketHandler`（extends TextWebSocketHandler）

- `afterConnectionEstablished`：注册会话，立即推送一次 POINTS 快照（避免首屏空等）
- `afterConnectionClosed`：移除会话
- `handleTextMessage`：忽略（单向推送，不处理客户端消息；可扩展为 ping 响应）
- `handleTransportError`：移除会话

### 2.6 推送调度 `RealtimePushScheduler`

`@Scheduled(fixedDelay=1000, initialDelay=5000)`：
1. 查 `trajectoryService.listLatestPoints()` → 序列化为 `{type:"POINTS", data:[...]}` 广播
2. 增量风险：查 `risk_event WHERE create_date > now()-2s ORDER BY create_date`（用状态时间，避免时钟漂移用 `lastPushRiskId` 游标更稳；本期用时间窗口）→ `{type:"RISK", data:[...]}`
3. 增量报警：`warn_info WHERE create_date > now()-2s` → `{type:"ALARM", data:[...]}`
4. 整体 try/catch，异常不中断调度器

> 时间窗口 2s 与调度间隔 1s 有 1s 重叠，可接受重复（前端按 id 去重）；不丢消息优先。

### 2.7 消息契约

```json
{ "type": "POINTS", "data": [ {"id":..., "plateNo":"...", "identityCode":"...", "lng":116.4, "lat":39.9, "speed":50, "direction":90, "alarmFlag":0} ] }
{ "type": "RISK",   "data": [ {"id":..., "eventCode":"V_PCW", "plateNo":"...", "riskLevel":3, "eventTime":"...", "lng":116.4, "lat":39.9} ] }
{ "type": "ALARM",  "data": [ {"id":..., "plateNo":"...", "typeId":1, "startWarnTime":"...", "startLng":"116.4", "startLat":"39.9"} ] }
```

## 3. 前端设计

### 3.1 `composables/useRealtime.ts`

```ts
export function useRealtime(onMessage: (msg: WsMessage) => void) {
  const connected = ref(false)
  let ws: WebSocket | null = null
  let retry = 0
  let pollTimer: number | null = null
  let reconnectTimer: number | null = null

  function connect() {
    const token = localStorage.getItem('token')
    const proto = location.protocol === 'https:' ? 'wss' : 'ws'
    ws = new WebSocket(`${proto}://${location.host}/ws/realtime?token=${token}`)
    ws.onopen = () => { connected.value = true; retry = 0; stopPollFallback() }
    ws.onmessage = (e) => { try { onMessage(JSON.parse(e.data)) } catch {} }
    ws.onclose = () => { connected.value = false; startPollFallback(); scheduleReconnect() }
    ws.onerror = () => { ws?.close() }
  }
  function scheduleReconnect() {
    const delay = Math.min(1000 * 2 ** retry, 30000)
    retry++
    reconnectTimer = window.setTimeout(connect, delay)
  }
  function startPollFallback() { if (!pollTimer) pollTimer = window.setInterval(pollOnce, 5000) }
  function stopPollFallback() { if (pollTimer) { clearInterval(pollTimer); pollTimer = null } }
  // pollOnce 调用原 refresh() 逻辑（getOverview + getLatestPoints）
  onMounted(connect)
  onBeforeUnmount(() => { ws?.close(); stopPollFallback(); if (reconnectTimer) clearTimeout(reconnectTimer) })
  return { connected }
}
```

### 3.2 Monitor.vue 接入

- 引入 `useRealtime(msg => handleWsMessage(msg))`
- `handleWsMessage`：
  - POINTS → `points.value = msg.data`，重绘 marker
  - RISK → `overview.value.todayRisks!++`，`ElMessage` 提示
  - ALARM → `overview.value.todayWarnings!++`
- 保留原有 `refresh()` 供手动刷新与降级兜底
- 移除 `onMounted` 中的 `setInterval(refresh, 5000)`（由 useRealtime 接管）

### 3.3 API 层

- 不动 `api/traj.ts` / `api/monitor.ts`，降级时复用
- 新增类型 `WsMessage = { type: 'POINTS'|'RISK'|'ALARM', data: any[] }` 放 `composables/useRealtime.ts`

## 4. Nginx 配置

`portal-nginx` 需支持 WebSocket 升级，在 location 中加：

```
proxy_http_version 1.1;
proxy_set_header Upgrade $http_upgrade;
proxy_set_header Connection "upgrade";
proxy_read_timeout 3600s;
```

（若 /ws/ 路径单独 location 则配置在该 location，否则在通用 location）

## 5. 任务分解

| 任务 | 内容 |
|---|---|
| T1 | platform-boot 加 websocket starter；module-monitor 新增 Config/Interceptor/Registry/Handler/Scheduler；mvn compile |
| T2 | portal-nginx.conf 加 WebSocket 升级头；重建 nginx |
| T3 | 前端 useRealtime.ts + Monitor.vue 接入；npm build |
| T4 | 重建 platform-app，验证 WS 握手与推送 |
| T5 | 自测（§7） |
| T6 | MOD 实施记录回填、项目记忆更新 |

## 6. 风险与注意事项

1. **JWT 出现在 URL**：query 参数 token 会被 nginx access.log 记录，演示环境可接受；生产需改首条消息发 token 或 subprotocol。本期用 query 简化。
2. **增量窗口重复**：2s 窗口与 1s 调度重叠可能重复推送同一事件，前端按 id 去重（RISK/ALARM 用 Set 缓存最近 100 个 id）。
3. **@Scheduled 与 F20 复用**：platform-boot 已 `@EnableScheduling`（F20 启用），本调度器直接生效。
4. **广播性能**：遍历 session 同步发送，百级会话无压力；若 N 大需改异步批量发送，本期不做。
5. **POINTS 全量体积**：在途车辆上限估算 200 辆 × ~200B = 40KB/s，可接受。

## 7. 自测计划

1. **握手鉴权**：无 token / 错误 token → 握手 401；有效 token → 101 Switching Protocols。
2. **POINTS 推送**：连接后 1s 内收到首条 POINTS；启动模拟器，地图 marker 位置 1s 级更新。
3. **RISK 推送**：信号事件触发后 3s 内收到 RISK 消息，前端 toast 提示且计数 +1。
4. **ALARM 推送**：构造新 warn_info 插入，3s 内收到 ALARM。
5. **降级**：手动关闭 WS（浏览器 devtools 断网），5s 内自动回退轮询，数据继续刷新；恢复网络后自动重连成功并停止轮询。
6. **重连退避**：服务端重启后，前端按 1s/2s/4s... 退避重连，恢复后 connected=true。
7. **权限**：未登录访问 /ws/realtime 被拒（401），不泄露数据。

## 8. 实施记录（2026-10-02）

### 8.1 产出物
- 后端 module-monitor/websocket：`RealtimeWebSocketConfig`、`AuthHandshakeInterceptor`、`SessionRegistry`、`RealtimeWebSocketHandler`、`RealtimePushScheduler`
- platform-boot pom 加 `spring-boot-starter-websocket`；module-monitor pom 加 `module-trajectory` + `spring-websocket`
- `PlatformApplication` 加 `@EnableScheduling`（从 RiskOrderScheduler 上移，解决 bean 顺序导致 module-monitor @Scheduled 漏注册问题）
- 前端 `composables/useRealtime.ts`（WS 连接 + 指数退避重连 + 5s 轮询降级）；`Monitor.vue` 接入，移除 setInterval，加连接状态标签
- nginx `portal.conf` 加 `/ws/` location（Upgrade/Connection 头，read_timeout 3600s）

### 8.2 自测结果
- **握手鉴权**：无 token 拒绝；有效 token → 101 Switching Protocols，连接建立
- **POINTS 推送**：连接即收首帧，每秒全量位置快照；启动模拟器后车辆速度 1s 级变化（浏览器验证）
- **RISK 推送**：信号事件产生后，WS 客户端收到 `{type:"RISK",data:[...]}`；浏览器监控页"今日风险事件"计数 10→11（端到端验证）
- **降级**：工具栏标签显示"实时推送：已连接/降级轮询"
- **游标初始化**：`@PostConstruct` 将 lastRiskId/lastAlarmId 设为当前 max id，避免重启回放全量历史

### 8.3 避坑
- **@EnableScheduling 必须在启动类**：放在业务 @Component（如 RiskOrderScheduler）上时，ScheduledAnnotationBeanPostProcessor 注册时机晚于部分 bean，导致其他模块 @Scheduled 漏处理。移到 `PlatformApplication`（@SpringBootApplication 内含 @Configuration）确保最早注册。
- **ObjectMapper 不能 new**：`new ObjectMapper()` 未注册 JavaTimeModule，序列化含 LocalDateTime/BigDecimal 的实体（RiskEvent/GpsPoint）抛异常。必须注入 Spring 容器管理的 ObjectMapper。
- **PostgreSQL FILTER 子句位置**（F20 已记，F14 未涉及但相关）：略。
- **PowerShell 后台任务**：Start-Job 跨进程无法传递 WebSocket 对象且常静默失败；并发测试用 Start-Process 独立进程或 Runspace + 文件输出。
