import test from 'node:test'
import assert from 'node:assert/strict'
import {
  batchStatusText,
  canAddToBatch,
  isConflict,
  sameBatchGroup,
  validateReview,
  reviewIntent,
  type BatchOrder,
} from '../src/features/batch/model'

const accepted: BatchOrder = {
  id: '9007199254740993', number: 'DH001', version: 2,
  warehouseId: 'warehouse/1', stopId: 'stop&2', slotId: 'slot-1',
  slotStart: '2030-01-01T09:00:00+08:00', slotEnd: '2030-01-01T09:30:00+08:00',
  description: '轴承 2 箱', size: '40 × 30 × 25 cm', receiverName: '张三',
  receiverPhone: '138 **** 0000', receiverBound: true, applicantId: 'worker-1',
  applicantName: '张三', status: 'ACCEPTED', batchId: null, cancellation: null,
  allowedActions: ['ORDER_REVIEW'], createdAt: '2030-01-01T08:00:00+08:00', updatedAt: '2030-01-01T08:00:00+08:00',
  remark: '', compartmentIds: [], vehicleId: null, dispatchPendingReview: false,
}

test('批次成员必须同仓同点同时段', () => {
  assert.equal(sameBatchGroup([accepted, { ...accepted, id: '2' }]), true)
  assert.equal(sameBatchGroup([accepted, { ...accepted, id: '2', stopId: 'other' }]), false)
  assert.equal(sameBatchGroup([accepted, { ...accepted, id: '2', slotId: 'other' }]), false)
})

test('受理订单不误处理独立取消申请', () => {
 const order={...accepted,cancellation:{id:'cancel-1',status:'PENDING' as const,reason:'调整计划',result:null}}
 assert.equal(reviewIntent(order,'review','ACCEPT','').cancellationId,undefined)
 assert.equal(reviewIntent(order,'cancel','APPROVE','已核对').cancellationId,'cancel-1')
})

test('批次只允许选择已受理且未占用订单', () => {
  assert.equal(canAddToBatch(accepted), true)
  assert.equal(canAddToBatch({ ...accepted, status: 'PENDING' }), false)
  assert.equal(canAddToBatch({ ...accepted, batchId: 'batch-1' }), false)
})

test('驳回必须填写原因且受理不需要原因', () => {
  assert.equal(validateReview('REJECT', '  '), '请填写驳回原因')
  assert.equal(validateReview('REJECT', '信息不完整'), '')
  assert.equal(validateReview('ACCEPT', ''), '')
})

test('批次状态和并发冲突保持独立语义', () => {
  assert.equal(batchStatusText('DRAFT'), '待装货')
  assert.equal(batchStatusText('LOCKED'), '已锁定')
  assert.equal(isConflict({ code: 40902 }), true)
  assert.equal(isConflict({ code: 40903 }), true)
  assert.equal(isConflict({ code: 42201 }), false)
})

test('仓库审批与批次 API 使用字符串 ID、版本和幂等键', async () => {
  const calls: any[] = []
  ;(globalThis as any).uni = { request(options: any) { calls.push(options); options.success({ statusCode: 200, data: { code: 0, message: 'ok', data: { items: [], total: 0, page: 1, pageSize: 20 } } }) } }
  const { listWarehouseOrders, reviewOrder, reviewCancellation, listBatches, createBatch, replaceBatchOrders } = await import('../src/features/batch/api')
  await listWarehouseOrders({ status: 'PENDING', keyword: '轴承 & 箱', page: 1, pageSize: 20 })
  await reviewOrder('9007199254740993', 2, 'REJECT', '资料不全', 'key-review')
  await reviewCancellation('9007199254740993', 'cancel/1', 3, 'APPROVE', '已核对', 'key-cancel')
  await listBatches({ warehouseId: 'warehouse/1', status: 'DRAFT', page: 2, pageSize: 20 })
  await createBatch(['9007199254740993'], 'key-create')
  await replaceBatchOrders({ id: 'batch/1', version: 4 }, ['9007199254740993', '2'], 'key-replace')
  assert.equal(calls[0].header['X-Workspace'], 'warehouse')
  assert.ok(calls[0].url.includes('keyword=%E8%BD%B4%E6%89%BF%20%26%20%E7%AE%B1'))
  assert.deepEqual(calls[1].data, { expectedVersion: 2, decision: 'REJECT', reason: '资料不全' })
  assert.equal(calls[1].header['Idempotency-Key'], 'key-review')
  assert.ok(calls[2].url.endsWith('/orders/9007199254740993/cancellations/cancel%2F1/review'))
  assert.ok(calls[3].url.includes('warehouseId=warehouse%2F1'))
  assert.deepEqual(calls[4].data, { orderIds: ['9007199254740993'] })
  assert.ok(calls[5].url.endsWith('/batches/batch%2F1/orders'))
  assert.deepEqual(calls[5].data, { expectedVersion: 4, orderIds: ['9007199254740993', '2'] })
})
