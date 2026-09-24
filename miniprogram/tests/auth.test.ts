import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createMockAuth } from '../src/features/auth/mock'

test('微信绑定必须先完成微信登录且成功后令牌只能使用一次', async () => {
  const auth = createMockAuth({ delay: 0 })
  await assert.rejects(auth.bindCredentials('worker', 'password'), /微信登录/)
  await auth.wechatLogin()
  const user = await auth.bindCredentials('worker', 'password')
  assert.equal(user.id, 'mock-user-1')
  await assert.rejects(auth.bindCredentials('worker', 'password'), /微信登录/)
})

test('微信授权取消或网络失败后可以重试', async () => {
  const auth = createMockAuth({ delay: 0 })
  auth.failNext('cancel')
  await assert.rejects(auth.wechatLogin(), /取消/)
  assert.ok(await auth.wechatLogin())
  auth.reset()
  auth.failNext('network')
  await assert.rejects(auth.wechatLogin(), /网络/)
  assert.ok(await auth.wechatLogin())
})
