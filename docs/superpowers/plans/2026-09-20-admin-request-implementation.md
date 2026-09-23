# 管理端认证请求基础 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将管理端请求接入内存 Bearer 会话，保留版本冲突细节，准确表达未知结果，并支持真实 xlsx 下载。

**Architecture:** auth-state 为独立内存模块；request 统一添加凭据，HTTP错误保留data，401仅失效本次请求所用会话，防止旧响应注销新登录。

**Tech Stack:** TypeScript、Vite、Vitest、Fetch。

## Global Constraints

- 保持 feature/base；不提交推送。
- token 只存内存，不使用 Cookie，不写日志。
- 下载验证 xlsx Content-Type，文件名去除路径/控制字符。

---

### Task 1: 请求与会话失效

**Files:** 创建 `admin-web/src/api/auth-state.ts`、`auth-state.test.ts`；修改 `admin-web/src/api/request.ts`。

**Interfaces:** 产出 `setAccessToken(string)`, `clearAccessToken()`, `onSessionExpired(()=>void)`, `request<T>(path,RequestInit):Promise<T>` 和 `download(path):Promise<{blob,filename}>`。

- [ ] **Step 1: 写测试并运行红灯**

`npm test -- src/api/auth-state.test.ts`，预期缺少 auth-state 模块失败。

```ts
import { afterEach, expect, it, vi } from 'vitest'
import { setAccessToken, clearAccessToken, onSessionExpired } from './auth-state'
import { request } from './request'

afterEach(() => { clearAccessToken(); vi.unstubAllGlobals() })

it('只在会话内发送 Bearer，不依赖 Cookie', async () => {
  const fetcher = vi.fn().mockImplementation(() => Promise.resolve(new Response(JSON.stringify({ code: 0, data: {} }))))
  vi.stubGlobal('fetch', fetcher)
  await request('/identity/me')
  expect(new Headers(fetcher.mock.calls[0]![1].headers).has('Authorization')).toBe(false)
  setAccessToken('session-test')
  await request('/identity/me')
  expect(new Headers(fetcher.mock.calls[1]![1].headers).get('Authorization')).toBe('Bearer session-test')
  expect(fetcher.mock.calls[1]![1].credentials).toBe('omit')
})

it('401 使会话失效，但普通403不注销全部授权', async () => {
  const expired = vi.fn()
  onSessionExpired(expired)
  setAccessToken('session-test')
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 40302, message: '授权不足' }), { status: 403 })))
  await expect(request('/admin/employees')).rejects.toMatchObject({ status: 403 })
  expect(expired).not.toHaveBeenCalled()
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 40101, message: '请重新登录' }), { status: 401 })))
  await expect(request('/identity/me')).rejects.toMatchObject({ status: 401 })
  expect(expired).toHaveBeenCalledOnce()
})

it('保留版本冲突详情，并将无响应写操作标为结果未知', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 40902, message: '记录已更新', data: { currentVersion: 3 } }), { status: 409 })))
  await expect(request('/admin/employees/a', { method: 'PUT', body: '{}' })).rejects.toMatchObject({ details: { currentVersion: 3 }, kind: 'conflict' })
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))
  await expect(request('/admin/employees', { method: 'POST', body: '{}' })).rejects.toMatchObject({ kind: 'unknown-result' })
})
```

- [ ] **Step 2: 实现内存状态与请求**

`admin-web/src/api/auth-state.ts`

```ts
let token: string | null = null
let expired: () => void = () => {}
export function setAccessToken(value: string) { token = value }
export function getAccessToken() { return token }
export function clearAccessToken() { token = null }
export function onSessionExpired(handler: () => void) { expired = handler }
export function invalidateSession(expectedToken: string | null) {
  if (token !== expectedToken) return
  clearAccessToken()
  expired()
}
```

`admin-web/src/api/request.ts`

```ts
import { getAccessToken, invalidateSession } from './auth-state'

export interface ApiResponse<T> { code: number; message: string; data: T }
export type ApiErrorKind = 'business' | 'unauthorized' | 'forbidden' | 'conflict' | 'unknown-result' | 'invalid-response'
export class ApiError extends Error {
  constructor(message: string, public readonly code: number, public readonly status: number,
    public readonly details: unknown = null, public readonly kind: ApiErrorKind = 'business') {
    super(message)
    this.name = 'ApiError'
  }
}
const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')
function isWrite(init: RequestInit) { return !['GET', 'HEAD'].includes((init.method || 'GET').toUpperCase()) }

async function send(path: string, init: RequestInit): Promise<Response> {
  const headers = new Headers(init.headers)
  const token = getAccessToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  headers.set('X-Request-Id', crypto.randomUUID())
  let response: Response
  try {
    response = await fetch(`${apiBaseUrl}/${path.replace(/^\//, '')}`, { ...init, headers, credentials: 'omit' })
  } catch {
    throw new ApiError(isWrite(init) ? '请求结果待核对，请先刷新查询实际状态' : '网络连接失败，请重试', -1, 0, null, isWrite(init) ? 'unknown-result' : 'business')
  }
  if (!response.ok) {
    let envelope: Partial<ApiResponse<unknown>> = {}
    try { envelope = await response.json() as Partial<ApiResponse<unknown>> } catch { /* 不回显 HTML 或代理错误正文。 */ }
    if (response.status === 401 || envelope.code === 40301) invalidateSession(token)
    const kind = response.status === 401 ? 'unauthorized' : response.status === 403 ? 'forbidden' : response.status === 409 ? 'conflict' : isWrite(init) && response.status >= 500 ? 'unknown-result' : 'business'
    throw new ApiError(envelope.message || `请求失败（${response.status}）`, envelope.code ?? response.status, response.status, envelope.data, kind)
  }
  return response
}
export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await send(path, init)
  let envelope: ApiResponse<T>
  try { envelope = await response.json() as ApiResponse<T> } catch {
    throw new ApiError('响应无法解析，请刷新核对结果', -1, response.status, null, isWrite(init) ? 'unknown-result' : 'invalid-response')
  }
  if (!envelope || envelope.code !== 0) throw new ApiError(envelope?.message || '请求失败', envelope?.code ?? -1, response.status, envelope?.data)
  return envelope.data
}
export async function download(path: string): Promise<{ blob: Blob; filename: string }> {
  const response = await send(path, {})
  if (!response.headers.get('content-type')?.includes('application/vnd.openxmlformats-officedocument.spreadsheetml.sheet')) {
    throw new ApiError('导出响应格式不正确，请重试', -1, response.status, null, 'invalid-response')
  }
  const disposition = response.headers.get('content-disposition') || ''
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(disposition)?.[1]
  let name = '订单报表.xlsx'
  try { if (encoded) name = decodeURIComponent(encoded) } catch { /* 使用安全默认名称。 */ }
  return { blob: await response.blob(), filename: name.replace(/[\\/\x00-\x1f]/g, '_') }
}
```

- [ ] **Step 3: 运行绿灯与构建**

`cd admin-web && npm test && npm run build`；预期新旧请求测试通过，构建成功。

- [ ] **Step 4: Review 并记录**

核对 token 不持久化、Cookie omit、旧401不清除新token、403不盲目清除其他授权、网络写入错误不称已失败或已成功；真实后台登录联调由身份任务完成。
