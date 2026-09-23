import assert from 'node:assert/strict'
import { test } from 'node:test'
import { Submission } from '../src/features/order/submission'

test('网络失败后重试同一提交复用幂等键和请求体', async () => {
  const submission = new Submission<{ expectedVersion: number }>()
  const calls: Array<{ body: { expectedVersion: number }; key: string }> = []
  let attempts = 0
  const send = async (body: { expectedVersion: number }, key: string) => {
    calls.push({ body, key })
    attempts += 1
    if (attempts === 1) throw new Error('网络失败')
    return { status: 'ACCEPTED' as const }
  }

  await assert.rejects(submission.run({ expectedVersion: 4 }, send), /网络失败/)
  await submission.run({ expectedVersion: 4 }, send)

  assert.equal(calls.length, 2)
  assert.equal(calls[0].key, calls[1].key)
  assert.deepEqual(calls[0].body, calls[1].body)
  assert.equal(submission.pending, false)
})

test('UNKNOWN 结果保留同一提交，重试时继续复用幂等键', async () => {
  const submission = new Submission<{ action: string }>()
  const keys: string[] = []
  let attempts = 0
  const send = async (body: { action: string }, key: string) => {
    keys.push(key)
    attempts += 1
    return { status: attempts === 1 ? 'UNKNOWN' as const : 'ACCEPTED' as const, body }
  }

  const first = await submission.run({ action: 'go' }, send, result => result.status === 'UNKNOWN')
  assert.equal(first.status, 'UNKNOWN')
  assert.equal(submission.pending, true)
  await submission.run({ action: 'go' }, send, result => result.status === 'UNKNOWN')

  assert.equal(keys.length, 2)
  assert.equal(keys[0], keys[1])
  assert.equal(submission.pending, false)
})

test('待确认提交不能改写为另一项操作', async () => {
  const submission = new Submission<{ action: string }>()
  await submission.run({ action: 'go' }, async () => ({ status: 'UNKNOWN' as const }), result => result.status === 'UNKNOWN')
  await assert.rejects(submission.run({ action: 'cancel' }, async () => ({ status: 'ACCEPTED' as const })), /重试原申请/)
})
