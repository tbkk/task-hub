import assert from 'node:assert/strict'
import { test } from 'node:test'
import { displayMetric, doorStatusText, requestStatusText } from '../src/features/common/presentation'

test('未知值不伪装为零或正常状态', () => {
  assert.equal(displayMetric(null, '米'), '—')
  assert.equal(displayMetric(12, '米'), '12 米')
  assert.equal(doorStatusText('UNKNOWN'), '状态待核对')
})

test('请求状态区分受理与实际执行结果待核对', () => {
  assert.equal(requestStatusText('ACCEPTED'), '请求已受理')
  assert.equal(requestStatusText('UNKNOWN'), '结果待核对')
})
