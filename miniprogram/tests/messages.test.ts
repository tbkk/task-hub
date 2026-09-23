import test from 'node:test'
import assert from 'node:assert/strict'
import { messageTargetUrl } from '../src/features/message/model'

test('消息目标只生成已知实体路由，失权目标不跳转', () => {
  assert.equal(messageTargetUrl(null), null)
  assert.equal(messageTargetUrl({ type: 'ORDER', id: 'a/1', workspace: 'worker' }), '/subpackages/worker/order-detail?id=a%2F1')
  assert.equal(messageTargetUrl({ type: 'TICKET', id: 't1', workspace: 'warehouse' }), '/subpackages/warehouse/ticket-detail?id=t1')
  assert.equal(messageTargetUrl({ type: 'UNKNOWN', id: 'x', workspace: 'worker' }), null)
})
