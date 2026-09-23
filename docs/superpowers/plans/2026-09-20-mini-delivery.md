# 小程序完整业务交付 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有阶段 1 基础上实现 P03–P17、P19/P20、P22–P29、P33/P34/P40，并通过真实后端/MySQL 与服务端车辆模拟器跑通完整业务闭环。

**Architecture:** 页面只编排展示和交互，所有请求进入 `src/services/`，业务类型与纯逻辑进入 `src/features/`。客户端使用内存 opaque Bearer 会话、当前 `X-Workspace`、字符串 ID、`expectedVersion` 和可复用幂等键；后端是权限、范围、状态、并发和控制条件的最终边界。

**Tech Stack:** uni-app、Vue 3、TypeScript 5.4、Node test/tsx、现有 SCSS token；接口基线为 `docs/api/delivery-contract.md`。

## Global Constraints

- 继续使用 `feature/base`，不新建分支；不覆盖当前未提交修改。
- 文档和界面文案使用中文；实现每组页面前按 `docs/design/figma-miniprogram.md` 读取对应 Figma 节点。
- 业务数据只能来自服务端 MySQL；客户端 mock 仅保留阶段 1 演示，微信/短信/车辆模拟只能由服务端 local/test provider 提供。
- 所有 ID 为 `string`；响应 `{code,message,data}`；分页 `{items,page,pageSize,total}`；日期时间采用带偏移 ISO-8601。
- 会话只存内存；请求发送 opaque `Authorization: Bearer` 与 `X-Workspace`。创建/控制使用 `Idempotency-Key`，更新/动作使用 `expectedVersion`。
- 取消状态独立于订单主状态；请求受理独立于真实门/车状态；取货只允许指定本人；继续出发必须由服务端返回全部条件满足。
- 每个页面覆盖 loading、empty、error/content；写操作覆盖 busy、失败保留输入、重复点击；列表覆盖分页；`40101/40301/40302` 清理会话和旧工作区数据。
- 不增加地图、轨迹、ETA、远程关门、返航、订阅通知、任意地址、代取、自动派车或业务短信。
- 每项实现前确认对应后端接口已可联调；缺少真实外部资源时只验服务端模拟闭环并记录阻塞。

---

## 文件结构

新增共享文件：`src/services/{types,session}.ts`、`src/features/common/{idempotency,paged-query,presentation}.ts`、业务通用组件。每个业务域使用 `src/features/<domain>/{model,api}.ts`；页面按 `pages/` 与四个 `subpackages/` 归属放置。测试按域置于 `miniprogram/tests/*.test.ts`，每个任务先增加纯逻辑失败测试，再实现最小可用切片。

### Task 1: 统一会话、请求协议和分页状态

**Files:**
- Create: `miniprogram/src/services/types.ts`
- Create: `miniprogram/src/services/session.ts`
- Create: `miniprogram/src/features/common/idempotency.ts`
- Create: `miniprogram/src/features/common/paged-query.ts`
- Create: `miniprogram/src/features/common/presentation.ts`
- Modify: `miniprogram/src/services/request.ts`
- Modify: `miniprogram/src/features/auth/session.ts`
- Test: `miniprogram/tests/request.test.ts`
- Test: `miniprogram/tests/paged-query.test.ts`

**Interfaces:**
- Consumes: `workspace.activeRole`，后端公共协议和错误码。
- Produces: `request<T>(options: ApiRequestOptions): Promise<T>`、`sessionToken.set/clear/get()`、`newIdempotencyKey(): string`、`PagedQuery<T>`、`Page<T>`、`ApiRequestError.data`。

- [ ] **Step 1: RED：写请求头、错误和分页行为测试**

```ts
test('业务请求附加内存 token、工作区和幂等键', async () => {
  sessionToken.set('opaque-token')
  workspace.refresh('u1', [worker], 'worker')
  await request({ path: '/orders', method: 'POST', data: {}, idempotencyKey: 'k1' })
  assert.deepEqual(lastRequest.header, {
    Authorization: 'Bearer opaque-token',
    'X-Workspace': 'worker',
    'Idempotency-Key': 'k1',
  })
})

test('刷新替换、下一页追加且按 id 去重', () => {
  const state = new PagedQuery<{id:string}>()
  state.replace({ items: [{id:'1'}], page:1, pageSize:20, total:2 })
  state.append({ items: [{id:'1'}, {id:'2'}], page:2, pageSize:20, total:2 })
  assert.deepEqual(state.items.map(item => item.id), ['1', '2'])
})
```

- [ ] **Step 2: 运行 RED**

Run: `cd miniprogram && npm test -- --test-name-pattern='业务请求|刷新替换'`

Expected: FAIL，模块或导出尚不存在。

- [ ] **Step 3: CODE：实现公共协议和请求注入**

```ts
export interface Page<T> { items:T[]; page:number; pageSize:number; total:number }
export interface ApiErrorData { fieldErrors?:Record<string,string>; blockers?:{code:string;message:string}[]; currentVersion?:number }

let token: string | null = null
export const sessionToken = { get: () => token, set: (value:string) => { token = value }, clear: () => { token = null } }

export function newIdempotencyKey() {
  return globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`
}
```

扩展 `ApiRequestOptions` 为 `idempotencyKey?: string; workspace?: Role | false`，在 `request` 内合并调用方 header 后注入认证头；捕获 `40101/40301/40302` 时调用单一 `invalidateSession()` 清 token、当前用户和工作区缓存并 `reLaunch`。`PagedQuery` 暴露 `loading/error/items/hasMore/replace/append/reset`，失败追加不清空旧 items。

- [ ] **Step 4: GREEN：验证公共层**

Run: `cd miniprogram && npm test && npm run typecheck`

Expected: 全部测试 PASS，类型检查退出码 0。

- [ ] **Step 5: Commit checkpoint**

```bash
git add miniprogram/src/services miniprogram/src/features/common miniprogram/src/features/auth/session.ts miniprogram/tests
git commit -m "feat(mini): unify authenticated request state"
```

### Task 2: 业务页面壳与通用状态组件

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
- Consumes: Task 1 `presentation` 格式化函数和现有 `BaseButton/FormField/StatePanel/StatusTag/ConfirmDialog`。
- Produces: 统一详情导航、列表四态、固定操作栏、时间线、条件和车辆/格口展示组件。

- [ ] **Step 1: RED：锁定未知值和状态语义**

```ts
test('未知车辆值不伪装为零或正常', () => {
  assert.equal(displayMetric(null, '米'), '—')
  assert.equal(doorStatusText('UNKNOWN'), '状态待核对')
  assert.equal(requestStatusText('ACCEPTED'), '请求已受理')
  assert.equal(requestStatusText('UNKNOWN'), '结果待核对')
})
```

- [ ] **Step 2: 运行 RED**

Run: `cd miniprogram && npm test -- --test-name-pattern='未知车辆值'`

Expected: FAIL，展示映射未实现。

- [ ] **Step 3: CODE：实现组件固定契约**

```vue
<!-- PagedListState.vue -->
<template>
  <StatePanel v-if="loading && !hasContent" state="loading" title="" description="" />
  <StatePanel v-else-if="error && !hasContent" state="error" title="加载失败" :description="error" :retry="true" @retry="$emit('retry')" />
  <StatePanel v-else-if="!hasContent" title="暂无数据" :description="emptyText" />
  <slot v-else />
  <BaseButton v-if="hasContent && hasMore" :busy="loadingMore" variant="plain" @click="$emit('more')">加载更多</BaseButton>
</template>
```

`ActionBar` 使用 `position: fixed` 与安全区 padding，页面自动补足底部空间；`ConditionRow` 同时显示图标、文本状态和阻断说明；车辆、格口组件对 null/UNKNOWN 使用“—/待核对”。更新 token 增加成功、提醒、危险、分隔线颜色，不改变已核实主色。

- [ ] **Step 4: GREEN：测试、类型与双端构建**

Run: `cd miniprogram && npm test && npm run typecheck && npm run build:h5 && npm run build:mp-weixin`

Expected: 全部退出码 0；320px/390px 手工检查固定操作栏不遮挡正文且无横向滚动。

- [ ] **Step 5: Commit checkpoint**

```bash
git add miniprogram/src/components miniprogram/src/styles miniprogram/tests/presentation.test.ts
git commit -m "feat(mini): add business state components"
```

### Task 3: 目录、时段与常用停靠点（P04/P05/P19）

**Files:**
- Create: `miniprogram/src/features/catalog/model.ts`
- Create: `miniprogram/src/features/catalog/api.ts`
- Create: `miniprogram/src/subpackages/worker/destination.vue`
- Create: `miniprogram/src/subpackages/worker/slot.vue`
- Create: `miniprogram/src/subpackages/worker/favorites.vue`
- Modify: `miniprogram/src/pages.json`
- Modify: `miniprogram/src/pages/profile/index.vue`
- Test: `miniprogram/tests/catalog.test.ts`

**Interfaces:**
- Consumes: `GET /catalog/warehouses|stops|slots|rules`、`GET/PUT/DELETE /favorites`。
- Produces: `CatalogStop`、`BookingSlot`、`listAvailableStops(warehouseId)`、`listSlots(...)`、`setFavorite(stopId,favorite,key)`；选择页通过 event channel 返回字符串 ID 和显示值。

- [ ] **Step 1: RED：测试时段和收藏规则**

```ts
test('只能确认服务端标记可用的未来时段', () => {
  assert.equal(canSelectSlot({ available:true, start:'2026-09-21T14:00:00+08:00' }, now), true)
  assert.equal(canSelectSlot({ available:false, start:'2026-09-21T14:00:00+08:00' }, now), false)
})
test('停用收藏仍展示但不能选为目的地', () => {
  assert.deepEqual(stopPresentation({ favorite:true, enabled:false }), { label:'不可用', selectable:false })
})
```

- [ ] **Step 2: 运行 RED**

Run: `cd miniprogram && npm test -- --test-name-pattern='时段|停用收藏'`

Expected: FAIL，catalog 模块不存在。

- [ ] **Step 3: CODE：实现 API 和三页交互**

```ts
export const catalogApi = {
  stops: (warehouseId:string) => request<CatalogStop[]>({ path:`/catalog/stops?warehouseId=${encodeURIComponent(warehouseId)}` }),
  slots: (warehouseId:string, stopId:string, date:string) => request<BookingSlot[]>({ path:`/catalog/slots?warehouseId=${warehouseId}&stopId=${stopId}&date=${date}` }),
  favorite: (stopId:string, selected:boolean, key:string) => request<null>({ path:`/favorites/${stopId}`, method:selected?'PUT':'DELETE', idempotencyKey:key }),
}
```

目的地页合并常用与可用点位，空状态禁止手输；时段页先选日期并展示 available/满额/过期；收藏页添加和移除均局部 busy，失败保留列表和当前选择。实现前读取 Figma P04 `27:240`、P05 `27:265`、P19 `39:281`。

- [ ] **Step 4: GREEN：验证目录切片**

Run: `cd miniprogram && npm test && npm run typecheck && npm run build:mp-weixin`

Expected: PASS；手工验证无点位、无时段、分页/失败、收藏停用和重复点击。

- [ ] **Step 5: Commit checkpoint**

```bash
git add miniprogram/src/features/catalog miniprogram/src/subpackages/worker miniprogram/src/pages/profile/index.vue miniprogram/src/pages.json miniprogram/tests/catalog.test.ts
git commit -m "feat(mini): add stops slots and favorites"
```

### Task 4: 工人申请、订单和取消（P03/P06–P09）

**Files:**
- Create: `miniprogram/src/features/order/model.ts`
- Create: `miniprogram/src/features/order/api.ts`
- Create: `miniprogram/src/features/order/form.ts`
- Replace: `miniprogram/src/subpackages/worker/index.vue`
- Create: `miniprogram/src/subpackages/worker/result.vue`
- Create: `miniprogram/src/subpackages/worker/orders.vue`
- Create: `miniprogram/src/subpackages/worker/order-detail.vue`
- Modify: `miniprogram/src/pages.json`
- Test: `miniprogram/tests/order.test.ts`

**Interfaces:**
- Consumes: `POST/GET /orders`、`GET /orders/{id}`、`POST /orders/{id}/cancellations`，Task 3 点位/时段选择。
- Produces: `Order`、`OrderDraft`、`validateOrderDraft()`、`orderStatusText()`、`createOrder(draft,key)`、`requestCancellation(id,version,reason,key)`。

- [ ] **Step 1: RED：测试表单、状态和取消分离**

```ts
test('取消处理中不覆盖订单主状态', () => {
  const vm = presentOrder({ status:'ACCEPTED', cancellation:{status:'PENDING'} } as Order)
  assert.equal(vm.primaryStatus, '已受理')
  assert.equal(vm.badges.includes('取消处理中'), true)
})
test('申请校验保留未绑定接收人但拒绝格式错误', () => {
  assert.deepEqual(validateOrderDraft(validDraft), {})
  assert.equal(validateOrderDraft({...validDraft, receiverPhone:'12'}).receiverPhone, '请输入正确手机号')
})
```

- [ ] **Step 2: 运行 RED**

Run: `cd miniprogram && npm test -- --test-name-pattern='取消处理中|申请校验'`

Expected: FAIL，order 模型未定义。

- [ ] **Step 3: CODE：实现申请与订单 API**

```ts
export function createOrder(input:CreateOrderInput, key:string) {
  return request<Order>({ path:'/orders', method:'POST', data:input, idempotencyKey:key })
}
export function requestCancellation(order:Order, reason:string, key:string) {
  return request<Order>({ path:`/orders/${order.id}/cancellations`, method:'POST', data:{expectedVersion:order.version,reason}, idempotencyKey:key })
}
```

申请页用一个 key 覆盖一次确认和网络重试；`42201` 只清时段并保留其余草稿。订单列表按服务端分页，关系/状态筛选转换为明确查询参数；详情按 `allowedActions` 呈现取消/扫码/反馈入口。提交取消后显示原主状态与“取消处理中”。实现前读取 P03 `18:123`、P06 `27:287`、P07 `18:171`、P08 `18:221`、P09 `27:305`。

- [ ] **Step 4: GREEN：订单切片联调**

Run: `cd miniprogram && npm test && npm run typecheck && npm run build:h5 && npm run build:mp-weixin`

Expected: PASS；真实后端/MySQL 验证创建后重启客户端仍可查询，同 key 不重复建单，失败保留输入，取消待处理不变主状态。

- [ ] **Step 5: Commit checkpoint**

```bash
git add miniprogram/src/features/order miniprogram/src/subpackages/worker miniprogram/src/pages.json miniprogram/tests/order.test.ts
git commit -m "feat(mini): add worker orders and cancellation"
```

### Task 5: 仓库审批和批次编辑（P22–P24）

**Files:**
- Create: `miniprogram/src/features/batch/model.ts`
- Create: `miniprogram/src/features/batch/api.ts`
- Replace: `miniprogram/src/subpackages/warehouse/index.vue`
- Create: `miniprogram/src/subpackages/warehouse/order-review.vue`
- Create: `miniprogram/src/subpackages/warehouse/batches.vue`
- Create: `miniprogram/src/subpackages/warehouse/batch-edit.vue`
- Modify: `miniprogram/src/pages.json`
- Test: `miniprogram/tests/batch.test.ts`

**Interfaces:**
- Consumes: `GET /orders`、订单 review/cancellation review、`GET/POST /batches`、`GET /batches/{id}`、`PUT /batches/{id}/orders`。
- Produces: `Batch`、`sameBatchGroup(orders)`、`reviewOrder(...)`、`reviewCancellation(...)`、`createBatch(...)`、`replaceBatchOrders(...)`。

- [ ] **Step 1: RED：批次分组和并发刷新**

```ts
test('批次成员必须同仓同点同时段', () => {
  assert.equal(sameBatchGroup([a, {...a,id:'2'}]), true)
  assert.equal(sameBatchGroup([a, {...a,id:'2',stopId:'other'}]), false)
})
test('驳回必须填写原因', () => assert.equal(validateReview('REJECT','  '), '请填写驳回原因'))
```

- [ ] **Step 2: 运行 RED**

Run: `cd miniprogram && npm test -- --test-name-pattern='批次成员|驳回必须'`

Expected: FAIL。

- [ ] **Step 3: CODE：实现审批与全量成员更新**

```ts
export function replaceBatchOrders(batch:Batch, orderIds:string[], key:string) {
  return request<Batch>({ path:`/batches/${batch.id}/orders`, method:'PUT', data:{expectedVersion:batch.version,orderIds}, idempotencyKey:key })
}
```

工作台分页按预约时间显示；多选仅允许已受理且未占用订单。`40902/40903` 保留筛选、退出多选并刷新对象。取消批准后读取新订单和批次，明确要求重新装货；已派发订单显示人工核对，不暗示整车取消。实现前读取 P22 `46:212`、P23 `46:239` 及 `47:219/236/252/266/287`、P24 `46:263/47:414`。

- [ ] **Step 4: GREEN：仓库审批联调**

Run: `cd miniprogram && npm test && npm run typecheck && npm run build:mp-weixin`

Expected: PASS；双客户端同时受理/占用仅一次成功，冲突端刷新为最新状态。

- [ ] **Step 5: Commit checkpoint**

```bash
git add miniprogram/src/features/batch miniprogram/src/subpackages/warehouse miniprogram/src/pages.json miniprogram/tests/batch.test.ts
git commit -m "feat(mini): add warehouse review and batches"
```

### Task 6: 装货、格口和选车派发（P25/P26）

**Files:**
- Create: `miniprogram/src/features/dispatch/model.ts`
- Create: `miniprogram/src/features/dispatch/api.ts`
- Create: `miniprogram/src/subpackages/warehouse/loading.vue`
- Create: `miniprogram/src/subpackages/dispatch/vehicle-select.vue`
- Create: `miniprogram/src/subpackages/dispatch/dispatch-confirm.vue`
- Modify: `miniprogram/src/pages.json`
- Test: `miniprogram/tests/loading-dispatch.test.ts`

**Interfaces:**
- Consumes: vehicles、batch loading/open/confirm、dispatch 与 reconcile 接口。
- Produces: `Vehicle/DispatchRequest/ControlRequest`、`validateAssignments()`、`saveLoading()`、`confirmLoading()`、`dispatchBatch()`、`reconcileDispatch()`。

- [ ] **Step 1: RED：格口完整性和请求/实态分离**

```ts
test('每个订单必须分配有效且非空格口', () => {
  assert.deepEqual(validateAssignments(['o1','o2'], [{orderId:'o1',compartmentIds:['c1']}]), ['o2'])
})
test('派发受理不等于车辆运行', () => {
  assert.equal(dispatchPresentation({status:'ACCEPTED',taskId:null}), '任务规划中')
  assert.equal(dispatchPresentation({status:'UNKNOWN',taskId:null}), '派发待核对')
})
```

- [ ] **Step 2: 运行 RED**

Run: `cd miniprogram && npm test -- --test-name-pattern='格口|派发受理'`

Expected: FAIL。

- [ ] **Step 3: CODE：实现装货和派发动作**

```ts
export function dispatchBatch(batch:Batch, key:string) {
  return request<DispatchRequest>({ path:`/batches/${batch.id}/dispatch`, method:'POST', data:{expectedVersion:batch.version}, idempotencyKey:key })
}
export function reconcileDispatch(id:string) {
  return request<DispatchRequest>({ path:`/dispatch-requests/${id}/reconcile`, method:'POST' })
}
```

换车立即清空 assignments；容量由仓库勾选人工确认。开装货格口只提交已分配格口，`ACCEPTED` 后继续读取车辆门状态；`UNKNOWN` 只允许核对。车辆离线、过期、非空闲或有任务不可选并显示原因。实现前读取 P25 `46:286/47:302/319`、P26 `46:312/47:341/364/381/397`。

- [ ] **Step 4: GREEN：装货派发联调**

Run: `cd miniprogram && npm test && npm run typecheck && npm run build:h5 && npm run build:mp-weixin`

Expected: PASS；模拟器覆盖 accepted/failed/timeout，timeout 显示待核对且没有第二次派发。

- [ ] **Step 5: Commit checkpoint**

```bash
git add miniprogram/src/features/dispatch miniprogram/src/subpackages/warehouse/loading.vue miniprogram/src/subpackages/dispatch miniprogram/src/pages.json miniprogram/tests/loading-dispatch.test.ts
git commit -m "feat(mini): add loading and dispatch flow"
```

### Task 7: 调度台、车辆详情和控制（P27–P29）

**Files:**
- Replace: `miniprogram/src/subpackages/dispatch/index.vue`
- Create: `miniprogram/src/subpackages/dispatch/task-detail.vue`
- Create: `miniprogram/src/subpackages/dispatch/vehicles.vue`
- Create: `miniprogram/src/subpackages/dispatch/vehicle-detail.vue`
- Create: `miniprogram/src/subpackages/dispatch/control.vue`
- Modify: `miniprogram/src/features/dispatch/model.ts`
- Modify: `miniprogram/src/features/dispatch/api.ts`
- Modify: `miniprogram/src/pages.json`
- Test: `miniprogram/tests/vehicle-control.test.ts`

**Interfaces:**
- Consumes: vehicles/tasks、open/go/cancel、control request reconcile。
- Produces: `controlEligibility(vehicle,action)`、`openCompartments()`、`continueTask()`、`cancelTask()`、`reconcileControl()`。

- [ ] **Step 1: RED：取消与控制阻断测试**

```ts
test('速度未知、非零或状态过期均禁止取消任务', () => {
  assert.equal(canCancel({...vehicle,speed:null}), false)
  assert.equal(canCancel({...vehicle,speed:0.1}), false)
  assert.equal(canCancel({...vehicle,speed:0,fresh:false}), false)
})
test('UNKNOWN 控制只允许核对', () => assert.deepEqual(controlActions({status:'UNKNOWN'} as ControlRequest), ['RECONCILE']))
```

- [ ] **Step 2: 运行 RED**

Run: `cd miniprogram && npm test -- --test-name-pattern='取消任务|UNKNOWN 控制'`

Expected: FAIL。

- [ ] **Step 3: CODE：实现控制请求**

```ts
export function cancelTask(vehicle:Vehicle, taskId:string, reason:string, key:string) {
  return request<ControlRequest>({ path:`/vehicles/${vehicle.id}/controls/cancel`, method:'POST', data:{taskId,reason}, idempotencyKey:key })
}
```

详情每次 `onShow` 读取持久化快照，不自行高于 1Hz 轮询；离线、连接异常、过期分别展示。控制页按 `allowedActions/blockers` 呈现开门/继续出发/取消；开门必须明确选择任务内格口；取消二次确认列受影响批次/订单且说明订单另行处理。无远程关门/返航。实现前读取 P27 `51:241/273`、P28 `51:299/330` 与 `52:250/267/283`、P29 `51:353`、`52:299–425`、`55:321`、`56:326`。

- [ ] **Step 4: GREEN：车辆控制验证**

Run: `cd miniprogram && npm test && npm run typecheck && npm run build:mp-weixin`

Expected: PASS；模拟器验证失败、未知、旧事件和重复事件不伪造成功、不覆盖新状态。

- [ ] **Step 5: Commit checkpoint**

```bash
git add miniprogram/src/features/dispatch miniprogram/src/subpackages/dispatch miniprogram/src/pages.json miniprogram/tests/vehicle-control.test.ts
git commit -m "feat(mini): add dispatch console and controls"
```

### Task 8: 工人扫码、开门、取货与继续出发（P10–P12）

**Files:**
- Create: `miniprogram/src/features/order/pickup.ts`
- Create: `miniprogram/src/subpackages/worker/scan-result.vue`
- Create: `miniprogram/src/subpackages/worker/pickup.vue`
- Create: `miniprogram/src/subpackages/worker/continue.vue`
- Modify: `miniprogram/src/features/order/api.ts`
- Modify: `miniprogram/src/pages.json`
- Test: `miniprogram/tests/pickup.test.ts`

**Interfaces:**
- Consumes: `/pickup/scan`、order pickup open/confirm、vehicle go、control request read/reconcile。
- Produces: `scanVehicle()`、`openPickup()`、`confirmPickup()`、`continueVehicle()`、`ContinueCheckView`。

- [ ] **Step 1: RED：本人取货和继续出发条件**

```ts
test('扫码仅呈现服务端返回的本人可取订单', () => {
  assert.deepEqual(presentScan({orders:[mine],canContinue:false,blockers:[]}).orderIds, [mine.id])
})
test('任一 blocker 都禁用继续出发并保留本人完成状态', () => {
  const vm = presentContinue({canContinue:false,blockers:[{code:'ORDERS_PENDING',message:'本站还有订单未完成取货'}]})
  assert.equal(vm.enabled, false)
  assert.equal(vm.reasons[0], '本站还有订单未完成取货')
})
```

- [ ] **Step 2: 运行 RED**

Run: `cd miniprogram && npm test -- --test-name-pattern='扫码仅|任一 blocker'`

Expected: FAIL。

- [ ] **Step 3: CODE：实现扫码与逐单取货**

```ts
export function confirmPickup(order:Order, key:string) {
  return request<Order>({ path:`/orders/${order.id}/pickup/confirm`, method:'POST', data:{expectedVersion:order.version}, idempotencyKey:key })
}
```

使用 `uni.scanCode`，取消不改变页面；相机拒绝显示设置提示。扫码结果不授予权限，只展示服务端订单。开门页分别显示请求状态和门状态，格口未知禁止操作；确认取货只完成当前订单。继续出发直接呈现服务端 blockers，所有条件满足后再二次确认；UNKNOWN 只核对。实现前读取 P10 `27:320`、P11 `27:346`、P12 `27:365`。

- [ ] **Step 4: GREEN：工人取货闭环**

Run: `cd miniprogram && npm test && npm run typecheck && npm run build:mp-weixin`

Expected: PASS；验证仅申请人、错车、非 AT_STOP、状态过期、本人已取他人未取、门未关、活动工单、末站和 unknown。

- [ ] **Step 5: Commit checkpoint**

```bash
git add miniprogram/src/features/order miniprogram/src/subpackages/worker miniprogram/src/pages.json miniprogram/tests/pickup.test.ts
git commit -m "feat(mini): add pickup and continue flow"
```

### Task 9: 工人工单与仓库处理（P13–P15/P33）

**Files:**
- Create: `miniprogram/src/features/ticket/model.ts`
- Create: `miniprogram/src/features/ticket/api.ts`
- Create: `miniprogram/src/subpackages/worker/ticket-create.vue`
- Create: `miniprogram/src/subpackages/worker/tickets.vue`
- Create: `miniprogram/src/subpackages/worker/ticket-detail.vue`
- Create: `miniprogram/src/subpackages/warehouse/tickets.vue`
- Create: `miniprogram/src/subpackages/warehouse/ticket-detail.vue`
- Modify: `miniprogram/src/pages.json`
- Test: `miniprogram/tests/ticket.test.ts`

**Interfaces:**
- Consumes: tickets list/detail/create/accept/notes/close。
- Produces: `Ticket`、`createTicket()`、`acceptTicket()`、`addTicketNote()`、`closeTicket()`、`ticketStateText()`。

- [ ] **Step 1: RED：重复活动工单和关闭语义**

```ts
test('已有活动工单时打开原工单', () => assert.equal(resolveCreatedTicket(existing).route, `/subpackages/worker/ticket-detail?id=${existing.id}`))
test('关闭结果去空白后不能为空', () => assert.equal(validateCloseResult('  '), '请填写处理结果'))
```

- [ ] **Step 2: 运行 RED**

Run: `cd miniprogram && npm test -- --test-name-pattern='活动工单|关闭结果'`

Expected: FAIL。

- [ ] **Step 3: CODE：实现工单动作和双视角页面**

```ts
export function closeTicket(ticket:Ticket, result:string, key:string) {
  return request<Ticket>({ path:`/tickets/${ticket.id}/close`, method:'POST', data:{expectedVersion:ticket.version,result:result.trim()}, idempotencyKey:key })
}
```

工人列表只能读本人提交；仓库列表按当前范围。创建接口返回现有活动工单时直接打开。仓库受理、说明、关闭每次用最新 version；409 刷新并显示他人已处理。关闭后不修改订单/车辆，并保留完整时间线。实现前读取 P13 `27:386`、P14 `39:144`、P15 `39:180`、P33 `51:423/443` 与 `52:626/643/659/675/690`。

- [ ] **Step 4: GREEN：工单闭环**

Run: `cd miniprogram && npm test && npm run typecheck && npm run build:h5 && npm run build:mp-weixin`

Expected: PASS；验证重复反馈、并发受理/关闭、提交失败保留说明、关闭后订单与任务不变。

- [ ] **Step 5: Commit checkpoint**

```bash
git add miniprogram/src/features/ticket miniprogram/src/subpackages/worker miniprogram/src/subpackages/warehouse miniprogram/src/pages.json miniprogram/tests/ticket.test.ts
git commit -m "feat(mini): add ticket workflow"
```

### Task 10: 消息、跨工作区跳转与九识入口（P16/P17/P20）

**Files:**
- Create: `miniprogram/src/features/notification/model.ts`
- Create: `miniprogram/src/features/notification/api.ts`
- Create: `miniprogram/src/features/notification/navigation.ts`
- Create: `miniprogram/src/features/integration/jiushi.ts`
- Replace: `miniprogram/src/pages/messages/index.vue`
- Create: `miniprogram/src/pages/messages/detail.vue`
- Create: `miniprogram/src/components/JiushiEntry.vue`
- Modify: `miniprogram/src/pages.json`
- Test: `miniprogram/tests/notification.test.ts`

**Interfaces:**
- Consumes: messages、read、unread count、`GET /integration/mini-entry`，Task 1/阶段 1 工作区切换。
- Produces: `messageApi`、`openMessageTarget(message)`、`openJiushiHome()`。

- [ ] **Step 1: RED：失权和跨工作区跳转**

```ts
test('失权消息不尝试打开业务对象', () => assert.deepEqual(targetAction({...message,target:null}), {kind:'NONE'}))
test('目标角色已授权时先切换再导航', () => assert.deepEqual(targetAction(message), {kind:'SWITCH',role:'warehouse',url:'/subpackages/warehouse/order-review?id=o1'}))
```

- [ ] **Step 2: 运行 RED**

Run: `cd miniprogram && npm test -- --test-name-pattern='失权消息|目标角色'`

Expected: FAIL。

- [ ] **Step 3: CODE：实现消息和九识返回刷新**

```ts
export async function openJiushiHome() {
  const entry = await request<MiniEntry>({path:'/integration/mini-entry'})
  if (!entry.available || !entry.appId) throw new Error('九识小程序暂不可用')
  return uni.navigateToMiniProgram({appId:entry.appId,path:'/'})
}
```

消息列表分页、未读标记、详情进入即幂等已读；目标失权时只显示说明。跨工作区复用放弃草稿确认，切换后重新拉取目标。九识入口只在仓库/调度有授权页面展示；取消/失败保留本页，返回 `onShow` 刷新车辆，不更改业务状态。开发模拟由服务端 mode 标识，生产无客户端模拟按钮。实现前读取 P16 `39:200`、P17 `39:231`、P20 `39:302/58:571/587/662`。

- [ ] **Step 4: GREEN：消息与跳转验证**

Run: `cd miniprogram && npm test && npm run typecheck && npm run build:mp-weixin`

Expected: PASS；验证去重消息、已读、无权限、对象不可用、切换取消和九识取消/失败/返回刷新。

- [ ] **Step 5: Commit checkpoint**

```bash
git add miniprogram/src/features/notification miniprogram/src/features/integration miniprogram/src/pages/messages miniprogram/src/components/JiushiEntry.vue miniprogram/src/pages.json miniprogram/tests/notification.test.ts
git commit -m "feat(mini): add messages and jiushi entry"
```

### Task 11: 日常概览与业务履历（P34/P40）

**Files:**
- Create: `miniprogram/src/features/report/model.ts`
- Create: `miniprogram/src/features/report/api.ts`
- Replace: `miniprogram/src/subpackages/overview/index.vue`
- Create: `miniprogram/src/subpackages/overview/records.vue`
- Create: `miniprogram/src/subpackages/overview/history.vue`
- Create: `miniprogram/src/subpackages/overview/history-detail.vue`
- Modify: `miniprogram/src/pages.json`
- Test: `miniprogram/tests/report.test.ts`

**Interfaces:**
- Consumes: `GET /overview`、`GET /history`；下钻复用订单/任务/工单查询。
- Produces: `Overview`、`History`、`metricDrilldown()`、`historyPresentation()`。

- [ ] **Step 1: RED：统计口径和更正记录**

```ts
test('任务指标不会转换成订单筛选', () => assert.deepEqual(metricDrilldown('RUNNING_TASKS'), {entity:'TASK',state:'RUNNING'}))
test('更正记录保留原值、新值和原因', () => assert.deepEqual(presentCorrection(history), {before:'旧备注',after:'新备注',reason:'现场核对'}))
```

- [ ] **Step 2: 运行 RED**

Run: `cd miniprogram && npm test -- --test-name-pattern='任务指标|更正记录'`

Expected: FAIL。

- [ ] **Step 3: CODE：实现概览下钻和履历分页**

```ts
export function getOverview(date:string, warehouseId?:string) {
  const query = new URLSearchParams({date,...(warehouseId?{warehouseId}:{})})
  return request<Overview>({path:`/overview?${query}`})
}
```

日期使用 Asia/Shanghai 业务日；仓库选择只显示授权范围。指标卡明确标注文单/批次/任务/工单单位并传固定下钻条件。履历支持日期、业务类型、人员、车辆或订单筛选，服务端裁剪敏感信息；详情只读，无小程序更正按钮。实现前读取 P34 `57:325/58:332/348/366/60:387/406/425/443`、P40 `57:421/58:677/694/712/730`。

- [ ] **Step 4: GREEN：概览和履历验证**

Run: `cd miniprogram && npm test && npm run typecheck && npm run build:h5 && npm run build:mp-weixin`

Expected: PASS；按服务端种子数据逐项核对统计和下钻数量，跨仓查询返回 403/404 且不残留旧数据。

- [ ] **Step 5: Commit checkpoint**

```bash
git add miniprogram/src/features/report miniprogram/src/subpackages/overview miniprogram/src/pages.json miniprogram/tests/report.test.ts
git commit -m "feat(mini): add overview and business history"
```

### Task 12: 完整闭环、权限、视觉与交付验证

**Files:**
- Modify: `miniprogram/tests/request.test.ts`
- Create: `miniprogram/tests/delivery-flow.test.ts`
- Modify: `miniprogram/README.md`
- Modify: `docs/superpowers/plans/2026-09-20-development-roadmap.md`
- Create: `docs/reviews/2026-09-20-mini-delivery-review.md`
- Create: `docs/validation/2026-09-20-mini-delivery.md`

**Interfaces:**
- Consumes: Tasks 1–11 全部页面/API，服务端 MySQL、种子数据和开发车辆模拟器。
- Produces: 可复核的流程 A–H、J 验证记录、review 结论和外部依赖清单。

- [ ] **Step 1: RED：增加失权和关键状态回归**

```ts
test('权限撤销响应清除业务数据并退出当前工作区', async () => {
  seedSensitiveCache()
  await assert.rejects(request({path:'/orders'}), /权限已变化/)
  assert.equal(sessionToken.get(), null)
  assert.deepEqual(workspace.cache, {})
})
test('控制受理、结果未知和实际执行保持三种展示', () => {
  assert.deepEqual(['ACCEPTED','UNKNOWN','EXECUTED'].map(controlPhaseText), ['请求已受理','结果待核对','现场状态已确认'])
})
```

- [ ] **Step 2: 运行完整自动化验证**

Run: `cd miniprogram && npm test && npm run typecheck && npm run build:h5 && npm run build:mp-weixin`

Expected: 全部退出码 0；记录测试数量和构建产物路径。

- [ ] **Step 3: CODE：只修复验证发现的问题并补交付说明**

```markdown
| 验证项 | 环境 | 结果 | 证据/阻塞 |
| --- | --- | --- | --- |
| 申请→取货→继续出发 | 本地后端 + MySQL + 车辆模拟器 | 通过/失败 | 订单、批次、任务、控制请求 ID |
| 微信真机 | 微信开发者工具/真机 | 依赖阻塞 | 缺 AppID 时不得写“通过” |
| 九识实车 | 真实车辆 | 依赖阻塞 | 缺凭证/车辆时不得写“通过” |
```

在 README 写明环境变量、后端地址、模拟边界和启动命令；路线图只勾选有证据的条目。review 检查客户端越权拼接、token 落盘、ID 数值化、控制盲重发、取消覆盖主状态、门请求等同门状态、未覆盖空/失败/分页等问题。

- [ ] **Step 4: GREEN：浏览器和联调验收**

Run: `cd miniprogram && npm run dev:h5 -- --host 127.0.0.1`

Expected: 在 390×844 和 320×568 完成流程 A–H、J；无横向溢出、遮挡或不可解释的禁用按钮。使用两个账号验证跨仓拒绝、并发冲突、停用/撤权；模拟器验证失败、超时、重复、乱序和过期事件。

- [ ] **Step 5: 最终 diff 与 review 门禁**

Run: `git diff --check && git status --short`

Expected: `git diff --check` 退出码 0；没有密钥、token、真实密码、日志或构建产物。完成 review 并修复确认问题后，重复受影响测试。

- [ ] **Step 6: Commit checkpoint**

```bash
git add miniprogram docs/superpowers/plans/2026-09-20-development-roadmap.md docs/reviews/2026-09-20-mini-delivery-review.md docs/validation/2026-09-20-mini-delivery.md
git commit -m "feat(mini): complete delivery workflows"
```

## 执行顺序与外部依赖

Task 1–2 是所有业务切片前置；Task 3→4→5→6 形成申请到派发；Task 7 和 Task 8 共用车辆/控制模型，先完成调度侧模型再做工人取货；Task 9 可在 Task 4 后并行准备但完整阻断联调依赖 Task 7/8；Task 10 依赖各目标路由；Task 11 在订单/任务/工单稳定后完成；Task 12 最后执行。

后端须先提供 `docs/api/delivery-contract.md` 中对应接口及 MySQL 测试数据。页面联调前补齐设计文档第 9 节的聚合展示字段；缺字段时更新契约与 DTO，不在客户端跨权限拼接。微信 AppID、短信服务、九识凭证/MQTT、测试车辆和点位未提供时，Task 12 只能记录模拟闭环通过，真实微信/跳转/实车项保持“依赖阻塞”。
