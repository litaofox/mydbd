# MOD-IAM-001 用户与权限（IAM）模块设计

> 功能编号：F33（功能清单 §G 系统管理域）
> 文档版本：v1.1（2026-10-03）｜状态：**已上线**（F33 已落地并自测通过，见 §14 实施记录）
> 配套文档：[PRD-IAM-001 用户与权限产品设计](../../html/PRD-IAM-001-用户与权限IAM模块产品设计.html)

---

## 1. 模块概述

以 **RBAC（用户—角色—权限点）为主、ABAC（部门数据范围）为辅**，替代当前唯一演示账号 admin/admin123，建成后：

- 用户由管理员开号、分配一个或多个角色、可启用/停用/解锁/重置密码；
- 角色聚合"功能权限点（菜单/按钮）"与"数据范围（可见哪些部门的数据）"；
- 权限点以三级菜单树（目录/菜单/按钮）维护，随版本种子化，**本期不提供菜单的页面增删**；
- 后端接口注解鉴权（`@RequiresPerm`），前端动态菜单 + 路由守卫 + `v-perm` 按钮控制；
- 认证加固：PBKDF2 密码哈希、连续失败锁定、可选 TOTP 二次验证（MFA）、自助改密；
- 数据权限本期在 **MDM 车辆档案列表与组织树**落地（车辆是当前唯一带 `dept_id` 的业务对象），监控/轨迹/终端/司机随各自波次按同一机制接入。

### 1.1 范围内 / 范围外

| 在范围内（本期） | 不在范围内（后续） |
|---|---|
| 用户、角色、菜单/权限点、用户角色、角色权限、角色自定义部门 6 表 | 菜单的运行时增删改页面（权限点版本化种子） |
| 接口级 + 按钮级功能鉴权，超管内置绕过 | 字段级/属性级 ABAC、数据行列级脱敏策略 |
| 5 档数据范围（全部/本企业及以下/本部门及以下/仅本部门/自定义），车辆档案与组织树生效 | 监控、轨迹、终端、司机、风险等页面的数据过滤（同一机制后续接入） |
| PBKDF2 密码哈希、失败 5 次锁定 15 分钟、启停用、解锁、重置密码、自助改密 | 密码历史、首次登录强制改密、复杂度可配置、找回密码流程 |
| TOTP MFA（RFC 6238）自助绑定/登录二次验证/管理员关闭 | 短信、邮件、硬件 Key、企业微信/钉钉等 MFA 通道；SSO/LDAP/OAuth |
| 登录返回用户档案/权限集/菜单树；登出审计 | Token 黑名单、在线用户列表、强制下线（需 Redis/会话表，后续） |
| 种子：4 个内置角色、全量权限点树；首启自动建超管 admin | 用户注册、审批流、组织兼任多部门 |

---

## 2. 现状与设计约束

1. 当前认证：[AuthController.java](file:///d:/ProgramData/mytraeprojects/mydbd/backend-java/platform-boot/src/main/java/com/mydbd/boot/AuthController.java) 硬编码 admin/admin123；[JwtAuthFilter](file:///d:/ProgramData/mytraeprojects/mydbd/backend-java/platform-common/src/main/java/com/mydbd/common/security/JwtAuthFilter.java)（order=1）解析 JWT 写 `UserContext`，仅放行 `/api/auth/login`，失败真实 HTTP 401；[JwtUtil](file:///d:/ProgramData/mytraeprojects/mydbd/backend-java/platform-common/src/main/java/com/mydbd/common/security/JwtUtil.java) claims 仅 subject+role，TTL 8h。
2. `UserInfo(username, role)`、`UserContext` ThreadLocal 被审计、MDM、监控、轨迹等模块引用；改造必须**向后兼容地扩展字段**（保留 `username()` 等方法）。
3. 过滤器在 [WebConfig](file:///d:/ProgramData/mytraeprojects/mydbd/backend-java/platform-common/src/main/java/com/mydbd/common/config/WebConfig.java) 中以 `FilterRegistrationBean` 手工 new；AuditFilter（order=0）通过 request 属性 `mydbd.audit.user` 读取 UserInfo。
4. `@MapperScan("com.mydbd.**.mapper")` 覆盖新模块；统一 `Result<T>`/`PageData`、`BizException(ErrorCode)`、[GlobalExceptionHandler](file:///d:/ProgramData/mytraeprojects/mydbd/backend-java/platform-common/src/main/java/com/mydbd/common/exception/GlobalExceptionHandler.java) 已存在；业务失败 HTTP 200 + 非 0 code，仅认证过滤器返回真实 401。
5. 表均在 `traj` schema，init 脚本序号执行（现有 01~05）；新模块注册三处（父 pom modules+dependencyManagement、platform-boot pom）。
6. **不引入 Spring Security 全家桶**：密码哈希用 JDK 原生 PBKDF2（`PBKDF2WithHmacSHA256`），TOTP 用 JCE `Mac` 手写 RFC 6238，后端零新依赖。
7. 部门树 `traj.traj_dept`（id/parent_id/dept_type 1企业2车队）已由 F28 建成；车辆 `traj.traj_vehicle.dept_id` 可空；终端/司机表无 dept_id。
8. 前端路由静态、菜单在 MainLayout 写死；token/用户信息存 localStorage（`mydbd-token` 等）；http 拦截器 code≠0 报错、401 跳登录。

---

## 3. 总体设计

### 3.1 权限模型

```
sys_user ──< sys_user_role >── sys_role ──< sys_role_menu >── sys_menu(目录/菜单/按钮=权限点)
                                  │
                                  └──< sys_role_dept >── traj_dept   (仅数据范围=自定义时)
sys_user.dept_id ──► traj_dept（用户主属部门，数据范围 2/3/4 的计算锚点）
```

- **功能权限**：用户所有启用角色关联的、状态正常的菜单 `perm_code` 去重并集；超管（角色码 `SUPER_ADMIN`）恒为全部。
- **数据权限**：用户所有启用角色数据范围的**并集**（最宽生效）；任一角色为"全部"即不设限。
- 权限装载结果在请求级使用，进程内缓存 120 秒（`PermissionCache`），用户/角色/授权任一写操作主动 evict 相关用户。

### 3.2 请求链路（改造后）

```
AuditFilter(order=0，已存在)
  └ JwtAuthFilter(order=1，改造)
       1 放行 /api/auth/login、/api/auth/mfa/verify
       2 parse JWT → userId/username（业务 token claim purpose=access，默认）
       3 AuthRealm.loadPrincipal(userId)  ← module-iam 实现：用户状态 + perms + deptScope
         用户停用/删除/无有效角色不直接 401（仍放行，由接口权限控制；停用用户返回 401）
       4 UserContext.set(UserInfo) + request.setAttribute(供审计)
  └ Controller
       @RequiresPerm 切面（common，新增）：superAdmin 放行；perms 命中放行；否则 BizException(FORBIDDEN 40301)
       业务代码读 UserContext.get().deptScope() 自行过滤（本期车辆 Service 落地）
```

- common 不反向依赖 module-iam：在 common 定义 SPI 接口 `AuthRealm`，iam 提供 `DbAuthRealm` 实现；WebConfig 以 `ObjectProvider<AuthRealm>` 注入。
- purpose=mfa 的短期 token 只用于 `/api/auth/mfa/verify`，过滤器对其他路径携带 mfa token 一律 401。

### 3.3 登录与 MFA 时序

```
POST /api/auth/login (username,password)
   校验用户存在/启用/未锁定 → PBKDF2 比对
   失败：fail_count++，达 5 次 locked_until=now+15min；返回 40101（锁定返回 42301，提示剩余分钟）
   成功：fail_count=0，记录 last_login_time/ip
      ├─ mfa_enabled=0：签发 8h access token → {token,userId,username,realName,mfaRequired:false}
      └─ mfa_enabled=1：签发 5min mfa token（purpose=mfa）→ {mfaRequired:true,mfaToken}
POST /api/auth/mfa/verify (mfaToken,code)
   校验 mfa token + TOTP（允许 ±1 时间步）→ 成功签发 access token（同上传回用户基本信息）
                                   失败 40101（不累计密码失败次数）
登录后前端立即 GET /api/auth/profile 取 perms + 菜单树
```

---

## 4. 数据库设计（06-iam-tables.sql）

### 4.1 表结构

**traj.sys_user**（继承平台软删风格，不用雪花 ID，bigserial）

| 列 | 类型 | 说明 |
|---|---|---|
| id | bigserial PK | |
| username | varchar(40) not null | 登录名，字母数字下划线 4~20 |
| password_hash | varchar(120) not null | `pbkdf2$120000$<saltB64>$<hashB64>` |
| real_name | varchar(40) not null | 显示名 |
| phone | varchar(20) | |
| email | varchar(80) | |
| dept_id | bigint | 主属部门（数据权限锚点，可空=未分配组织） |
| status | smallint default 1 | 1 启用 0 停用 |
| mfa_enabled | smallint default 0 | |
| mfa_secret | varchar(64) | 已确认生效的 Base32 密钥 |
| mfa_pending_secret | varchar(64) | 绑定流程中待确认密钥，确认后转正 |
| fail_count | int default 0 | |
| locked_until | timestamp | 非空且 > now 表示锁定中 |
| pwd_update_time | timestamp | |
| last_login_time | timestamp | |
| last_login_ip | varchar(45) | |
| remark | varchar(200) | |
| valid_mark / creator / create_date / updater / update_date | 平台标准列 | 软删 0/1 |

唯一索引：`uk_sys_user_username ON (username) WHERE valid_mark=1`；索引 (dept_id)、(status)。

**traj.sys_role**

| 列 | 类型 | 说明 |
|---|---|---|
| id | bigserial PK | |
| role_code | varchar(40) not null | 英文编码，唯一（有效行） |
| role_name | varchar(40) not null | |
| data_scope | smallint default 3 | 1全部 2本企业及以下 3本部门及以下 4仅本部门 5自定义 |
| built_in | smallint default 0 | 1 内置（禁删、禁改编码/数据范围） |
| status | smallint default 1 | |
| remark | varchar(200) | |
| 标准列 | | valid_mark 等 |

唯一索引 `uk_sys_role_code ON (role_code) WHERE valid_mark=1`。

**traj.sys_menu**（权限点目录，不软删；版本种子幂等 upsert）

| 列 | 类型 | 说明 |
|---|---|---|
| id | bigserial PK（**种子显式 id**） | |
| parent_id | bigint default 0 | |
| menu_name | varchar(40) not null | |
| menu_type | smallint not null | 1目录 2菜单 3按钮 |
| perm_code | varchar(60) | 菜单/按钮的权限码；目录可空 |
| path | varchar(120) | 前端路由（菜单） |
| icon | varchar(40) | 目录/菜单图标（Element 图标名） |
| sort_no | int default 0 | |
| visible | smallint default 1 | 1 显示 0 隐藏（仍参与鉴权） |
| status | smallint default 1 | |
| create_date | timestamp default now() | 仅留创建时间 |

索引 (parent_id)；`uk_sys_menu_perm ON (perm_code) WHERE perm_code IS NOT NULL`。

**关系表**

- `traj.sys_user_role(user_id bigint, role_id bigint, PRIMARY KEY(user_id,role_id))`
- `traj.sys_role_menu(role_id bigint, menu_id bigint, PRIMARY KEY(role_id,menu_id))`
- `traj.sys_role_dept(role_id bigint, dept_id bigint, PRIMARY KEY(role_id,dept_id))` — 仅 data_scope=5 使用

### 4.2 种子数据（脚本幂等）

菜单树（显式 id）：

| id | 层级 | 名称 | 类型 | perm_code | path |
|---|---|---|---|---|---|
| 10 | 根 | 实时监控 | 目录 | — | /monitor-group |
| 11 | · | 实时导航监控 | 菜单 | monitor:view | /monitor |
| 12 | · | 历史轨迹回放 | 菜单 | playback:view | /playback |
| 13 | · | 风险预警分析 | 菜单 | risk:view | /risk |
| 100 | 根 | 主数据管理 | 目录 | — | /mdm-group |
| 101 | · | 组织架构 | 菜单 | mdm:org:view | /mdm/org |
| 102 | · | 车辆档案 | 菜单 | mdm:vehicle:view | /mdm/vehicles |
| 103 | · | 终端档案 | 菜单 | mdm:terminal:view | /mdm/terminals |
| 104 | · | 驾驶员档案 | 菜单 | mdm:driver:view | /mdm/drivers |
| 201 | 101 下 | 组织维护 | 按钮 | mdm:org:edit | |
| 202 | 102 下 | 车辆维护 | 按钮 | mdm:vehicle:edit | |
| 203 | 103 下 | 终端维护 | 按钮 | mdm:terminal:edit | |
| 204 | 104 下 | 驾驶员维护 | 按钮 | mdm:driver:edit | |
| 900 | 根 | 系统管理 | 目录 | — | /system-group |
| 901 | · | 用户管理 | 菜单 | iam:user:view | /system/users |
| 902 | · | 角色管理 | 菜单 | iam:role:view | /system/roles |
| 903 | · | 操作审计 | 菜单 | audit:view | /system/audit |
| 911 | 901 下 | 用户维护 | 按钮 | iam:user:edit | |
| 912 | 902 下 | 角色维护 | 按钮 | iam:role:edit | |

角色种子（built_in=1）：

| code | 名称 | data_scope | 权限 |
|---|---|---|---|
| SUPER_ADMIN | 超级管理员 | 1 全部 | 全部（代码绕过，role_menu 灌全用于回显） |
| SAFE_ADMIN | 安全管理员 | 1 全部 | iam:user:view/edit、iam:role:view、audit:view、mdm:org:view |
| DISPATCHER | 调度监控员 | 3 本部门及以下 | monitor/playback/risk:view、mdm 四项 view（只读） |
| FLEET_CAPTAIN | 企业车队长 | 2 本企业及以下 | monitor/risk:view、mdm 全部 view + 三项 edit（组织仅 view） |

### 4.3 超管初始化（应用侧，非 SQL）

`DataInitializer`（module-iam，ApplicationRunner）：若 sys_user 中不存在 `admin`（有效），创建 admin / 初始密码（配置 `mydbd.bootstrap.admin-password`，默认 admin123），关联 SUPER_ADMIN，dept_id=null；已存在则跳过。避免把密码哈希写死在 SQL 中，且开发库手工执行脚本后重启即自动补齐。

---

## 5. platform-common 改造

### 5.1 UserInfo / UserContext（扩展不破坏）

```java
public record UserInfo(Long userId, String username, String realName,
                       Long deptId, boolean superAdmin,
                       Set<String> perms, Set<Long> deptScope) {
    // null deptScope = 全部数据；空集合 = 任何部门数据不可见
    public boolean hasPerm(String code) { ... }
}
```

兼容：`UserContext.username()` 保留；新增 `get()`、`deptScope()`。当前 JWT 老 token（无 uid claim）过渡期一律视为无效，重新登录即可。

### 5.2 JwtUtil / JwtAuthFilter

- `JwtUtil.generateAccess(userId, username)`：claims `uid`、`purpose=access`；`generateMfa(username)`：TTL 5min、`purpose=mfa`；保留 8h access TTL。
- 过滤器放行集合：`/api/auth/login`、`/api/auth/mfa/verify`；解析后校验 purpose（非 access 一律 401）；`AuthRealm.loadPrincipal(uid)` 装载 UserInfo，用户不存在/停用抛认证失效（401）。
- WebConfig 构造过滤器改为注入 `ObjectProvider<AuthRealm>`（无 IAM 模块时的测试场景退化为仅 subject 的最小主体——实际 platform-boot 必含 iam）。

### 5.3 SPI 接口与权限缓存（common/security）

```java
public interface AuthRealm {
    UserInfo loadByUserId(Long userId);   // 供过滤器
}
```

`PermissionCache`：`ConcurrentHashMap<Long, Entry(UserInfo, expireAt)>`，TTL 120s；`evict(userId)`、`evictAll()`；iam 侧在用户写/角色写/授权写后调用（通过 Realm 接口上的 default 方法或由 iam 内部直接操作同一缓存 bean——缓存类放 common，iam 注入即可）。

### 5.4 @RequiresPerm 注解 + 切面（common/security）

```java
@Target(METHOD) @Retention(RUNTIME)
public @interface RequiresPerm {
    String[] value();
    Logical logical() default Logical.AND;   // AND/OR
}
```

`PermAspect`（@Around，依赖 spring-aop 已在 common）：超管放行；按 logical 匹配 `UserContext.get().perms`；不满足抛 `BizException(ErrorCode.FORBIDDEN, "无操作权限：xxx")`。未登录（UserInfo 为空）抛 UNAUTHORIZED。

### 5.5 ErrorCode 新增

`LOCKED(42301, "账号已锁定")`；其余复用 40101/40301/40001/40401/40901。

---

## 6. module-iam 后端设计（包 com.mydbd.iam）

### 6.1 结构

```
com.mydbd.iam
├── entity/   SysUser SysRole SysMenu SysUserRole SysRoleMenu SysRoleDept
├── mapper/   6 Mapper（含自定义联查）
├── dto/      LoginRequest MfaVerifyRequest UserSaveDTO UserQuery RoleSaveDTO ChangePasswordDTO
│             MfaEnableDTO MfaDisableDTO ResetPasswordDTO AssignRoleDTO
├── vo/       UserVO（列表行，含 deptName/roleNames/locked 状态，排除 hash/secret）
│             RoleVO RoleDetailVO MenuNode ProfileVO LoginVO
├── service/  AuthService UserService RoleService MenuService DataScopeService
│             PasswordCodec TotpService
├── realm/    DbAuthRealm（implements AuthRealm）
├── controller/ AuthController IamUserController IamRoleController IamMenuController
└── config/   IamConfig（@EnableAsync 不需要）、DataInitializer
```

### 6.2 关键算法

**PasswordCodec（PBKDF2）**：`PBKDF2WithHmacSHA256`，迭代 120000，salt 16 字节随机，hash 256bit，串格式 `pbkdf2$120000$<Base64(salt)>$<Base64(hash)>`；常量时间比较。密码复杂度：8~20 位，须含字母与数字（前后端双校验）。

**TotpService（RFC 6238）**：新密钥 20 字节随机 → Base32（去填充）；30s 时间步、6 位数字、HMAC-SHA1；校验允许当前步 ±1（共 3 个码）；otpauth URI：
`otpauth://totp/mydbd:<username>?secret=<s>&issuer=mydbd`。

**DataScopeService 计算 deptScope（Set<Long>，null=全部）**：

1. 查用户全部**启用**角色；
2. 任一 data_scope=1 → 返回 null；
3. 否则收集：
   - 2（本企业及以下）：用户 deptId 向上回溯到 dept_type=1 的祖先企业 E，收集 E 子树全部 id；
   - 3（本部门及以下）：用户 deptId 子树；
   - 4（仅本部门）：用户 deptId；
   - 5（自定义）：sys_role_dept 列表；
4. 多角色取并集。用户无 deptId 且遇 2/3/4 档时该角色贡献空集；最终空集 = 不可见任何部门数据。
部门子树在 Java 内由一次性全量 dept 列表（走 module-mdm？为避免模块依赖，iam 直接查 traj_dept 表的只读 mapper）计算。

### 6.3 接口清单

认证（AuthController，承接 platform-boot 旧接口并删除旧类）：

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| POST | /api/auth/login | 放行 | 见 §3.3；返回 LoginVO |
| POST | /api/auth/mfa/verify | 放行 | mfaToken+code → access token |
| POST | /api/auth/logout | 登录 | 无状态，仅触发审计；前端清本地 |
| GET | /api/auth/profile | 登录 | ProfileVO{user, roles:[code], perms:[code], menus:[MenuNode 树]} |
| PUT | /api/auth/password | 登录 | 旧密码+新密码；成功后本次 token 仍有效（后续可换发） |
| GET | /api/auth/mfa/setup | 登录 | 生成 pending secret，返回 secret + otpauthUri（已启用则拒绝） |
| POST | /api/auth/mfa/enable | 登录 | 校验 code 与 pending，转正 |
| POST | /api/auth/mfa/disable | 登录 | 校验登录密码（+当前 code），关闭并清密钥 |

用户管理（`iam:user:view` / `iam:user:edit`）：

| 方法 | 路径 | 权限 |
|---|---|---|
| GET | /api/iam/users | iam:user:view（分页：username/realName/status/deptId/roleId） |
| GET | /api/iam/users/{id} | iam:user:view（含 roleIds、详情） |
| POST | /api/iam/users | iam:user:edit（@AuditLog CREATE，objectId 取响应 data.id） |
| PUT | /api/iam/users/{id} | iam:user:edit（UPDATE） |
| PUT | /api/iam/users/{id}/status | iam:user:edit（启用/停用） |
| PUT | /api/iam/users/{id}/reset-password | iam:user:edit（管理员设新密码，清锁定） |
| PUT | /api/iam/users/{id}/roles | iam:user:edit（分配角色，全量覆盖） |
| PUT | /api/iam/users/{id}/unlock | iam:user:edit（清 fail_count/locked_until） |
| DELETE | /api/iam/users/{id} | iam:user:edit（软删，DELETE 留痕） |

角色管理：`GET/POST/PUT/DELETE /api/iam/roles[...]`（view/edit 同理），详情含 menuIds、deptIds；`GET /api/iam/roles/all` 启用列表供用户分配；`GET /api/iam/menus/tree` 全量权限点树（iam:role:view 或 iam:user:edit 均可调用，供勾选）。

业务规则：

- admin（用户名为 admin 的超管）不可停用、不可删除、不可取消 SUPER_ADMIN 角色、不可改用户名；
- 当前登录用户不可停用/删除自己、不可给自己移除全部角色；
- 内置角色（built_in=1）不可删除，role_code/data_scope 不可改；
- 删除角色前校验有效关联用户，有则 40901「该角色已分配给 N 个用户，请先解除」；
- 用户名重复（有效行）40901；停用用户的 token 在 120s 缓存过期后失效（用户停用操作同时 evict）。

### 6.4 Mapper 关键查询

- `selectPermsByUserId(uid)`：user_role join role（status=1,valid_mark=1）join role_menu join menu（status=1），distinct perm_code（非空）。
- `selectMenuTreeByUserId(uid)`：同上但取 menu_type in (1,2)、visible=1 的行，Java 内建树；超管直接全量。
- 用户分页：sys_user left join traj_dept 取 dept_name，角色名聚合（PG `string_agg` 别名为 `roleNames`，注意 MyBatis 映射用驼峰别名）；条件查询可按 roleId 经 exists 子查询过滤。
- PG 聚合 count 别名带引号的老问题只在 @Select Map 场景出现，VO 映射用驼峰别名即可。

---

## 7. MDM 数据权限接入（本期最小落地）

- **车辆档案分页**（module-mdm VehicleService）：读 `UserContext.get().deptScope()`：
  - null → 不过滤（超管/全部范围）；
  - 空集 → 直接返回空页；
  - 非空 → 查询条件追加 `dept_id IN (scope)`（dept_id 为 NULL 的车辆对受限角色不可见）。
- **组织树**（`/api/mdm/depts/tree`）：受限用户按 deptScope 裁剪树（超管不变）；空集返回空数组。
- 终端/司机/监控/轨迹/风险**本期不改**，PRD 与本文档明确为后续波次接入点（机制：同一 `UserContext.deptScope()`，业务对象具备部门归属后即时可加）。
- MDM 写操作的数据归属校验本期不做（功能权限 iam 已拦无 edit 权限者；跨部门越权写列入后续）。

---

## 8. 前端设计

### 8.1 路由与菜单

- 静态路由全部保留，meta 增补 `perm`（audit 已有）：monitor/playback/risk、mdm 四页、system/users、system/roles、system/audit。
- 新增路由：`/system/users`（UserList.vue）、`/system/roles`（RoleList.vue）。
- `router.beforeEach`：无 token→/login；有 token 但 store 未装载 profile→先 `await profile`（页面刷新场景）；`meta.perm` 不满足→跳 /monitor 并 ElMessage 无权限。
- MainLayout 侧边菜单改为**由 profile.menus 动态渲染**（el-sub-menu/el-menu-item，图标经本地 icon map 映射 Element 图标组件）；不再写死。
- 新增指令 `v-perm`（`src/directives/perm.ts`，main.ts 注册）：无权限时移除宿主元素；按钮级控制（新建/编辑/删除/重置密码/分配角色/解锁等）。

### 8.2 store/auth.ts

state 增加 `userId/realName/deptId/perms:string[]/menus:MenuNode[]/loaded:boolean`，localStorage 只持久化 token（权限每次登录/刷新拉取，避免陈旧授权）；actions：

- `login(username,password)` → mfaRequired=false 时保存 token 并 `loadProfile()`；=true 时返回标志，由登录页进入二步；
- `verifyMfa(mfaToken,code)` → 存 token + loadProfile；
- `loadProfile()`、`logout()`（调 logout 接口，失败不阻塞本地清理）；
- getter `has(code)`。

### 8.3 页面

1. **Login.vue 改造**：同一卡片两阶段——账号密码 → 二次验证码（6 位数字输入，支持重发/返回上一步）；错误文案展示锁定剩余分钟。
2. **MainLayout 右上角**：用户名下拉（个人资料与安全、退出登录）。
3. **components/ProfileDialog.vue**：三个折叠区——基本资料（用户名只读、姓名/手机/邮箱/部门可改→PUT 复用？本期新增 `PUT /api/auth/profile` 更新本人非敏感信息：realName/phone/email/deptId 不改部门（部门由管理员定）→ 简化：只允许改 phone/email）；修改密码；MFA 绑定（未绑：显示二维码（`qrcode` npm 包渲染 otpauthUri）+ 手动密钥 + 6 位确认；已绑：显示开启状态与关闭按钮，关闭需密码+验证码）。
4. **views/system/UserList.vue**：筛选卡（用户名/姓名/状态/部门）、表格（用户名/姓名/部门/角色 tags/状态/MFA/最近登录/锁定态）、新建编辑对话框（必填用户名（创建后不可改）/姓名/密码（创建必填，重置另走按钮）/手机/邮箱/部门/状态/角色多选）、行操作：编辑、分配角色（编辑对话框内合并）、重置密码、解锁（锁定行可见）、启停用、删除（二次确认，admin 行无危险操作按钮）。
5. **views/system/RoleList.vue**：表格（编码/名称/数据范围/内置/状态/用户数）、编辑对话框三分区：基本信息（code 创建后不可改、名称/状态/备注）、数据范围（5 选 1，选"自定义"显示部门树 el-tree 多选勾选）、功能权限（菜单/按钮 el-tree show-checkbox，父子联动；保存时提交 checked+halfChecked 以保留目录/菜单的可见性——提交全部勾选中含半选父节点）。内置角色 code/数据范围只读。
6. **api/iam.ts、api/auth.ts** 按 §6.3 封装；id 统一 number（本模块 bigserial，量级小；但用户列表如有大数风险——当前数据量无虞，保持 number 与 MDM 一致）。
7. dict.ts 增：用户状态、MFA 状态、数据范围、菜单类型枚举。

### 8.4 二维码依赖

`npm i qrcode @types/qrcode`（qrcode 为纯前端无原生依赖包）。若构建环境无法安装，MfaBind 降级为只展示 otpauth URI 与手动输入密钥（认证器 App 均支持手动录入），不阻断功能。

---

## 9. 与审计（F34）的衔接

- 登录成功/失败沿用 AuditFilter 特例（LOGIN/LOGIN_FAIL）；锁定、MFA 二次验证经 `/api/auth/mfa/verify` POST 自动留痕（module=AUTH/action=POST，result_code 可区分成败）。
- 用户/角色写接口由 `AuditFilter` 全量自动记录；另在 Controller 关键方法加 `@AuditLog(action=CREATE/UPDATE/DELETE, objectId SpEL)` 补充语义（如 `#id`、响应 data.id）。
- 审计页的 `audit:view` 权限点正式生效：非授权角色菜单不可见、接口 40301。
- 用户被软删后历史审计行的 user_name 为当时快照样可查（审计不做 join）。

---

## 10. 安全要点

1. 密码：PBKDF2-SHA256 120k 迭代 + 每用户随机盐；日志/接口永不回传 hash、secret。
2. 登录：失败计数持久化；锁定阈值与时长本期写死（5 次/15 分钟），后续随 F35 配置化；不区分"用户不存在"与"密码错误"文案，统一"用户名或密码错误"。
3. MFA：secret 仅在 setup 响应中出现一次；pending 与正式分离，未确认不生效；关闭需再验密码。
4. 越权：所有 iam 写接口必须显式注解；超管保护规则后端硬编码，前端隐藏按钮仅体验层。
5. 审计用户管理操作不可由被管用户自行抹除（用户页无审计权限即看不到）。
6. JWT secret 继续走环境变量 `JWT_HMAC_SECRET`；mfa token 与 access token 以 purpose claim 隔离。

---

## 11. 任务分解

| 任务 | 内容 | 产出 |
|---|---|---|
| T1 | 06-iam-tables.sql + 开发库执行 | 6 表、菜单/角色种子 |
| T2 | common 改造：UserInfo/Jwt/JwtFilter/AuthRealm/@RequiresPerm/PermAspect/ErrorCode | 鉴权底座 |
| T3 | module-iam：实体/mapper/service/controller/PasswordCodec/Totp/DataScope/DataInitializer；删除旧 AuthController；pom 三处注册 | 后端接口 |
| T4 | MDM 车辆列表+组织树接入 deptScope | 数据权限首个落点 |
| T5 | 前端：store/router/v-perm/MainLayout/Login/Profile/UserList/RoleList/api/dict | 管理端页面 |
| T6 | mvn compile + npm build + 容器重建 | 可运行环境 |
| T7 | 接口自测（见 §12） | 自测记录 |
| T8 | 浏览器端到端验证 | 截图/结论 |

## 12. 自测计划（接口层）

1. 首启自动建 admin；旧密码 admin123 登录成功；profile 返回全量 perms 与完整菜单树。
2. 错误密码 ×5：前 4 次 40101，第 5 次起 42301 锁定提示；解锁接口后恢复。
3. 建角色"测试车队长"（data_scope=2）+ 用户 captain01（dept=企业1），登录：
   - 菜单只见监控/主数据等授权项；`/api/iam/users` 返回 40301；
   - `/api/mdm/vehicles` 仅见企业 1 子树车辆；`/api/mdm/depts/tree` 裁剪。
4. 只读角色调 MDM 写接口 → 40301。
5. 新建用户/改密/停用/重置/删除全链路；停用后该用户旧 token 120s 内被踢（evict 立即生效，验证立即 401）。
6. admin 保护：停用/删除 admin、删 SUPER_ADMIN 角色、改内置角色 code 均 40901/40301。
7. 角色删除占用校验：先给角色配用户再删 → 40901；解除后删除成功。
8. 自定义数据范围角色（只选企业 2）→ 车辆只见企业 2。
9. MFA：setup 返回 32 位 Base32 密钥与 otpauth URI；用密钥按 RFC6238 生成正确码 → enable 成功；重新登录返回 mfaRequired+mfaToken；错误码 40101、正确码换 access token；disable 流程关闭成功，登录恢复单步。
10. 改密：旧密码错误 40001；新密码不合规 40001；成功后新密码可登录。
11. 回归：监控/轨迹/风险/MDM 既有接口在 admin 下全部 200 code=0；审计页非授权用户 40301、admin 正常。
12. 审计核对：用户/角色写操作、登录失败/锁定/MFA 验证在 sys_audit_log 留痕且密码/密钥不出现在 request_body（现有脱敏覆盖 password；secret 字段在 iam 接口字段名为 mfaSecret/secret，需在 AuditFilter 脱敏键集合补充 `secret`/`mfaSecret`/`code`（验证码 code 不入 body 脱敏？验证码也脱敏，键名 `code`））。

> T2 实施时同步更新 AuditFilter 脱敏键：新增 secret、mfaSecret、totpCode、code。

## 13. 待确认事项

1. 本期密码策略最小化（8~20 位含字母数字）、失败锁定 5 次/15 分钟写死，后续随 F35 配置化——建议认可。
2. TOTP 为唯一 MFA 通道，短信/邮件后续——建议认可。
3. 数据权限仅在车辆档案/组织树生效，其余模块后续波次接入——建议认可。
4. 权限点（菜单）本期种子化、无管理页面——建议认可（页面在后续运维增强期开放）。
5. 停用用户权限失效有 ≤120s 缓存窗口；停用/改角色时主动 evict 使窗口实际为 0（仅极端并发下可能短暂延迟）——建议认可。

---

## 14. 实施记录（F33 落地，2026-06）

### 14.1 完成情况

T1~T8 全部完成并通过验证：6 表 + 种子、common 鉴权底座、module-iam 全量接口、MDM 车辆列表/组织树数据权限落点、前端管理端（动态菜单/v-perm/用户/角色/个人中心/MFA）、构建部署、43 项接口自测、浏览器端到端。当前有效数据仅 admin 用户与 4 个内置角色；验证用临时用户/角色均已软删且关联表清零。

### 14.2 与原设计的偏差（重要）

1. **ID 策略调整：sys_user / sys_role 采用雪花 ID，接口层字符串化。**
   §8.3 第 6 条原记"id 统一 number（bigserial，量级小）"。实施时实体 ID 改为 MyBatis-Plus 雪花算法（19 位，如 admin=2105950458955513857），超出 JS `Number.MAX_SAFE_INTEGER`，前端精度丢失。落地方案：
   - 后端 VO 对用户/角色 id 统一加 `@JsonSerialize(using = ToStringSerializer.class)`（LoginVO.userId、UserProfile.id、UserListVO/UserDetailVO/RoleListVO/RoleDetailVO/OptionRoleVO.id；UserDetailVO.roleIds 加 `contentUsing = ToStringSerializer`）；创建接口返回 `Result<String>`。
   - **例外保持 number**：RoleDetailVO.menuIds/deptIds、菜单 id（10~912 小号）、部门 id（1、2 小号）——el-tree `node-key` 与勾选回传依赖 number，且无精度风险。
   - 前端 api 类型、store userId、用户/角色表单 id 全部改为 string。
2. **缺陷修复 A：登录失败锁定策略原实现完全失效。** `AuthService.login()` 原带 `@Transactional(rollbackFor = Exception.class)`，密码错误抛 BizException 时，失败计数 UPDATE 随事务回滚，永远到不了 5 次。修复：移除该方法事务注解（单表单行写无需事务，锁定计数必须独立提交）。
3. **缺陷修复 B：MyBatis-Plus 默认 `NOT_NULL` 更新策略导致 `setXxx(null)` 不写库。** 直接后果：`unlock()` 清 `locked_until` 无效（解锁后仍返回 42301）；同类隐患影响 MFA secret 清空、phone/email/deptId 清空。修复：SysUser 对 phone/email/deptId/mfa_secret/mfa_pending_secret/locked_until/pwd_update_time/last_login_time/last_login_ip/remark 加 `@TableField(updateStrategy = FieldStrategy.ALWAYS)`；SysRole.remark 同。
4. **前端修复：退出确认框遮罩跨路由残留。** 原实现 `await ElMessageBox.confirm()` → `await auth.logout()` → `router.push('/login')`，MainLayout 随路由卸载与 message-box overlay 关闭动画存在时序竞争，`.el-overlay.is-message-box` 在登录页仍 display:block 遮挡登录表单（硬刷新消失）。修复（MainLayout.vue）：confirm 用 try/catch 区分取消；确认后先显式 `ElMessageBox.close()`，logout 后改用 `window.location.assign('/login')` 硬跳转，彻底重置前端内存态与所有弹层。

### 14.3 验证结果摘要

- **T7 接口自测（43 项全绿，4 组 PowerShell + curl.exe）**：权限/数据范围（disp01 只见本部门 2 台车、越权单资源 40401、iam 接口 40301、组织树裁剪、多角色 dataScope 并集 total=5、角色回显、admin 停用/删除拒绝）；MFA 全链路（setup→错误码拒绝→enable→mfaRequired 登录→verify→disable 恢复，RFC6238 TOTP 脚本生成）；锁定（5 次错误 42301、锁定期正确密码亦拒、unlock 恢复）；雪花 id 响应为字符串；内置角色不可删；用户名重复/弱密码拒绝；审计 LOGIN_FAIL/解锁留痕。
- **T8 浏览器端到端**：admin 动态菜单 3 顶级目录；用户页 19 位雪花 id 完整显示无精度丢失、admin 行仅"编辑"；角色编辑回显（dataScope=5 自定义、菜单树与部门树勾选全对）、内置角色 code 与 5 档范围全禁用；个人中心 3 tab、MFA 二维码 169×169 + 32 位 Base32 密钥只读展示；受限用户 e2e01 菜单无"系统管理"、直接访问 /system/users 被路由守卫弹回 /monitor、车辆列表仅 2 台且无写按钮、组织树单节点；退出二次确认；退出遮罩修复后复测：硬跳转 /login、0 可见 overlay、token 清除、登录表单可直接交互并重新登录成功。
- 环境注意（非产品问题）：自动化视口 376×304 下，el-dropdown teleport 菜单 snapshot 会失焦关闭（需"点击后不 snapshot 直接点菜单项 ref"）；测试浏览器标签页被节流时 CSS 展开动画冻结在 scaleY(0)，新建标签页恢复；浏览器自动填充曾导致登录输入框追加旧值，type 需带 clear。

### 14.4 测试工具链备忘（PowerShell 5.1）

- byte[] 请求体不能经 `if 表达式` 包装传给 Invoke-WebRequest（数组被枚举只发首字节）；先 `[byte[]]$body=@()`，再用 splatting（变量名不可用自动变量 `$args`）。
- 无 BOM 含中文脚本按 GBK 解析会错乱：脚本纯英文，输出前设 `[Console]::OutputEncoding=[Text.Encoding]::UTF8`。
- 401 响应体 PS GetResponseStream 读不到，curl.exe 可正常读取 `{"code":40101,...}`。
