# 管理平台 M01-M04、M06-M08 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将管理端从占位骨架交付为使用真实服务端 API 的人员权限、基础资料、规则、接入、基础报表与审计平台，并完整覆盖认证、数据范围、并发冲突和失败状态。

**Architecture:** 管理端按 `auth`、`people`、`master-data`、`rules`、`integrations`、`reports`、`audit` 功能目录组织；每个目录包含类型、API 适配和页面，页面不直接调用 `fetch`。服务端返回不透明 Bearer token，前端只在内存保存 token 和当前身份，刷新后重新登录；统一请求层负责鉴权头、结构化错误和请求标识，各管理接口均由服务端重新校验 `admin` 能力。

**Tech Stack:** Vue 3.5、TypeScript 5.9、Vue Router 4.5、Element Plus 2.11、Vitest 4、Vue Test Utils、happy-dom、原生 Fetch API

**执行状态：** 本文冻结跨模块顺序、文件所有权、服务契约与验收边界。由于范围包含七个独立子系统，Task 1-3 是首个认证切片的详细计划骨架，仍须补齐完整测试与实现代码后才能执行；后续计划 4-11 是详细计划索引，同样不得直接交给编码 agent，也不得据此宣称模块已计划到可执行粒度。每个后续计划实施前应在同一目录新增 `2026-09-20-admin-<module>.md`，逐步给出可复制的完整测试和实现代码，并引用本文已冻结的接口与文件边界。

## Global Constraints

- 项目文档、页面文案、验证记录与变更说明使用中文。
- 管理端登录采用内部账号＋密码，由管理员开通；不提供公开注册、短信登录或默认公开密码。
- 业务 ID 始终为字符串；时间使用服务端 ISO 8601 字符串，页面按本地时区展示。
- 管理平台只承担管理与业务支撑；不得出现审批、合单、装货、派车、开关门、返航、确认取货、取消任务或关闭业务工单按钮。
- 页面权限只控制展示与导航，服务端仍是操作权限和仓库数据范围的最终边界。
- 角色定义与人员授权分离：M02-03 只读展示系统角色矩阵，M02-02 才编辑人员的多角色及逐角色范围；本期不创建或编辑自定义角色。
- 产品语义上报表查看、明细查看和 Excel 导出是不同能力；当前服务端契约仅有统一 `admin:boolean`，在服务端增加独立管理能力前，前端不得声称已实现三者的独立授权，见下方“契约阻塞项”。
- API 使用真实服务端数据，不增加静态表格、假成功、前端模拟保存或前端自行扩大查询范围。
- 密码、会话、九识/微信/短信密钥不写入源码、localStorage、普通日志或接口响应；敏感配置只显示掩码和配置完整性，更新时只提交用户本次输入的新值。
- `409` 版本冲突必须提示刷新后重新编辑；网络中断或超时导致结果未知时禁止显示“保存失败”并鼓励重复提交，应提供“查询实际状态”。
- 无权限或会话失效时阻断保存；`401` 转到登录并保留安全的原目标，`403` 转到第一个可访问页面。
- 列表包含加载、空、错误、分页和重复操作保护；宽表允许横向滚动，桌面最小验收宽度 1280px，窄屏 768px 不允许控件互相遮挡。
- 外部微信、短信和九识可以连接服务端 mock 适配器，但管理端必须调用真实 API；mock、编译或浏览器检查不能记为真实第三方验收。
- 本计划不执行提交、推送或合并；实施时每个任务通过 review 后，再按用户授权决定是否提交。

---

## 文件结构与接口边界

| 路径 | 职责 |
| --- | --- |
| `admin-web/src/api/request.ts` | 内存 Bearer token、请求标识、结构化错误、下载响应；不包含业务类型 |
| `admin-web/src/api/contracts.ts` | 分页、版本、角色授权、平台能力、错误详情等跨模块 DTO |
| `admin-web/src/features/auth/` | M01 会话状态、登录、账户信息与路由授权 |
| `admin-web/src/features/people/` | M02 员工列表/编辑、变更摘要、角色矩阵 |
| `admin-web/src/features/master-data/` | M03 仓库、停靠点、车辆、格口 API 与页面 |
| `admin-web/src/features/rules/` | M04 营业预约、业务参数、版本冲突与变更摘要 |
| `admin-web/src/features/integrations/` | M06 接入状态、非明文配置更新、只读状态刷新 |
| `admin-web/src/features/reports/` | M07 汇总、只读明细、独立鉴权的 Excel 导出 |
| `admin-web/src/features/audit/` | M08 审计筛选与脱敏详情；无运维控制按钮 |
| `admin-web/src/components/admin/` | 页面标题、筛选条、状态面板、变更确认、未保存离开保护 |
| `admin-web/src/router/index.ts` | 登录页、能力元数据、导航守卫和可访问首页选择 |

服务端交付契约以 `docs/api/delivery-contract.md` 为唯一接口来源。实施开始前必须逐项核对下表；若服务端文档名称不同，同步修改本计划中的 API 适配函数和 DTO 后再编码，禁止在页面层临时兼容两套字段。

| 前端函数 | 方法与路径 | 请求/返回要点 |
| --- | --- | --- |
| `login` | `POST /api/admin/auth/login` | `{ username, password }` → `{token,expiresAt,user}`，token 只存内存 |
| `getCurrentIdentity` | `GET /api/identity/me` | `Identity`，含 `admin`、`mustChangePassword`、日常角色及逐角色范围 |
| `logout` | `POST /api/identity/logout` | 幂等撤销当前会话 |
| `listEmployees` / `getEmployee` / `createEmployee` / `updateEmployee` | `/api/admin/employees...` | 标准 CRUD；更新正文带 `expectedVersion` |
| `resetCredentials` | `POST /api/admin/employees/{id}/credentials` | 设置临时密码并撤销该员工管理会话，响应不回显密码 |
| `listWarehouses` / `createWarehouse` / `updateWarehouse` | `/api/admin/warehouses...` | 标准 CRUD；停用通过 PUT `enabled:false` |
| `listStops` / `createStop` / `updateStop` | `/api/admin/stops...` | 标准 CRUD；外部点位只能绑定已有点位 |
| `listVehicles` / `createVehicle` / `updateVehicle` | `/api/admin/vehicles...` | 标准 CRUD；外部车辆名称按契约处理 |
| `listCompartments` / `createCompartment` / `updateCompartment` | `/api/admin/vehicles/{id}/compartments...` | 格口 CRUD，硬件号非空 |
| `getRules` / `updateRules` | `GET/PUT /api/admin/rules/{warehouseId}` | 营业、容量和全部业务参数合并资源，PUT 带 `expectedVersion` |
| `getIntegrationSettings` / `updateIntegrationSettings` | `GET/PUT /api/admin/integration-settings` | 读取不回显凭证；写入可带新 `credential` |
| `getIntegrationStatus` | `GET /api/admin/integration-status` | 只读接入状态，不触发车辆或短信动作 |
| `getReportSummary` / `getReportOrders` | `/api/admin/reports/summary`、`/orders` | 相同筛选口径的汇总与只读订单明细 |
| `exportReport` | `GET /api/admin/reports/orders/export` | Excel，最多 10000 行；服务端记录导出审计并防公式注入 |
| `listAuditLogs` | `GET /api/admin/audits` | `Page<History>`，配置、登录、授权、导出和接入变更 |

所有列表统一消费：

```ts
export interface PageQuery { page: number; pageSize: number }
export interface PageResult<T> { items: T[]; page: number; pageSize: number; total: number }
export interface Versioned { version: number; createdAt: string; updatedAt: string }
export type Role = 'worker' | 'warehouse' | 'dispatch' | 'overview'
export interface Grant { role: Role; scope: 'SELF' | 'WAREHOUSES' | 'ALL'; warehouseIds: string[] }
```

## 契约阻塞项

`docs/api/delivery-contract.md` 当前未提供角色定义/权限矩阵 API、分项管理权限、基础资料影响预览、外部点位候选、格口同步差异或审计单条详情接口。因此实施必须遵守以下边界：

- M02-03 只能用契约内固定 `Role` 与产品说明绘制静态“系统定义说明”，不得把它呈现为服务端实时权限矩阵；若验收要求实时矩阵，先扩充服务端只读角色定义 API。
- M02、M03、M04 的变更摘要可以用“已获取详情与当前表单”在前端计算，但占用/业务冲突必须依赖 PUT 的 `409/422 blockers`；不得假造影响检查成功。
- M03-02 无外部点位候选 API，无法完成“从实际外部点位绑定”的完整交互；M03-04 目前只有格口 CRUD，无法完成“同步资料及差异预览”。实施前需扩充契约或明确这两项降级验收。
- 当前所有 `/admin/**` 仅校验统一 `ADMIN`，不能独立限制报表查看、明细和导出。若独立授权属于本期验收，服务端需先增加明确能力字段及逐端点校验，前端随后按返回能力控制入口；仅隐藏按钮不算完成。
- 审计契约只有列表且 `History` 已含 before/after/reason；详情抽屉只能使用列表行数据。若列表裁剪字段，则需增加 `GET /api/admin/audits/{id}` 后再实现详情请求。

### Task 1: 请求层、测试环境与通用契约

**Files:**
- Modify: `admin-web/package.json`
- Modify: `admin-web/vite.config.ts`
- Modify: `admin-web/src/api/request.ts`
- Modify: `admin-web/src/api/request.test.ts`
- Create: `admin-web/src/api/contracts.ts`
- Create: `admin-web/src/test/setup.ts`

**Interfaces:**
- Consumes: 服务端统一信封 `{ code, message, data }` 和由 auth 会话仓库注入的不透明 token。
- Produces: `setAccessToken()`、`request<T>()`、`download()`、`ApiError.kind`、`PageResult<T>`、`Grant`，供全部后续任务使用。

- [ ] **Step 1: 安装页面测试依赖并配置 DOM 环境**

在 `devDependencies` 增加 `@vue/test-utils` 和 `happy-dom`，在 Vite 配置加入：

```ts
test: {
  environment: 'happy-dom',
  setupFiles: ['./src/test/setup.ts'],
  clearMocks: true,
}
```

`setup.ts` 在每个测试后恢复 mock、清空 DOM 与 sessionStorage。

- [ ] **Step 2: 先补请求行为失败测试**

在 `request.test.ts` 覆盖：设置 token 后请求携带 `Authorization: Bearer <token>`；未设置时不发送空头；`401` 分类为 `unauthorized`；`403` 为 `forbidden`；`409` 保存 `data.currentVersion`；Abort/网络错误分类为 `unknown-result`；Excel 响应读取 UTF-8 文件名且拒绝 JSON 错误信封。

- [ ] **Step 3: 实现结构化请求与下载**

```ts
export type ApiErrorKind = 'business' | 'unauthorized' | 'forbidden' | 'conflict' | 'unknown-result' | 'invalid-response'
export interface RequestOptions extends RequestInit { signal?: AbortSignal }
export function setAccessToken(token: string | null): void
export async function request<T>(path: string, init: RequestOptions = {}): Promise<T>
export async function download(path: string, init?: RequestOptions): Promise<{ blob: Blob; filename: string }>
```

生成每次调用的 `X-Request-Id`，从闭包内存读取 token 并设置 Bearer 头；不得写入 storage 或记录请求体。只有服务端明确返回失败时才归类为业务失败，连接中断、客户端超时和不可解析的成功响应均归为结果未知。

- [ ] **Step 4: 运行测试和构建**

Run: `cd admin-web && npm test -- src/api/request.test.ts && npm run build`

Expected: 请求层测试全部通过，类型检查和 Vite 构建退出码为 0。

### Task 2: M01 登录、账户会话和权限导航

**Files:**
- Create: `admin-web/src/features/auth/types.ts`
- Create: `admin-web/src/features/auth/api.ts`
- Create: `admin-web/src/features/auth/session.ts`
- Create: `admin-web/src/features/auth/session.test.ts`
- Create: `admin-web/src/features/auth/LoginView.vue`
- Create: `admin-web/src/features/auth/AccountView.vue`
- Create: `admin-web/src/components/admin/ForbiddenView.vue`
- Modify: `admin-web/src/router/index.ts`
- Modify: `admin-web/src/layouts/AdminLayout.vue`
- Modify: `admin-web/src/styles/main.css`

**Interfaces:**
- Consumes: `request<T>`、`setAccessToken()`、登录/当前身份/退出/修改密码接口。
- Produces: `Identity`、`Session`、`isAdmin()`、`ensureSession()` 和全局路由守卫。

- [ ] **Step 1: 写会话行为测试**

覆盖刷新后没有内存 token 直接进入登录、登录成功设置 token 后请求 `/identity/me`、`admin:false` 禁止进入管理页面、`mustChangePassword:true` 强制进入修改密码页、401 清空 token/身份并转 `/login?redirect=...`、403 不循环跳转、退出失败时仍清空本地身份但展示结果未知。

- [ ] **Step 2: 定义身份模型和 API**

```ts
export interface Identity {
  id: string; name: string; verifiedPhone: string | null; grants: Grant[]
  admin: boolean; mustChangePassword: boolean
}
export interface Session { token: string; expiresAt: string; user: Identity }
export function login(input: { username: string; password: string }): Promise<Session>
export function getCurrentIdentity(): Promise<Identity>
export function logout(): Promise<void>
export function changePassword(input: { currentPassword: string; newPassword: string }): Promise<void>
```

- [ ] **Step 3: 实现会话仓库和路由守卫**

token 和身份只保存在内存；禁止写入 localStorage/sessionStorage/Cookie。刷新不能恢复 token，直接返回登录。路由守卫要求 `identity.admin === true`；修改初始密码后服务端撤销全部会话，清空内存并回到登录页。当前契约没有分项管理能力，导航不得伪造细粒度授权。

- [ ] **Step 4: 实现 M01 页面**

登录表单只包含内部账号、密码和登录按钮；统一显示“账号或密码错误”，不泄露账号是否存在。账户页只读展示姓名、脱敏手机号、全部角色及各自范围，退出有二次确认；无权时显示明确说明并提供回到可访问首页。

- [ ] **Step 5: 验证 M01**

Run: `cd admin-web && npm test -- src/features/auth/session.test.ts && npm run build`

Expected: 权限导航、会话失效和退出用例通过；登录页构建产物中不存在演示密码、固定 token 或验证码字段。

### Task 3: 通用管理页面组件和编辑状态

**Files:**
- Create: `admin-web/src/components/admin/AdminPage.vue`
- Create: `admin-web/src/components/admin/FilterBar.vue`
- Create: `admin-web/src/components/admin/RequestState.vue`
- Create: `admin-web/src/components/admin/ChangePreviewDialog.vue`
- Create: `admin-web/src/components/admin/UnsavedChangesGuard.ts`
- Create: `admin-web/src/components/admin/admin-components.test.ts`
- Modify: `admin-web/src/styles/main.css`

**Interfaces:**
- Consumes: `ApiError.kind`、Element Plus 表单/表格/对话框。
- Produces: `ChangeItem { label, before, after, impact? }`、`useUnsavedChangesGuard(dirty)` 和统一 loading/empty/error/conflict/unknown-result 状态。

- [ ] **Step 1: 写组件测试**

覆盖错误态重试事件、空状态文案、保存中禁用重复提交、变更前后值展示、dirty 路由离开确认、取消离开保持输入、冲突状态只允许刷新最新版本、结果未知提供“查询实际状态”。

- [ ] **Step 2: 实现公共组件**

```ts
export interface ChangeItem { field: string; label: string; before: string; after: string; impact?: string }
export function useUnsavedChangesGuard(dirty: Ref<boolean>): { confirmDiscard: () => Promise<boolean> }
```

`RequestState` 接受 `loading | empty | error | conflict | unknown-result`，由调用页提供重试/刷新函数；不自动重发写请求。

- [ ] **Step 3: 验证组件**

Run: `cd admin-web && npm test -- src/components/admin/admin-components.test.ts`

Expected: 公共状态测试通过，无 Vue warning 或未处理 Promise rejection。

### 后续计划 4: M02 人员准入、多角色范围与只读角色矩阵

**Files:**
- Create: `admin-web/src/features/people/types.ts`
- Create: `admin-web/src/features/people/api.ts`
- Create: `admin-web/src/features/people/PeopleListView.vue`
- Create: `admin-web/src/features/people/EmployeeEditor.vue`
- Create: `admin-web/src/features/people/RoleMatrixView.vue`
- Create: `admin-web/src/features/people/people.test.ts`
- Modify: `admin-web/src/router/index.ts`
- Modify: `admin-web/src/layouts/AdminLayout.vue`

**Interfaces:**
- Consumes: `/api/admin/employees` 标准 CRUD、凭据重置接口和固定 `Role/Grant` 契约。
- Produces: 真实员工列表，新增/编辑/启停、临时凭据设置和独立 `/roles` 系统角色说明页。

- [ ] **Step 1: 写人员模块失败测试**

覆盖多角色员工一人只出现一行；编辑角色 A 不覆盖角色 B；手机号修改不设置 `verifiedPhone`；`SELF` 不能附带仓库，`WAREHOUSES` 至少一个仓库，`ALL` 不附带仓库；手机号冲突不合并；409 保留表单并要求刷新；停用确认说明保留历史；临时密码发送后不回显且表单立即清空。

- [ ] **Step 2: 定义 DTO 与 API**

```ts
export interface EmployeeGrantInput { roleCode: string; scopeType: 'SELF'|'WAREHOUSES'|'FACTORY'; warehouseIds: string[] }
export interface EmployeeSaveInput {
  name: string; phone: string; enabled: boolean; grants: Grant[]; admin: boolean; expectedVersion?: number
}
export function saveEmployee(id: string | null, input: EmployeeSaveInput): Promise<EmployeeDetail>
export function resetCredentials(id: string, input: { username: string; temporaryPassword: string }): Promise<void>
```

- [ ] **Step 3: 实现员工列表和编辑抽屉**

查询参数仅发送契约支持的 `keyword,enabled,page,pageSize`；服务端负责管理授权。新增/编辑在前端用已获取详情和表单计算姓名、手机号、角色、逐角色范围、管理员与启停差异，用户确认后调用 POST/PUT。PUT 携带 `expectedVersion`；成功才关闭，失败保留输入；内部 ID 和 `verifiedPhone` 只读。

- [ ] **Step 4: 实现角色矩阵独立页面**

左侧单选固定 `worker/warehouse/dispatch/overview`，右侧只读展示产品文档定义的页面、操作与数据范围，并明确标记为“系统定义说明”；员工授权仍从员工详情的 `grants` 展示来源。本期没有新增角色、删除角色或编辑固定能力的入口，服务端提供角色定义 API 后再改为实时矩阵。

- [ ] **Step 5: 验证 M02**

Run: `cd admin-web && npm test -- src/features/people/people.test.ts && npm run build`

Expected: 多角色、范围、冲突、权限和停用测试通过；真实 API 失败时表格不显示示例数据。

### 后续计划 5: M03 仓库与停靠点基础资料

**Files:**
- Create: `admin-web/src/features/master-data/types.ts`
- Create: `admin-web/src/features/master-data/api.ts`
- Create: `admin-web/src/features/master-data/WarehouseListView.vue`
- Create: `admin-web/src/features/master-data/StopListView.vue`
- Create: `admin-web/src/features/master-data/WarehouseEditor.vue`
- Create: `admin-web/src/features/master-data/StopEditor.vue`
- Create: `admin-web/src/features/master-data/warehouse-stop.test.ts`
- Modify: `admin-web/src/router/index.ts`

**Interfaces:**
- Consumes: `/api/admin/warehouses`、`/api/admin/stops` 标准 CRUD。
- Produces: M03-01、M03-02 页面；后续车辆、规则模块复用 `WarehouseOption`。

- [ ] **Step 1: 写仓库与点位测试**

覆盖仓库标识只读、停用显示人员/车辆/点位及在途冲突、冲突时不发送停用；点位只绑定外部候选、不允许自由地址；同步失败/绑定冲突/停用分别显示；停用不修改历史描述。

- [ ] **Step 2: 实现 API 与 DTO**

```ts
export interface WarehouseOption { id: string; name: string; enabled: boolean }
export interface WarehouseInput { name: string; code: string; enabled: boolean; expectedVersion?: number }
export interface StopInput { name: string; externalStopId: string; warehouseIds: string[]; enabled: boolean; expectedVersion?: number }
```

- [ ] **Step 3: 实现仓库和停靠点页面**

列表使用服务端分页。停用前展示当前关联字段并确认，PUT 携带 `expectedVersion`；服务端返回 `40903/422 blockers` 时展示阻塞并保持启用。当前契约不支持外部候选查询，因此不得提供自由地址或伪造候选；完整绑定交互依赖契约阻塞项解除。

- [ ] **Step 4: 验证 M03-01/02**

Run: `cd admin-web && npm test -- src/features/master-data/warehouse-stop.test.ts`

Expected: 影响检查、绑定冲突和禁止自由地址测试通过。

### 后续计划 6: M03 车辆档案与格口同步

**Files:**
- Create: `admin-web/src/features/master-data/VehicleListView.vue`
- Create: `admin-web/src/features/master-data/VehicleDetailDrawer.vue`
- Create: `admin-web/src/features/master-data/CompartmentView.vue`
- Create: `admin-web/src/features/master-data/vehicle-compartment.test.ts`
- Modify: `admin-web/src/features/master-data/types.ts`
- Modify: `admin-web/src/features/master-data/api.ts`
- Modify: `admin-web/src/router/index.ts`

**Interfaces:**
- Consumes: 车辆列表/详情/保存、归属影响检查、格口列表/同步预览/确认接口。
- Produces: M03-03、M03-04，只维护本地展示与基础映射。

- [ ] **Step 1: 写车辆与格口测试**

覆盖外部车辆 ID 和硬件能力只读；有当前任务时阻止移仓；详情没有派车/取消/控制按钮；格口差异区分新增/缺失/变化；同步必须二次确认且只刷新定义；页面无订单选择器和手工新增硬件格口。

- [ ] **Step 2: 实现车辆页面**

车辆 PUT 提交契约字段 `{ name, externalVehicleName, warehouseId, enabled, boundStopIds, expectedVersion }`；外部字段是否可写须在服务端契约收紧前由页面标记来源并二次确认。归属冲突依赖服务端 `40903/422`，状态未知不以绿色“正常”或数量 0 代替。

- [ ] **Step 3: 实现格口同步页**

```ts
export interface CompartmentDiff {
  kind: 'ADDED' | 'MISSING' | 'CHANGED'
  externalCode: string
  before: Record<string, string | boolean | null> | null
  after: Record<string, string | boolean | null> | null
}
```

先加载真实格口，CRUD 均携带 `expectedVersion`，硬件号不能为空。当前契约没有同步或差异预览端点，因此页面只能维护契约允许的格口资源；不得把重新 GET 包装成“同步成功”，同步画板在契约补齐前标为依赖阻塞。

- [ ] **Step 4: 验证 M03-03/04**

Run: `cd admin-web && npm test -- src/features/master-data/vehicle-compartment.test.ts && npm run build`

Expected: 移仓保护、同步差异和禁止业务操作测试通过。

### 后续计划 7: M04 营业预约与业务参数

**Files:**
- Create: `admin-web/src/features/rules/types.ts`
- Create: `admin-web/src/features/rules/api.ts`
- Create: `admin-web/src/features/rules/RulesView.vue`
- Create: `admin-web/src/features/rules/ScheduleRulesPanel.vue`
- Create: `admin-web/src/features/rules/BusinessParametersPanel.vue`
- Create: `admin-web/src/features/rules/rules.test.ts`
- Modify: `admin-web/src/router/index.ts`

**Interfaces:**
- Consumes: `GET/PUT /api/admin/rules/{warehouseId}` 合并规则资源和仓库选项。
- Produces: M04-01/02；明确的时间、容量、字符、分钟、秒单位。

- [ ] **Step 1: 写规则行为测试**

覆盖结束晚于开始、时间段不重叠、30 分钟粒度、容量正整数；容量减少影响已有预约时必须展示冲突且不自动取消；单位不可互换；未配置显示“未配置”；409 刷新后重填；保存失败不显示生效；结果未知查询当前版本。

- [ ] **Step 2: 定义规则类型**

```ts
export interface BusinessHour { weekday: number; start: string; end: string }
export interface RuleSet extends Versioned {
  businessHours: BusinessHour[]; slotCapacity: number; bookingDays: number
  descriptionMaxLength: number; sizeMaxLength: number; remarkMaxLength: number
  telemetryMaxAgeSeconds: number; doorMaxAgeSeconds: number
  pickupTimeoutMinutes: number; sharedCompartmentEnabled: false
}
```

- [ ] **Step 3: 实现编辑、预览和离开保护**

客户端做即时格式校验，服务端做最终业务校验。页面用当前 GET 值计算变更摘要，PUT 携带 `expectedVersion`；已有预约影响必须依赖服务端 blocker，不得假造预览或自动更改单据。dirty 状态接入通用离开保护，保存中禁用重复提交。

- [ ] **Step 4: 验证 M04**

Run: `cd admin-web && npm test -- src/features/rules/rules.test.ts && npm run build`

Expected: 校验、影响、版本冲突和结果未知测试通过。

### 后续计划 8: M06 既有服务接入与安全配置

**Files:**
- Create: `admin-web/src/features/integrations/types.ts`
- Create: `admin-web/src/features/integrations/api.ts`
- Create: `admin-web/src/features/integrations/IntegrationListView.vue`
- Create: `admin-web/src/features/integrations/IntegrationEditor.vue`
- Create: `admin-web/src/features/integrations/integrations.test.ts`
- Modify: `admin-web/src/router/index.ts`

**Interfaces:**
- Consumes: `GET/PUT /api/admin/integration-settings` 和 `GET /api/admin/integration-status`。
- Produces: M06-01，仅九识车辆 API、状态推送、普通小程序首页入口、登录验证码提供者配置。

- [ ] **Step 1: 写安全与能力边界测试**

覆盖接口响应不含明文 secret；留空敏感输入表示保持原值而非清空；新密钥提交后立即清空输入；未知/鉴权失败/超时/缺参状态分别显示；连通检查没有业务短信、派车、开门请求；无扩展接入、轨迹、自动派车开关。

- [ ] **Step 2: 定义安全 DTO**

```ts
export interface IntegrationUpdateInput {
  provider: 'simulator'|'jiushi'; baseUrl: string; organizationCode: string
  miniAppId: string; enabled: boolean; credential?: string; expectedVersion: number
}
```

读取模型只有 `credentialConfigured:boolean`，前端不得构造掩码值回填密码输入或随保存回传。只有用户输入新凭据时才带 `credential`；服务端错误详情在页面展示前按契约脱敏。

- [ ] **Step 3: 实现接入列表、编辑和检查**

设置页展示配置完整性，状态卡从 `/admin/integration-status` 展示 `connected,lastMessageAt,lastErrorCode,checkedAt`。刷新状态仅 GET，不额外设计会产生外部动作的“测试连接”；PUT 成功后重新获取无秘密详情。

- [ ] **Step 4: 验证 M06**

Run: `cd admin-web && npm test -- src/features/integrations/integrations.test.ts && npm run build`

Expected: 敏感字段和只读检查用例通过；测试快照和控制台中没有明文凭证。

### 后续计划 9: M07 报表汇总、只读明细与 Excel 导出

**Files:**
- Create: `admin-web/src/features/reports/types.ts`
- Create: `admin-web/src/features/reports/api.ts`
- Create: `admin-web/src/features/reports/ReportView.vue`
- Create: `admin-web/src/features/reports/ReportDetailDrawer.vue`
- Create: `admin-web/src/features/reports/ExportConfirmDialog.vue`
- Create: `admin-web/src/features/reports/reports.test.ts`
- Modify: `admin-web/src/router/index.ts`

**Interfaces:**
- Consumes: `/api/admin/reports/summary`、`/orders`、`/orders/export`；当前均使用统一 ADMIN 校验。
- Produces: M07-01/02，浏览器下载经过服务端授权生成的文件。

- [ ] **Step 1: 写口径和权限测试**

覆盖订单数、批次数、车辆任务数为不同指标；完成/取消/异常按服务端状态字段汇总；筛选使用单个 `warehouseId/vehicleId`；导出确认展示日期/车辆/仓库条件；超过 10000 行的 422 不下载；JSON 失败响应不下载；明细中无业务操作按钮。独立查看/明细/导出授权测试待契约能力补齐后加入，不能用前端常量伪造。

- [ ] **Step 2: 定义查询与结果类型**

```ts
export interface ReportQuery { from: string; to: string; warehouseId?: string; vehicleId?: string; status?: string }
export interface ReportSummary {
  orderCounts: Record<string, number>; batchCounts: Record<string, number>
  taskCounts: Record<string, number>; openTickets: number
}
export function exportReport(query: ReportQuery): Promise<{ blob: Blob; filename: string }>
```

- [ ] **Step 3: 实现汇总和只读明细**

只有用户点击查询才请求；汇总和分页订单明细分别调用 API。当前契约的报表明细只返回 `Order`，不得虚构批次/任务/工单履历；可展示订单已有的 `batchId,vehicleId,cancellation` 关系，详情组件不引入任何变更业务状态 API。

- [ ] **Step 4: 实现独立导出流程**

导出确认后调用 GET `/admin/reports/orders/export`；使用响应文件名创建临时下载链接并立即释放。导出错误保留筛选条件，422 提示缩小区间，结果未知提示查询审计日志且不自动重复生成。独立导出能力校验须在契约增加平台能力后落实。

- [ ] **Step 5: 验证 M07**

Run: `cd admin-web && npm test -- src/features/reports/reports.test.ts && npm run build`

Expected: 统计口径、只读边界和下载失败测试通过；三项独立权限仅在服务端能力契约补齐后计入通过。

### 后续计划 10: M08 审计日志和运维边界

**Files:**
- Create: `admin-web/src/features/audit/types.ts`
- Create: `admin-web/src/features/audit/api.ts`
- Create: `admin-web/src/features/audit/AuditLogView.vue`
- Create: `admin-web/src/features/audit/AuditDetailDrawer.vue`
- Create: `admin-web/src/features/audit/audit.test.ts`
- Modify: `admin-web/src/router/index.ts`
- Modify: `admin-web/src/layouts/AdminLayout.vue`

**Interfaces:**
- Consumes: `GET /api/admin/audits` 返回的 `Page<History>`。
- Produces: M08-02 审计查询；基础运维仍由部署、日志、监控和恢复验证文档证明。

- [ ] **Step 1: 写审计边界测试**

覆盖契约支持的 `from,to,actorId,action` 筛选；详情显示对象、前后值、原因、操作者、时间与摘要；凭证/token/验证码字段不存在；个人信息使用服务端脱敏值；页面不存在清日志、重试业务、备份、恢复或生产控制按钮。

- [ ] **Step 2: 实现审计列表和详情**

```ts
export interface History {
  id: string; objectType: string; objectId: string; action: string
  actorName: string; workspace: string; occurredAt: string; summary: string
  before: Record<string, unknown> | null; after: Record<string, unknown> | null
  reason: string | null
}
```

详情抽屉直接使用列表行的 `History`，前端只渲染服务端允许的字段，并对对象值使用安全文本渲染，不用 `v-html`。

- [ ] **Step 3: 修正导航名称和范围说明**

导航使用“审计日志”，不将其命名为可操作的“运行维护工作台”。在页面说明中链接部署/恢复验证文档位置，不伪造健康、备份或告警状态。

- [ ] **Step 4: 验证 M08**

Run: `cd admin-web && npm test -- src/features/audit/audit.test.ts && npm run build`

Expected: 筛选、脱敏、纯文本和无运维控制测试通过。

### 后续计划 11: 全模块联调、界面检查与文档同步

**Files:**
- Modify: `admin-web/README.md`
- Modify: `docs/superpowers/plans/2026-09-20-development-roadmap.md`
- Create: `docs/reviews/2026-09-20-admin-delivery-review.md`
- Create: `docs/verification/2026-09-20-admin-delivery.md`
- Delete: `admin-web/src/views/ModulePlaceholder.vue`（仅当路由已无引用）

**Interfaces:**
- Consumes: Task 1-3 与后续计划 4-10 全部页面及服务端交付契约。
- Produces: 可复核的真实 API 联调证据、视觉检查、review 与路线图状态。

- [ ] **Step 1: 增加跨模块路由测试**

验证每个能力组合的第一个可访问页面、菜单与直接 URL；会话撤销后所有模块阻断写操作；账号停用后下一请求跳登录；全厂查看权限不会扩张某个写角色的仓库范围。

- [ ] **Step 2: 运行管理端完整验证**

Run: `cd admin-web && npm test && npm run typecheck && npm run build`

Expected: 全部测试通过，TypeScript 无错误，生产构建退出码 0。

- [ ] **Step 3: 运行真实服务端联调**

启动本地服务端和管理端，用不同权限账号验证：登录/退出、员工编辑冲突、跨仓拒绝、资料停用阻塞、规则变更与服务端 blocker、接入状态刷新、报表只读与导出、审计追溯。记录实际 API、HTTP 状态、业务码、数据库变化和 mock 外部适配器边界；不得把前端 mock 测试写成联调通过。

- [ ] **Step 4: 执行界面检查**

在 1440×900、1280×720 和 768×1024 检查所有主页面：表格横向滚动、抽屉/对话框可用、长角色名/仓库名不溢出、错误与空态不重叠、键盘焦点可见、按钮保存中不位移。确认整个管理端不存在小程序日常业务按钮。

- [ ] **Step 5: 完成 review 并修复后复验**

Review 重点：权限只依赖前端、角色范围合并越权、明文秘密、409 静默覆盖、未知结果重复提交、报告查看隐含导出、管理端引入业务操作。将发现、修复和复验写入 review 文档。

- [ ] **Step 6: 更新交付文档**

README 记录启动/配置但不写凭证；验证记录逐项标注“通过/未执行/依赖阻塞”；路线图只勾选已有真实证据的项目。微信、短信、九识真实联调和目标 MySQL 8.4 若未执行，保持未完成。

## 完成条件

- M01、M02、M03、M04、M06、M07、M08 的本期页面均连接真实服务端 API，没有占位页、静态业务表格或假成功。
- 管理账号密码登录、会话失效、逐能力导航、数据范围、冲突、保存结果未知和重复提交均有自动测试及联调证据。
- 多角色授权不会相互覆盖或拼接扩大权限；角色矩阵维持只读独立范围。
- 凭证不回显、不持久化到浏览器、不进入普通日志；首次管理员初始化仍由受控服务端部署步骤完成。
- 报表汇总、只读明细、Excel 导出权限分别验证；所有业务记录页面均无日常业务操作。
- `npm test`、`npm run typecheck`、`npm run build` 通过，界面尺寸检查和 review 已留档；外部真实接入未验证项明确标为依赖阻塞。
