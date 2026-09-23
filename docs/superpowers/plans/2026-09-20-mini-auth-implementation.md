# 小程序服务端会话基础 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在保留阶段 1 显式本地演示模式的同时，让小程序可使用真实后端完成微信交换、手机号验证码登录、内存会话、实时权限刷新和统一业务请求。

**Architecture:** `services/request.ts` 只负责公共协议、请求头和错误转换，`features/auth/api.ts` 负责身份端点，`features/auth/session.ts` 负责登录事务与内存状态。受保护页面通过现有访问守卫在 `onShow` 拉取 `/identity/me`，权限变化时刷新工作区并清理旧缓存；公共分页与展示组件为后续业务页面提供稳定接口。

**Tech Stack:** uni-app、Vue 3、TypeScript 5.4、Node test/tsx、现有 SCSS token。

## Global Constraints

- 继续使用 `feature/base`，不提交、不推送，不覆盖现有未提交修改。
- 只修改 `miniprogram/**` 与本计划文件。
- 身份及业务 ID 均为 `string`；身份包含 `grants`、`platformGrants`、`admin`、`mustChangePassword`。
- token 仅保存在内存；请求头使用 `Authorization: Bearer <token>` 与 `X-Workspace`，不得使用 `X-Workspace-Role`。
- 创建和控制请求支持调用方传入并复用 `Idempotency-Key`；更新及动作正文保留 `expectedVersion`。
- `VITE_AUTH_MOCK=true` 才启用客户端阶段 1 演示；真实模式调用后端，微信端先调用 `uni.login`。演示验证码来自 `VITE_AUTH_MOCK_CODE`，认证 API 响应不返回验证码。
- `40101/40301/40302` 清空会话和工作区缓存；页面刷新失败保留页面已有业务数据，由页面显示错误。
- 每个新增行为遵循 RED、GREEN、REFACTOR；最终运行完整测试、类型检查、H5 和微信构建。

---

### Task 1: 公共请求协议与分页状态

**Files:**
- Create: `miniprogram/src/services/types.ts`
- Create: `miniprogram/src/services/session.ts`
- Create: `miniprogram/src/features/common/idempotency.ts`
- Create: `miniprogram/src/features/common/paged-query.ts`
- Create: `miniprogram/src/features/common/presentation.ts`
- Modify: `miniprogram/src/services/request.ts`
- Test: `miniprogram/tests/request.test.ts`
- Test: `miniprogram/tests/paged-query.test.ts`

**Interfaces:**
- Produces: `sessionToken.get/set/clear()`、`request<T>(options)`、`ApiRequestError.data`、`newIdempotencyKey()`、`PagedQuery<T>`、`Page<T>`。
- `ApiRequestOptions` accepts `{path,method?,data?,header?,workspace?:Role|false,idempotencyKey?}`。

- [x] **Step 1: RED：锁定请求头、错误数据、失权回调与分页去重**

在 `request.test.ts` 注入 `globalThis.uni.request`，断言 token、当前工作区和幂等键被加入请求，调用方自定义 header 得以保留；断言 `{code:40902,data:{currentVersion:3}}` 转换成保留 data 的 `ApiRequestError`；断言 `40101/40301/40302` 触发一次会话失效处理。`paged-query.test.ts` 断言刷新替换、追加按字符串 id 去重、追加失败保留旧 items、reset 清空状态。

- [x] **Step 2: 运行 RED**

Run: `cd miniprogram && npm test -- --test-name-pattern='请求头|错误数据|失权|分页'`

Expected: FAIL，新增模块或导出尚不存在。

- [x] **Step 3: GREEN：实现公共协议**

`services/types.ts` 定义 `Page<T>`、`ApiErrorData`、`ApiEnvelope<T>`；`services/session.ts` 保存模块内 token，并允许注册单一失效处理器。`request.ts` 合并 header 后注入 Bearer、`X-Workspace`、`Idempotency-Key`，保留错误 data，并在细分失权码上调用处理器。`PagedQuery<T>` 暴露 `items/loading/loadingMore/error/hasMore/replace/append/fail/reset`。

- [x] **Step 4: 验证 GREEN**

Run: `cd miniprogram && npm test -- --test-name-pattern='请求头|错误数据|失权|分页'`

Expected: PASS。

### Task 2: 真实认证、登录事务和实时权限刷新

**Files:**
- Create: `miniprogram/src/features/auth/api.ts`
- Create: `miniprogram/src/features/auth/model.ts`
- Modify: `miniprogram/src/features/auth/fixtures.ts`
- Modify: `miniprogram/src/features/auth/mock.ts`
- Modify: `miniprogram/src/features/auth/session.ts`
- Modify: `miniprogram/src/features/workspace/model.ts`
- Modify: `miniprogram/src/features/workspace/navigation.ts`
- Modify: `miniprogram/src/pages/login/index.vue`
- Modify: `miniprogram/src/pages/phone/index.vue`
- Modify: `miniprogram/src/pages/profile/index.vue`
- Modify: `miniprogram/src/env.d.ts`
- Modify: `miniprogram/.env.example`
- Test: `miniprogram/tests/auth-api.test.ts`
- Test: `miniprogram/tests/auth-session.test.ts`
- Test: `miniprogram/tests/auth.test.ts`
- Test: `miniprogram/tests/workspace.test.ts`

**Interfaces:**
- Produces: `exchangeWechat(code): Promise<WechatExchange>`、`requestSms(phone,purpose,bindingToken?): Promise<SmsChallenge>`、`verifySms(input): Promise<Session>`、`fetchIdentity(): Promise<Identity>`、`logout(): Promise<void>`。
- Produces: `startWechatLogin(): Promise<'AUTHENTICATED'|'PHONE_REQUIRED'>`、`sendCode(phone,purpose)`、`signIn(phone,code,purpose,isActive)`、`refreshIdentity()`、`signOut()`。

- [x] **Step 1: RED：锁定 API 请求体与真实微信交换**

断言微信交换发送 `{code}`；短信发送包含 `purpose:'LOGIN'|'BIND'` 和可选 `bindingToken`；验证发送 `challengeId/phone/code/bindingToken`；`GET /identity/me` 与 logout 使用公共请求。真实微信登录断言调用 `uni.login`，不得生成员工 ID 或用随机身份完成登录。

- [x] **Step 2: 运行 RED**

Run: `cd miniprogram && npm test -- --test-name-pattern='微信交换|短信 challenge|真实微信'`

Expected: FAIL，认证 API/真实适配尚不存在。

- [x] **Step 3: GREEN：实现身份类型、API 与显式演示配置**

Identity 使用 `verifiedPhone:string|null`、`grants:Grant[]`、`platformGrants:PlatformGrant[]`。真实模式经 API；`VITE_AUTH_MOCK=true` 才使用 fixture，模拟验证码读取 `VITE_AUTH_MOCK_CODE`，页面仅在显式演示模式显示该配置值。

- [x] **Step 4: RED：锁定取消竞态、实时撤权和退出行为**

断言 verify 返回后若页面已卸载，不写入 token/currentUser/workspace；刷新 me 更新 scope 并清工作区 cache，当前角色被撤销时进入工作区选择，全部撤权清会话；logout 只有确认后调用，服务端失败也完成本地清理，重复清理不循环 reLaunch。

- [x] **Step 5: 运行 RED**

Run: `cd miniprogram && npm test -- --test-name-pattern='取消登录|实时撤权|退出'`

Expected: FAIL，事务和刷新逻辑尚未实现。

- [x] **Step 6: GREEN：实现会话事务、onShow 刷新与页面接入**

登录成功先检查操作代次与 `isActive()`，再一次性写 token、Identity 和 workspace。守卫以共享 in-flight Promise 防止同一页面树重复 `/identity/me`；失败只在明确失权码清会话，普通网络失败由调用页显示且不丢旧数据。登录页按微信交换结果直接进入或跳手机号绑定；手机号页保存 challengeId 并按 LOGIN/BIND 验证；profile 使用 verifiedPhone 并显示 API scope 的中文映射。

- [x] **Step 7: 验证 GREEN**

Run: `cd miniprogram && npm test -- --test-name-pattern='微信交换|短信 challenge|真实微信|取消登录|实时撤权|退出'`

Expected: PASS。

### Task 3: 通用业务页面、四态和分页组件

**Files:**
- Create: `miniprogram/src/components/BusinessPage.vue`
- Create: `miniprogram/src/components/PagedListState.vue`
- Create: `miniprogram/src/components/TimelineList.vue`
- Create: `miniprogram/src/components/ActionBar.vue`
- Create: `miniprogram/src/components/ConditionRow.vue`
- Create: `miniprogram/src/components/VehicleCard.vue`
- Create: `miniprogram/src/components/CompartmentCard.vue`
- Modify: `miniprogram/src/components/AppPage.vue`
- Modify: `miniprogram/src/styles/tokens.scss`
- Test: `miniprogram/tests/presentation.test.ts`

**Interfaces:**
- Produces: `displayMetric`、`doorStatusText`、`requestStatusText`；详情页统一返回/标题容器；分页组件接收 `loading/loadingMore/error/hasContent/hasMore/emptyText`。

- [x] **Step 1: RED：锁定未知值与请求状态文案**

断言 null 指标为 `—`、UNKNOWN 门状态为“状态待核对”、ACCEPTED 请求为“请求已受理”、UNKNOWN 请求为“结果待核对”。

- [x] **Step 2: 运行 RED**

Run: `cd miniprogram && npm test -- --test-name-pattern='未知值|请求状态'`

Expected: FAIL，展示映射尚不存在。

- [x] **Step 3: GREEN：实现详情导航、四态、分页和业务展示组件**

`BusinessPage` 复用 `AppPage` 并提供返回按钮；`PagedListState` 在首屏分别显示 loading/error/empty/content，加载更多失败时继续渲染 slot；`ActionBar` 留出安全区且正文获得对应底部空间；时间线、条件、车辆与格口组件不把 null/UNKNOWN 显示成正常或零值。

- [x] **Step 4: 验证 GREEN 与完整门禁**

Run: `cd miniprogram && npm test && npm run typecheck && npm run build:h5 && npm run build:mp-weixin`

Expected: 所有命令退出码 0。

### Task 4: 自查与交付记录

**Files:**
- Modify: `docs/superpowers/plans/2026-09-20-mini-auth-implementation.md`
- Create outside repository by requirement: `/tmp/task-hub-mini-auth-report.md`

- [x] **Step 1: 对照契约检查 header、Identity 字段、LOGIN/BIND、challengeId、mock 边界、竞态和失权清理。**
- [x] **Step 2: 运行 `git diff --check` 与 owned-files diff，确认未改服务端和 root 文档。**
- [x] **Step 3: 报告服务层函数签名、RED/GREEN 证据、完整验证结果、真实凭证/真机未执行项及 review 注意事项。**
