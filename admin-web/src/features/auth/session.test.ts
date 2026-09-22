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
