import assert from 'node:assert/strict'
import { test } from 'node:test'

const identity = { id: 'employee-password', name: '密码员工', verifiedPhone: null, grants: [{ role: 'worker', scope: 'SELF', warehouseIds: [] }], platformGrants: [], admin: false, mustChangePassword: true }

test('账号密码登录保存临时会话并暴露首次改密状态', async () => {
  const storage: Record<string, unknown> = {}
  let request: any
  ;(globalThis as any).uni = {
    getStorageSync: (key: string) => storage[key] ?? '', setStorageSync: (key: string, value: unknown) => { storage[key] = value }, removeStorageSync: (key: string) => { delete storage[key] }, reLaunch: () => undefined,
    request(options: any) { request = options; options.success({ statusCode: 200, data: { code: 0, data: { token: 'password-token', expiresAt: new Date(Date.now() + 3600000).toISOString(), user: identity } } }) },
  }
  const { signInWithPassword, clearLocalSession, currentUser } = await import('../src/features/auth/session')
  const session = await signInWithPassword(' worker-1 ', 'temporary-password')
  assert.equal(session.user.mustChangePassword, true)
  assert.equal(currentUser.value?.id, identity.id)
  assert.deepEqual(request.data, { username: 'worker-1', password: 'temporary-password' })
  clearLocalSession()
})

test('改密成功清理本地会话', async () => {
  const storage: Record<string, unknown> = {}
  ;(globalThis as any).uni = {
    getStorageSync: (key: string) => storage[key] ?? '', setStorageSync: (key: string, value: unknown) => { storage[key] = value }, removeStorageSync: (key: string) => { delete storage[key] }, reLaunch: () => undefined,
    request(options: any) { options.success({ statusCode: 200, data: { code: 0, data: null } }) },
  }
  const { acceptSession, updatePassword, currentUser } = await import('../src/features/auth/session')
  acceptSession({ token: 'change-token', expiresAt: new Date(Date.now() + 3600000).toISOString(), user: identity })
  await updatePassword('temporary-password', 'a-secure-password-123')
  assert.equal(currentUser.value, null)
  assert.equal(storage['task-hub:session'], undefined)
})
