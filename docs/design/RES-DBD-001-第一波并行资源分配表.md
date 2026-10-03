# RES-DBD-001 第一波并行资源分配表（F15/F16/F17/F22/F23）

> 版本：v1.0　|　日期：2026-10-03　|　用途：五项功能设计并行开工的**唯一资源仲裁依据**。
> 任何设计文档（MOD）与后续实施若与本表冲突，以本表为准；需变更先改本表。

## 一、并行分组与依赖

| 组 | 功能 | 设计文档 | 依赖关系 |
|---|---|---|---|
| A | F17 终端报警中心 | MOD-MON-004 | 无前置；其 §接口契约 供 F15/F16 引用 |
| A | F15 监控总览大屏 | MOD-MON-002 | 引用 F14 推送、F17 报警契约（见本表 §四） |
| A | F16 车辆详情聚合面板 | MOD-MON-003 | 引用 MDM/轨迹/F17 契约（见本表 §四） |
| B | F22 驾驶行为评分模型 | MOD-ANA-001 | 无前置；其表与接口契约见本表 §五，供 F23 引用 |
| B | F23 风险趋势与画像 | MOD-ANA-002 | 引用 F18/F20/F21 数据 + F22 契约（本表 §五） |

A、B 两组之间零依赖，可完全并行。组内依赖已通过本表 §四/§五 的契约固化，无需等待对方文档完成。

## 二、后端模块归属（锁定）

| 功能 | Maven 模块 | 包名 | 说明 |
|---|---|---|---|
| F15/F16/F17 | **module-monitor（已有，扩展）** | com.mydbd.monitor | WarnInfo 实体/Mapper 已在此模块，F17 直接扩展；F15/F16 新增聚合 Controller |
| F22/F23 | **module-analysis（新建）** | com.mydbd.analysis | 新模块注册三处：父 pom modules+dependencyManagement、platform-boot 依赖（见项目避坑经验） |

- 模块间零 Maven 依赖约定不变：module-analysis 读 risk/工单数据一律走同库跨 schema SQL，不 import 其他模块类。
- API 前缀：F15 `/api/monitor/dashboard/**`；F16 `/api/monitor/vehicle-panel/**`；F17 `/api/alarm/**`；F22 `/api/analysis/score/**`；F23 `/api/analysis/profile/**`。

## 三、菜单 / 权限码 / SQL 脚本 / 错误码分配（锁定）

### 3.1 已占用（勿用）

- 菜单：10~14（监控目录/导航监控/回放/风险预警/工单）、100~104、201~204（MDM）、900~905、911~916、920~924（系统/风控/字典/参数/消息中心）。
- SQL 脚本：infra/postgres/init 01~11。
- 权限码前缀：monitor:view、playback:view、risk:*、mdm:*、iam:*、audit:*、system:*、notify 相关。

### 3.2 本次分配

| 资源 | F15 | F16 | F17 | F22 | F23 |
|---|---|---|---|---|---|
| 菜单 id | **15**（实时监控目录下，path `/dashboard`，perm `monitor:dashboard:view`，menu_type=2，sort 15） | 无（组件，不出现在菜单） | **16**（实时监控目录下，path `/alarms`，perm `alarm:view`，menu_type=2，sort 16）＋ **926**（挂 16 下，按钮 `alarm:handle`） | **300** 目录"分析决策"（parent 0，sort 30）＋ **301**（path `/analysis/scores`，perm `analysis:score:view`，sort 31） | **302**（挂 300 下，path `/analysis/profiles`，perm `analysis:profile:view`，sort 32） |
| SQL 脚本 | 12-dashboard.sql（仅菜单+授权） | 无 | **13-alarm-center.sql**（索引/字典/菜单 16、926/授权） | **14-driving-score.sql**（评分表+字典 score_level+菜单 300/301/授权） | **15-risk-profile.sql**（菜单 302/授权，如需物化视图一并） |
| 错误码段 | 复用通用（40001/40401） | 复用通用 | **45001~45099**（45001 状态冲突：重复确认/解除等） | **46001~46099**（46001 参数错误、46002 计算任务冲突） | 复用 46xxx（46101 起） |
| 字典 | 无 | 无 | 新增 `alarm_handle_status`（0 待处理/1 已确认/2 已解除） | 新增 `score_level`（A/B/C/D/E，value=item_value） | 无（复用 score_level、risk_level） |
| 前端文件 | `views/dashboard/Dashboard.vue`（全屏路由，layout=false） | `components/VehicleDetailDrawer.vue`（共享组件） | `views/alarm/AlarmCenter.vue` | `views/analysis/ScoreList.vue` | `views/analysis/RiskProfile.vue` |
| 前端 API 文件 | `api/monitor.ts`（扩展） | `api/monitor.ts`（扩展） | `api/alarm.ts`（新建） | `api/analysis.ts`（新建） | `api/analysis.ts`（新建） |

### 3.3 角色授权（sys_role_menu）

| 菜单 | role1 admin | role2 SAFE_ADMIN | role3 DISPATCHER | role4 FLEET_CAPTAIN |
|---|---|---|---|---|
| 15 大屏 | ✓ | ✓ | ✓ | ✓ |
| 16 报警中心 | ✓ | ✓ | ✓ | ✓ |
| 926 报警处置按钮 | ✓ | ✓ | ✓ | ✗ |
| 300/301/302 分析 | ✓ | ✓ | ✗ | ✗ |

## 四、F17 报警契约（F15/F16 引用锚点，MOD-MON-004 细化）

- 数据表：`traj.traj_warn_info`（已有，含 handle_status/handle_result_code/handle_result_msg/handler/update_date/end_warn_time，**不新建主表**，只补索引与字典）。
- 状态语义：handle_status `0=待处理 1=已确认 2=已解除`。
- 核心接口（供 F15/F16 只读引用）：
  - `GET /api/alarm/page`：分页查询（plateNo/typeId/handleStatus/时间范围）。
  - `GET /api/alarm/latest?limit=N`：最新未处理报警（大屏/面板用）。
  - `GET /api/alarm/stats?start=&end=`：按 typeId、按状态聚合计数（大屏态势卡用）。
  - `POST /api/alarm/{id}/confirm`、`POST /api/alarm/{id}/resolve`：处置闭环（仅 F17 页面使用，perm `alarm:handle`，加 @AuditLog(HANDLE)）。
- 实时推送：F14 已有 `type:"ALARM"` 增量推送，F15/F17 前端直接复用 useRealtime，不新增消息类型。

## 五、F22 评分契约（F23 引用锚点，MOD-ANA-001 细化）

- 输出表：`mon.driver_score`（日粒度）
  - 列：id bigserial PK；score_date date；driver_id bigint；identity_code varchar(100)；plate_no varchar(50)；dept_id bigint；score numeric(5,1)（0~100）；level char(1)（A~E）；features jsonb（急加/急减/超速/疲劳/分心等特征计数与扣分明细）；sample_points integer；event_count integer；create_date timestamp。
  - 唯一约束：`uk_driver_score_date_driver(score_date, driver_id)`（重算=upsert）。
- 计算方式：Java 定时任务（每日 T+1 全量重算前一日）+ 手动触发重算接口；数据源 traj_gps_point（加速度特征）+ mon.risk_event（超速/疲劳/分心事件）。
- 核心接口（供 F23 引用）：
  - `GET /api/analysis/score/page`：评分分页（driver/plate/dept/日期范围/level）。
  - `GET /api/analysis/score/trend?driverId=&start=&end=`：单人趋势。
  - `POST /api/analysis/score/recalc`：手动重算（perm 复用 `analysis:score:view`？否——**单独按钮权限不新建，重算仅 admin+SAFE_ADMIN 通过角色校验实现**，MOD 中定稿）。
- F23 画像统计**只读 mon.driver_score + mon.risk_event + mon.risk_work_order + mon.risk_intervention**，不回写 F22 表。

## 六、前端文件边界（防冲突）

- `Monitor.vue`：F16 设计只允许"新增一行挂载 VehicleDetailDrawer + 地图点选回调"这一处改动；F15 不改 Monitor.vue（独立全屏路由页）。
- `RiskEvents.vue` / `RiskOrders.vue`：本波五项功能均不改动。
- 路由：`router/index.ts` 各功能只追加自己的路由项（/dashboard、/alarms、/analysis/scores、/analysis/profiles），互不重叠。
- useRealtime.ts：只读复用，不改内部逻辑；若需新消息类型，先改本表再动代码。

## 七、文档产出清单

| 文档 | 路径 |
|---|---|
| 本分配表 | docs/design/RES-DBD-001-第一波并行资源分配表.md |
| F15 | docs/design/mod/MOD-MON-002-监控总览大屏模块设计.md |
| F16 | docs/design/mod/MOD-MON-003-车辆详情聚合面板模块设计.md |
| F17 | docs/design/mod/MOD-MON-004-终端报警中心模块设计.md |
| F22 | docs/design/mod/MOD-ANA-001-驾驶行为评分模型模块设计.md |
| F23 | docs/design/mod/MOD-ANA-002-风险趋势与画像模块设计.md |

> PRD（HTML 业务版）暂不在本批产出；如需"双文档"惯例补齐，另行安排。
