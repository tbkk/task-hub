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

test('微信交换和短信 challenge 使用契约请求体', async () => {
  const { exchangeWechat, requestSms, verifySms } = await import('../src/features/auth/api')
  await exchangeWechat('wx-code')
  await requestSms('13800000000', 'BIND', 'binding-token')
  await verifySms({ challengeId: 'challenge-1', phone: '13800000000', code: '123456', bindingToken: 'binding-token' })
  assert.deepEqual(requests.map(item => ({ url: item.url, data: item.data })), [
    { url: '/api/mini/auth/wechat', data: { code: 'wx-code' } },
    { url: '/api/mini/auth/sms', data: { phone: '13800000000', purpose: 'BIND', bindingToken: 'binding-token' } },
    { url: '/api/mini/auth/verify', data: { challengeId: 'challenge-1', phone: '13800000000', code: '123456', bindingToken: 'binding-token' } },
  ])
})
