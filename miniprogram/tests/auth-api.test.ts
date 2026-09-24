import assert from 'node:assert/strict'
import { beforeEach, test } from 'node:test'

let requests: Array<Record<string, unknown>> = []
beforeEach(() => {
  requests = []
  ;(globalThis as typeof globalThis & { uni: unknown }).uni = {
    request(options: Record<string, unknown> & { success: (response: unknown) => void }) {
      requests.push(options)
      options.success({ statusCode: 200, data: { code: 0, message: 'ok', data: null } })
    },
  }
})

test('微信交换和账号密码绑定使用无短信契约', async () => {
  const { exchangeWechat, bindWechat } = await import('../src/features/auth/api')
  await exchangeWechat('wx-code')
  await bindWechat('binding-token', 'worker-1', 'a-long-password')
  assert.deepEqual(requests.map(item => ({ url: item.url, data: item.data })), [
    { url: '/api/mini/auth/wechat', data: { code: 'wx-code' } },
    { url: '/api/mini/auth/bind', data: { bindingToken: 'binding-token', username: 'worker-1', password: 'a-long-password' } },
  ])
})
