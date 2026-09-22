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

beforeEach(() => {
  ;(globalThis as typeof globalThis & { uni: unknown }).uni = {
    getStorageSync: () => '', setStorageSync: () => undefined, reLaunch: () => undefined,
  }
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
      options.success({ statusCode: 200, data: { code: 0, message: 'ok', data: { status: 'PHONE_REQUIRED', bindingToken: 'bind-1', expiresAt: '2026-09-20T10:05:00+08:00' } } })
    },
  }
  const { pendingBinding, startWechatLogin } = await import('../src/features/auth/session')
  assert.equal(await startWechatLogin(), 'PHONE_REQUIRED')
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
