# MOD-SYS-001 系统配置与通知中心（F35）模块设计

> 对应 PRD：docs/html/PRD-SYS-001-系统配置与通知中心产品设计.html
> 版本 v1.0　2026-10-02

## 1. 方案总览

新建 `module-system` 模块，承载数据字典与系统参数配置（本期核心），并建表预留站内消息与对象存储元数据。字典与参数均带内存缓存，变更时主动失效；对外提供按 code 查询接口，供前端下拉与业务模块读取。

## 2. 数据库设计（09-system-config.sql，schema `traj`）

### 2.1 sys_dict_type 字典类型
| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigserial PK | |
| dict_code | varchar(64) unique | 字典编码 |
| dict_name | varchar(128) | 字典名称 |
| status | smallint | 1启用 0停用 |
| remark | varchar(255) | |
| creator/create_date/updater/update_date | 审计四字段 | |

### 2.2 sys_dict_item 字典项
| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigserial PK | |
| dict_type_id | bigint FK | |
| item_label | varchar(128) | 显示名 |
| item_value | varchar(128) | 存储值 |
| sort | int | 排序 |
| status | smallint | 1启用 0停用 |
| css_class | varchar(64) | 标签样式（可选） |
| remark | varchar(255) | |
| 审计四字段 | | |

### 2.3 sys_config 系统参数
| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigserial PK | |
| config_key | varchar(128) unique | |
| config_value | text | |
| config_name | varchar(128) | 显示名 |
| value_type | varchar(16) | STRING/INT/BOOL/JSON |
| is_system | smallint | 1内置不可删 |
| remark | varchar(255) | |
| 审计四字段 | | |

### 2.4 预留表
- `sys_message`（站内消息）：id, user_id, title, content, msg_type, is_read, create_date
- `sys_file`（文件元数据）：id, file_name, file_path, file_size, content_type, biz_type, creator, create_date

### 2.5 种子数据
- 内置字典类型 11 类 + 对应字典项（见 PRD §2.2）
- 内置参数 5 项（见 PRD §3.2），is_system=1

## 3. 后端设计（module-system）

### 3.1 模块注册
- 父 pom modules + dependencyManagement 加 module-system
- platform-boot pom 加 module-system 依赖
- @MapperScan("com.mydbd.**.mapper") 已覆盖

### 3.2 数据字典

**entity**：`DictType`、`DictItem`（traj schema）

**mapper**：`DictTypeMapper`、`DictItemMapper`（BaseMapper）

**service**：`DictService`
- 类型分页/新增/编辑/逻辑删（status=0）
- 项按 typeId 列表/新增/编辑/逻辑删
- `getItemsByCode(dictCode)`：查类型下启用项，带 Caffeine 缓存（key=dictCode，写操作时 evict）
- `getAllDictMap()`：返回 code→items 全量，供前端一次性拉取

**controller**：`DictController` `/api/system/dict`
- `GET /types` 分页（perm system:dict:view）
- `POST /types`、`PUT /types/{id}`、`DELETE /types/{id}`（perm system:dict:edit）
- `GET /types/{typeId}/items`、`POST /items`、`PUT /items/{id}`、`DELETE /items/{id}`
- `GET /items/{dictCode}` 对外查询（perm system:dict:view，无权限也可查公共字典？本期登录即可）

### 3.3 系统参数

**entity**：`SysConfig`

**mapper**：`SysConfigMapper`

**service**：`ConfigService`
- 分页/新增/编辑/删除（is_system=1 不可删）
- `getValue(key, default)` / `getInt(key, default)` / `getBool(key, default)` / `getJson(key, Class)` 带 Caffeine 缓存
- 写操作 evict 缓存

**controller**：`ConfigController` `/api/system/config`
- `GET /` 分页（perm system:config:view）
- `POST /`、`PUT /{id}`、`DELETE /{id}`（perm system:config:edit）
- `GET /value/{key}` 业务读取（登录即可，带 default 参数）

### 3.4 缓存
- 用 `com.github.benmanes.caffeine.cache.Caffeine`（Spring Boot 已带）构建本地缓存
- 字典：`Cache<String, List<DictItem>>`，key=dictCode
- 参数：`Cache<String, String>`，key=configKey
- 写操作后调用 `invalidate(key)` / `invalidateAll()`

## 4. 前端设计

### 4.1 api/system.ts
- `DictType`、`DictItem`、`SysConfig` 类型
- `getDictTypes/addDictType/...`、`getDictItems/...`
- `getDictItemsByCode(code)` 对外查询
- `getConfigs/addConfig/...`、`getConfigValue(key, def)`

### 4.2 composables/useDict.ts
```ts
const dictCache = new Map<string, DictItem[]>()
export function useDict(code: string) {
  const items = ref<DictItem[]>(dictCache.get(code) || [])
  if (!dictCache.has(code)) {
    getDictItemsByCode(code).then(list => { items.value = list; dictCache.set(code, list) })
  }
  const toMap = computed(() => Object.fromEntries(items.value.map(i => [i.itemValue, i.itemLabel])))
  return { items, toMap }
}
```

### 4.3 页面
- `views/system/DictList.vue`：左侧字典类型树/列表，右侧字典项表格（新增/编辑/停用/排序）
- `views/system/ConfigList.vue`：参数表格，编辑值（按 value_type 渲染 input/switch/textarea）

### 4.4 路由与菜单
- `/system/dict` → DictList，perm `system:dict:view`
- `/system/config` → ConfigList，perm `system:config:view`
- 菜单挂在"系统管理"分组下

## 5. 任务分解

| 任务 | 内容 |
|---|---|
| T1 | 09-system-config.sql（表/种子），父 pom + platform-boot 注册 module-system |
| T2 | module-system entity/mapper/service/controller（字典+参数），mvn compile |
| T3 | 前端 api/system.ts + useDict + DictList + ConfigList + 路由，npm build |
| T4 | 菜单 + 授权（admin 全量，SAFE_ADMIN view），重建部署 |
| T5 | 自测（§7） |
| T6 | MOD 实施记录、项目记忆更新 |

## 6. 风险与注意事项

1. **缓存一致性**：写操作必须 evict 对应 key，否则前端/业务读到旧值；字典按 code evict，参数按 key evict。
2. **内置数据保护**：is_system=1 的参数和内置字典类型不可删除（只可停用/改值）。
3. **value_type 校验**：INT 必须可转数字，BOOL 为 true/false，JSON 必须合法；编辑时校验。
4. **菜单权限**：字典/参数 view 与 edit 分离；本期 admin 全量，SAFE_ADMIN 仅 view。

## 7. 自测计划

1. 字典：类型增删改查；按 code 查项返回正确；停用类型后对外查询不返回其项；缓存写后失效。
2. 参数：增删改查；is_system 参数不可删；getValue/getInt/getBool 读取正确；JSON 类型校验。
3. 权限：无 system:dict:edit 角色调用 POST → 40301；view 角色 GET 正常。
4. 前端：字典页左右联动、新增项、排序；参数页编辑不同类型值；useDict 在下拉中渲染正确。
5. 审计：字典/参数写操作在 sys_audit_log 留痕。

## 8. 实施记录

> 实施时间：2026-10-02

### 8.1 产出物
- SQL：`infra/postgres/init/09-system-config.sql`（sys_dict_type / sys_dict_item / sys_config + 预留 sys_message / sys_file；11 类字典、41 项、5 个内置参数；菜单 920/921 + 按钮 922/923）
- 后端：`module-system`（DictType/DictItem/SysConfig entity+mapper+service+controller）
- 前端：`api/system.ts`、`composables/useDict.ts`、`views/system/DictList.vue`、`views/system/ConfigList.vue`、router 两条

### 8.2 关键实现
1. 缓存：DictService 用 `ConcurrentHashMap<String, List<DictItem>>` 按 dictCode 缓存启用项；写操作（增删改类型/项）按 typeId 反查 code 后 evict。ConfigService 同理按 configKey 缓存字符串值。
2. 字典项唯一约束：`uk_dict_item_type_value(dict_type_id, item_value)`，重复插入由 GlobalExceptionHandler 转 40901。
3. 参数校验：INT 必须为整数、BOOL 必须 true/false、JSON 必须可被 ObjectMapper 解析，违反则 40001。
4. 内置保护：is_system=1 的参数 delete 抛 40001。
5. 对外查询：`GET /api/system/dict/items/{code}`、`GET /api/system/config/value/{key}` 登录即可访问，供业务模块与前端下拉使用。

### 8.3 避坑经验
1. **容器 jar 路径**：mydbd-platform-app-1 实际启动的 jar 是 `/app/platform-app.jar`（非 `/app/app.jar`），部署必须 cp 到该路径，且容器内无 `jar` 命令，验证依赖需用 SHA 对比。
2. **首次引入新模块**：加 module-system 后首次 `mvn package` 需先 `mvn install` 全量，否则 platform-boot 打不出含 module-system 的 fat jar；且 `mvn -pl platform-boot package` 偶发 MAVEN ExecutionException，改用 `mvn install` 后再 `package` 可解决。
3. **hibernate-validator 启动失败**：首次部署新 fat jar 出现 `NoClassDefFoundError: CollectionHelper`，`mvn clean install` 重建后消失（疑似本地仓库缓存不一致）。
4. **字典项重复**：初始 SQL 缺唯一约束导致重复执行产生重复项，已加 `uk_dict_item_type_value` 并清理存量重复。

### 8.4 自测结论（全通）
- 字典：类型/项增删改查、按 code 查询、停用后缓存失效、重复项 40901。
- 参数：增删改查、INT/BOOL/JSON 校验 40001、内置参数不可删 40001、getBool 读取正确。
- 前端：字典页左右联动、新增类型/项；参数页列表与编辑弹窗；useDict 缓存。
- 浏览器验证：数据字典页与系统参数页正常渲染、新增操作成功。
