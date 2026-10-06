# 修复方案存档：「运行模式」菜单更名后多标签页标题仍显示旧名「网关运行状态」

- 归档编号：20261006-02
- 日期：2026-10-06
- 类型：前端命名一致性 hotfix（已实施并验证）
- 关联文档：
  - [GATEWAY-PLAN-001 v1.3](../reference/GATEWAY-PLAN-001-mydbd对接vps车载网关改造方案.html)（第 10 章对照表「运行模式页」行）
  - 同日关联存档：[20261006-01-jwt-alg-401-fix.md](20261006-01-jwt-alg-401-fix.md)（本页 401 踢登录缺陷的前序修复）

## 1. 目标与范围

菜单 id=925 在 2026-10-06 二轮已由「网关接入状态」更名为「运行模式」（数据库 sys_menu 层面，左侧菜单显示正常），但打开该菜单后顶部多标签页标题仍显示旧名「网关运行状态」。需让标签标题与菜单名对齐，并清理代码/文档中的同名残留。

范围内：前端路由静态标题与 API 模块注释更名、重新构建发版、GATEWAY-PLAN-001 对照表措辞对齐。
范围外：菜单数据（sys_menu 已正确，无需动）、权限码 system:gateway:view、路由 path /system/gateway 与组件文件名 GatewayStatus.vue（均为内部标识，保持不变）。

## 2. 现状分析（根因）

多标签页标题与左侧菜单名来源不同：

1. 左侧菜单名取自后端动态菜单（sys_menu.name），二轮更名时已更新为「运行模式」。
2. 多标签页标题取自前端路由静态配置：`MainLayout.vue` 的路由 watcher（`immediate: true`）在每次路由进入时执行 `tabs.visit(route.path, route.fullPath, route.meta.title || ...)`，标题来源为 `route.meta.title`。
3. `frontend/src/router/index.ts` 中 /system/gateway 路由的 `meta.title` 仍为「网关运行状态」，从未跟随菜单更名——这是标签旧名的唯一来源。
4. 标签持久化在 sessionStorage（key=mydbd-tabs），旧标签以旧标题恢复；但 `tabs.visit()` 对已存在标签会用当前 title 覆盖（store/tabs.ts L63-L65），因此再次进入路由即自愈，无需数据迁移。

全量盘点（grep 全工作区，排除 documentation）：旧名仅残留 2 处生产代码——router/index.ts 的 meta.title、api/gateway.ts 的文件头注释。

## 3. 实施方案（已确认并执行）

1. `frontend/src/router/index.ts`：/system/gateway 路由 `meta.title` 由「网关运行状态」改为「运行模式」（实际修复点）。
2. `frontend/src/api/gateway.ts`：文件头注释同步为「运行模式页网关状态（GATEWAY-PLAN-001，菜单 925『运行模式』）」，消除术语残留。
3. `documentation/reference/GATEWAY-PLAN-001-*.html`：第 10 章对照表行标题「网关运行状态页」改为「运行模式页」，单元格补记三轮标签更名并挂本存档链接（该文档当日已为 v1.3，措辞修正计入同日 v1.3，不另升版）。

部署：`npm run build`（vite，37.5s 成功）；nginx 以 `./frontend/dist:/usr/share/nginx/html:ro` 只读卷挂载且 index.html 下发 no-cache/no-store 头，新产物即时生效，未重启任何容器。

## 4. 影响面

- 仅前端展示文案；不涉及接口、权限码、路由路径、数据库变更。
- 已打开过旧标签的浏览器：重新进入「运行模式」菜单即由 visit() 自动覆盖为新名；若仍见旧名，硬刷新（Ctrl+Shift+R）或重新登录即可（首次自动化复验即因复用构建前已打开的浏览器标签、加载旧 chunk 而误报 FAIL，清会话复验通过）。

## 5. 风险与回滚

- 风险极低：1 行路由配置 + 1 行注释 + 1 处文档措辞。
- 回滚：还原 router/index.ts 一行并重新 build 即可。

## 6. 验证方式与结论（已执行）

1. 构建产物全量 grep「网关运行状态」：dist 内 0 匹配。
2. HTTP 核对：nginx 实际下发的 /index.html 引用新入口 chunk `assets/index-Bu_RQCNs.js`，响应头 `no-cache, no-store, must-revalidate`。
3. 浏览器实测（清 sessionStorage/localStorage → admin/admin123 登录 → 硬重载 → 系统管理 → 运行模式）：
   - 活跃标签 `.tab-item.active .tab-title` 文本 = 「运行模式」；
   - sessionStorage mydbd-tabs 中 /system/gateway 的 title = 「运行模式」；
   - 切到「系统参数」再切回，标签与存储均仍为「运行模式」（旧标题自愈覆盖逻辑成立）；
   - 页面正常、无踢登录、无相关控制台错误。

结论：标签标题已与菜单名对齐为「运行模式」，代码与文档无旧名残留。

## 7. 变更文件清单

- frontend/src/router/index.ts（meta.title）
- frontend/src/api/gateway.ts（文件头注释）
- frontend/dist/**（vite 重新构建产物，卷挂载即时生效）
- documentation/reference/GATEWAY-PLAN-001-mydbd对接vps车载网关改造方案.html（第 10 章对照表措辞 + 本存档链接）
