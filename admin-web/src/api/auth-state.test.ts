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
