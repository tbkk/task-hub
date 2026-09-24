import assert from 'node:assert/strict'
import { beforeEach, test } from 'node:test'
import type { Identity, Session } from '../src/features/auth/model'

const identity: Identity = {
  id: 'employee-1',
  name: '张三',
  verifiedPhone: '13800000000',
  grants: [{ role: 'worker', scope: 'SELF', warehouseIds: ['warehouse-1'] }],
  platformGrants: [{ capability: 'REPORT_VIEW', scope: 'WAREHOUSES', warehouseIds: ['warehouse-1'] }],
  admin: false,
  mustChangePassword: false,
}
const session: Session = { token: 'opaque-token', expiresAt: '2026-09-20T18:00:00+08:00', user: identity }
let storage: Record<string, unknown>

beforeEach(() => {
  storage = {}
  ;(globalThis as typeof globalThis & { uni: unknown }).uni = {
    getStorageSync: (key: string) => storage[key] ?? '',
    setStorageSync: (key: string, value: unknown) => { storage[key] = value },
    removeStorageSync: (key: string) => { delete storage[key] },
    reLaunch: () => undefined,
  }
})

test('有效本地会话可在启动时恢复身份和 token', async () => {
  const { acceptSession, restoreSession, clearLocalSession, currentUser, AUTH_SESSION_STORAGE_KEY } = await import('../src/features/auth/session')
  const { sessionToken, SESSION_TOKEN_STORAGE_KEY } = await import('../src/services/session')
  const restored: Session = {
    ...session,
    token: 'restored-token',
    expiresAt: new Date(Date.now() + 60_000).toISOString(),
    user: { ...identity, id: 'restored-user' },
  }
  acceptSession(session)
  storage[AUTH_SESSION_STORAGE_KEY] = JSON.stringify(restored)
  storage[SESSION_TOKEN_STORAGE_KEY] = JSON.stringify({ token: restored.token, expiresAt: restored.expiresAt })

  assert.equal(restoreSession(), true)
  assert.equal(sessionToken.get(), restored.token)
  assert.equal(currentUser.value?.id, restored.user.id)
  clearLocalSession()
})

test('过期或不完整的本地会话启动时被清除', async () => {
  const { restoreSession, currentUser, AUTH_SESSION_STORAGE_KEY } = await import('../src/features/auth/session')
  const { sessionToken, SESSION_TOKEN_STORAGE_KEY } = await import('../src/services/session')
  const expired = { ...session, expiresAt: new Date(Date.now() - 1).toISOString() }
  storage[AUTH_SESSION_STORAGE_KEY] = JSON.stringify(expired)
  storage[SESSION_TOKEN_STORAGE_KEY] = JSON.stringify({ token: expired.token, expiresAt: expired.expiresAt })

  assert.equal(restoreSession(), false)
  assert.equal(sessionToken.get(), null)
  assert.equal(currentUser.value, null)
  assert.equal(storage[AUTH_SESSION_STORAGE_KEY], undefined)
  assert.equal(storage[SESSION_TOKEN_STORAGE_KEY], undefined)

  const malformed = { ...session, expiresAt: 'not-a-date' }
  storage[AUTH_SESSION_STORAGE_KEY] = JSON.stringify(malformed)
  storage[SESSION_TOKEN_STORAGE_KEY] = JSON.stringify({ token: malformed.token, expiresAt: malformed.expiresAt })
  assert.equal(restoreSession(), false)
  assert.equal(sessionToken.get(), null)
})

test('取消登录后服务端即使返回也不恢复 session', async () => {
  const { acceptSession, clearLocalSession, currentUser } = await import('../src/features/auth/session')
  const { sessionToken } = await import('../src/services/session')
  clearLocalSession()
  assert.equal(acceptSession(session, () => false), false)
  assert.equal(sessionToken.get(), null)
  assert.equal(currentUser.value, null)
})

test('接受会话保留 platformGrants，实时撤权清除旧工作区数据', async () => {
  const { acceptSession, applyIdentity, clearLocalSession, currentUser } = await import('../src/features/auth/session')
  const { workspace } = await import('../src/features/workspace/store')
  clearLocalSession()
  assert.equal(acceptSession(session), true)
  assert.deepEqual(currentUser.value?.platformGrants, identity.platformGrants)
  workspace.cache.privateOrder = 'order-1'
  applyIdentity({ ...identity, grants: [] })
  assert.deepEqual(workspace.cache, {})
  assert.equal(workspace.activeRole, null)
  assert.equal(currentUser.value?.grants.length, 0)
  clearLocalSession()
})

test('真实微信登录使用 uni.login code 交换且不在客户端指定员工', async () => {
  let requestData: unknown
  ;(globalThis as typeof globalThis & { uni: unknown }).uni = {
    getStorageSync: () => '', setStorageSync: () => undefined, reLaunch: () => undefined,
    login(options: { success: (result: { code: string }) => void }) { options.success({ code: 'wx-temporary-code' }) },
    request(options: { data: unknown; success: (response: unknown) => void }) {
      requestData = options.data
      options.success({ statusCode: 200, data: { code: 0, message: 'ok', data: { status: 'CREDENTIALS_REQUIRED', bindingToken: 'bind-1', expiresAt: '2026-09-20T10:05:00+08:00' } } })
    },
  }
  const { pendingBinding, startWechatLogin } = await import('../src/features/auth/session')
  assert.equal(await startWechatLogin(), 'CREDENTIALS_REQUIRED')
  assert.deepEqual(requestData, { code: 'wx-temporary-code' })
  assert.equal(pendingBinding.value, 'bind-1')
})

test('退出后迟到的 me 响应不能恢复已清空的身份', async () => {
  const { acceptSession, clearLocalSession, currentUser, refreshIdentity } = await import('../src/features/auth/session')
  let respond!: (response: unknown) => void
  Object.assign(uni, { request(options: { success: (response: unknown) => void }) { respond = options.success } })
  acceptSession(session)
  const pending = refreshIdentity()
  clearLocalSession()
  respond({ statusCode: 200, data: { code: 0, data: identity } })
  await pending.catch(() => undefined)
  assert.equal(currentUser.value, null)
})
