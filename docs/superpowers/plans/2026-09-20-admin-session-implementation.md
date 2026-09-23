# 管理端会话与权限菜单 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现账号密码登录会话、退出、改密和能力驱动的首页/菜单。

**Architecture:** Vue shallowRef 仅内存存放 Session；请求层的失效回调清空会话；generation 阻止迟到登录恢复身份。权限取每项平台授权，admin boolean 不扩权。

**Tech Stack:** Vue 3、TypeScript、Vitest。

## Global Constraints

- 继续 feature/base，不提交推送。
- 不存密码，不持久化 token。
- 权限最终由服务端校验。

---

### Task 1: 会话状态与能力菜单

**Files:** 新增 `admin-web/src/features/auth/session.ts`、`session.test.ts`。

**Interfaces:** 消费 `request<T>`、`auth-state.ts`；产出 `session`、`login`、`logout`、`changePassword`、`refreshIdentity`、`can`、`firstAllowedPath`，签名见完整代码。

- [ ] **Step 1: 写测试并验证缺少 session.ts 的红灯**

```ts
import { afterEach, expect, it, vi } from 'vitest'
import { login, logout, session, can, resetSession, firstAllowedPath } from './session'
import { getAccessToken } from '../../api/auth-state'

const identity = { id: 'employee-test', name: '测试人员', verifiedPhone: null, grants: [], platformGrants: [{ capability: 'REPORT_VIEW', scope: 'WAREHOUSES', warehouseIds: ['wh-1'] }], admin: true, mustChangePassword: false }
const reply = (data: unknown) => new Response(JSON.stringify({ code: 0, message: 'ok', data }))
afterEach(() => { resetSession(); vi.unstubAllGlobals() })

it('登录解包会话，菜单能力来自逐项授权', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(reply({ token: 'test-token', expiresAt: '2099-01-01T00:00:00Z', user: identity })))
  await login('test-user', 'test-password')
  expect(getAccessToken()).toBe('test-token')
  expect(session.value?.user.id).toBe('employee-test')
  expect(can('REPORT_VIEW')).toBe(true)
  expect(can('REPORT_EXPORT')).toBe(false)
  expect(can('EMPLOYEE_MANAGE')).toBe(false)
  expect(firstAllowedPath()).toBe('/reports')
})

it('退出失败保留会话，退出成功才清空', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(reply({ token: 'test-token', expiresAt: '2099-01-01T00:00:00Z', user: identity })))
  await login('test-user', 'test-password')
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')))
  await expect(logout()).rejects.toThrow()
  expect(session.value).not.toBeNull()
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(reply(null)))
  await logout()
  expect(session.value).toBeNull()
  expect(getAccessToken()).toBeNull()
})

it('显式取消会话后迟到的登录响应不能恢复认证', async () => {
  let resolve!: (value: Response) => void
  vi.stubGlobal('fetch', vi.fn().mockImplementation(() => new Promise<Response>(r => { resolve = r })))
  const pending = login('test-user', 'test-password')
  resetSession()
  resolve(reply({ token: 'late-token', expiresAt: '2099-01-01T00:00:00Z', user: identity }))
  await expect(pending).rejects.toThrow('登录已取消')
  expect(session.value).toBeNull()
  expect(getAccessToken()).toBeNull()
})
```

运行 `cd admin-web && npm test -- src/features/auth/session.test.ts`，预期缺模块失败。

- [ ] **Step 2: 写入完整实现**

```ts
import { shallowRef } from 'vue'
import { request } from '../../api/request'
import { clearAccessToken, onSessionExpired, setAccessToken } from '../../api/auth-state'
export type Capability = 'EMPLOYEE_MANAGE' | 'MASTERDATA_MANAGE' | 'RULE_MANAGE' | 'INTEGRATION_MANAGE' | 'REPORT_VIEW' | 'REPORT_EXPORT' | 'AUDIT_VIEW'
export interface PlatformGrant { capability: Capability; scope: 'WAREHOUSES' | 'ALL'; warehouseIds: string[] }
export interface Identity {
  id: string; name: string; verifiedPhone: string | null
  grants: { role: 'worker' | 'warehouse' | 'dispatch' | 'overview'; scope: 'SELF' | 'WAREHOUSES' | 'ALL'; warehouseIds: string[] }[]
  platformGrants: PlatformGrant[]; admin: boolean; mustChangePassword: boolean
}
export interface Session { token: string; expiresAt: string; user: Identity }
export const session = shallowRef<Session | null>(null)
let generation = 0
export function resetSession() { generation++; session.value = null; clearAccessToken() }
onSessionExpired(resetSession)
export function can(capability: Capability) {
  return !!session.value?.user.platformGrants.some(g => g.capability === capability && (g.scope === 'ALL' || g.warehouseIds.length > 0))
}
export const moduleEntries: { path: string; title: string; capability: Capability }[] = [
  { path: '/people', title: '人员与权限', capability: 'EMPLOYEE_MANAGE' },
  { path: '/master-data/warehouses', title: '仓库资料', capability: 'MASTERDATA_MANAGE' },
  { path: '/master-data/stops', title: '停靠点资料', capability: 'MASTERDATA_MANAGE' },
  { path: '/master-data/vehicles', title: '车辆资料', capability: 'MASTERDATA_MANAGE' },
  { path: '/master-data/compartments', title: '硬件格口', capability: 'MASTERDATA_MANAGE' },
  { path: '/rules', title: '营业与业务规则', capability: 'RULE_MANAGE' },
  { path: '/integrations', title: '既有服务接入', capability: 'INTEGRATION_MANAGE' },
  { path: '/reports', title: '基础报表', capability: 'REPORT_VIEW' },
  { path: '/audit', title: '审计日志', capability: 'AUDIT_VIEW' },
]
export function firstAllowedPath() {
  if (session.value?.user.mustChangePassword) return '/account'
  return moduleEntries.find(entry => can(entry.capability))?.path || '/account'
}
export async function login(username: string, password: string) {
  const attempt = ++generation
  const result = await request<Session>('/admin/auth/login', { method: 'POST', body: JSON.stringify({ username: username.trim(), password }) })
  if (generation !== attempt) throw new Error('登录已取消')
  if (!result.token || !result.user?.platformGrants?.length) throw new Error('当前账号未开通管理平台权限')
  session.value = result
  setAccessToken(result.token)
}
export async function refreshIdentity() {
  const existing = session.value
  if (!existing) return
  const user = await request<Identity>('/identity/me')
  if (session.value?.token !== existing.token) return
  session.value = { ...existing, user }
  if (!user.platformGrants.length) resetSession()
}
export async function logout() {
  const token = session.value?.token
  await request<null>('/identity/logout', { method: 'POST' })
  if (session.value?.token === token) resetSession()
}
export async function changePassword(currentPassword: string, newPassword: string) {
  await request<null>('/identity/password', { method: 'POST', body: JSON.stringify({ currentPassword, newPassword }) })
  resetSession()
}
```

- [ ] **Step 3: 绿灯与构建**

运行 `cd admin-web && npm test && npm run build`，预期新旧请求/会话测试通过，构建退出0。

- [ ] **Step 4: review**

核对退出失败不伪称撤销、迟到响应不恢复会话、独立REPORT_EXPORT不得从REPORT_VIEW获得、首次改密优先账户页。
