import assert from 'node:assert/strict'
import { beforeEach, test } from 'node:test'

type CapturedRequest = { header?: Record<string, string>; [key: string]: unknown }
let captured: CapturedRequest

beforeEach(() => {
  captured = {}
  ;(globalThis as typeof globalThis & { uni: unknown }).uni = {
    request(options: CapturedRequest & { success: (response: unknown) => void }) {
      captured = options
      options.success({ statusCode: 200, data: { code: 0, message: 'ok', data: { id: '1' } } })
    },
  }
})

test('业务请求附加内存 token、工作区和幂等键并保留自定义请求头', async () => {
  const { sessionToken } = await import('../src/services/session')
  const { request } = await import('../src/services/request')
  sessionToken.set('opaque-token')
  await request({
    path: '/orders',
    method: 'POST',
    data: {},
    workspace: 'worker',
    idempotencyKey: 'same-confirmation-key',
    header: { 'X-Trace': 'trace-1' },
  })
  assert.deepEqual(captured.header, {
    'X-Trace': 'trace-1',
    Authorization: 'Bearer opaque-token',
    'X-Workspace': 'worker',
    'Idempotency-Key': 'same-confirmation-key',
  })
  sessionToken.clear()
})

test('API 错误保留结构化错误数据', async () => {
  ;(globalThis as typeof globalThis & { uni: unknown }).uni = {
    request(options: { success: (response: unknown) => void }) {
      options.success({ statusCode: 409, data: { code: 40902, message: '版本冲突', data: { currentVersion: 3 } } })
    },
  }
  const { request, ApiRequestError } = await import('../src/services/request')
  await assert.rejects(request({ path: '/orders/1' }), (error: unknown) => {
    assert.ok(error instanceof ApiRequestError)
    assert.equal(error.code, 40902)
    assert.deepEqual(error.data, { currentVersion: 3 })
    return true
  })
})

test('已认证会话的401触发会话失效处理', async () => {
  let invalidations = 0
  const { setSessionInvalidationHandler, sessionToken } = await import('../src/services/session')
  sessionToken.set('active')
  const { request } = await import('../src/services/request')
  setSessionInvalidationHandler(() => { invalidations += 1 })
  ;(globalThis as typeof globalThis & { uni: unknown }).uni = {
    request(options: { success: (response: unknown) => void }) {
      options.success({ statusCode: 401, data: { code: 40101, message: '会话已失效', data: null } })
    },
  }
  await assert.rejects(request({ path: '/identity/me' }), /会话已失效/)
  assert.equal(invalidations, 1)
  setSessionInvalidationHandler(null)
  sessionToken.clear()
})

test('旧会话401、匿名验证码错误和单角色失权不注销新会话或其他角色', async () => {
  const { sessionToken, setSessionInvalidationHandler } = await import('../src/services/session')
  const { request } = await import('../src/services/request')
  let invalidations = 0
  let respond!: (response: unknown) => void
  setSessionInvalidationHandler(() => { invalidations++ })
  ;(globalThis as typeof globalThis & { uni: unknown }).uni = { request(options: { success: (response: unknown) => void }) { respond = options.success } }
  sessionToken.set('old')
  const old = request({ path: '/orders' })
  sessionToken.set('new')
  respond({ statusCode: 401, data: { code: 40101, message: '过期' } })
  await assert.rejects(old)
  sessionToken.clear()
  const verify = request({ path: '/mini/auth/verify', method: 'POST' })
  respond({ statusCode: 401, data: { code: 40101, message: '验证码错误' } })
  await assert.rejects(verify)
  sessionToken.set('new')
  const denied = request({ path: '/orders' })
  respond({ statusCode: 403, data: { code: 40302, message: '当前角色已撤销' } })
  await assert.rejects(denied)
  assert.equal(invalidations, 0)
  setSessionInvalidationHandler(null)
  sessionToken.clear()
})
