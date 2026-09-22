import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createMockAuth } from '../src/features/auth/mock'

test('验证码必须先发送，且验证成功后只能使用一次', async () => {
  const auth = createMockAuth({ delay: 0 })
  await assert.rejects(auth.verify('13800000000', '123456'), /获取验证码/)
  await auth.sendCode('13800000000')
  await assert.rejects(auth.verify('13800000000', '000000'), /验证码错误/)
  const user = await auth.verify('13800000000', '123456')
  assert.equal(user.verifiedPhone, '13800000000')
  assert.equal(user.id, 'mock-user-1')
  await assert.rejects(auth.verify('13800000000', '123456'), /获取验证码/)
})

test('校验手机号、发送限频和验证码有效期', async () => {
  let now = 1000
  const auth = createMockAuth({ delay: 0, now: () => now })
  await assert.rejects(auth.sendCode('123'), /手机号/)
  const sent = await auth.sendCode('13800000000')
  assert.equal(sent.retryAt, 61000)
  await assert.rejects(auth.sendCode('13800000000'), /稍后/)
  now += 300001
  await assert.rejects(auth.verify('13800000000', '123456'), /过期/)
})

test('微信绑定票据是必需的，拒绝未准入或停用员工', async () => {
  const auth = createMockAuth({ delay: 0 })
  await auth.sendCode('13800000000')
  await assert.rejects(auth.verify('13800000000', '123456', 'invalid'), /微信登录/)
  const ticket = await auth.wechatLogin()
  const user = await auth.verify('13800000000', '123456', ticket)
  assert.equal(user.name, '张三')
  for (const [phone, message] of [['13800000001', /未开通/], ['13800000002', /停用/]] as const) {
    await auth.sendCode(phone)
    await assert.rejects(auth.verify(phone, '123456'), message)
  }
})

test('取消授权和网络失败后可以重试，验证码发送失败不占用限频', async () => {
  const auth = createMockAuth({ delay: 0 })
  auth.failNext('cancel')
  await assert.rejects(auth.wechatLogin(), /取消/)
  assert.ok(await auth.wechatLogin())
  auth.failNext('network')
  await assert.rejects(auth.sendCode('13800000003'), /网络/)
  assert.equal(auth.retryAt('13800000003'), 0)
  await auth.sendCode('13800000003')
  const user = await auth.verify('13800000003', '123456')
  assert.equal(user.grants.length, 4)
  assert.deepEqual(user.grants.find(grant => grant.role === 'warehouse')?.warehouseIds, ['wh-1'])
  assert.deepEqual(user.grants.find(grant => grant.role === 'dispatch')?.warehouseIds, ['wh-2'])
})
