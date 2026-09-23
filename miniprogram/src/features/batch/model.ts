import type { Order } from '../order/model'

export function reviewIntent(order: Pick<Order,'version'|'cancellation'>,mode:'review'|'cancel',decision:string,reason:string) {
  return {expectedVersion:order.version,decision,reason:reason.trim(),...(mode==='cancel'?{cancellationId:order.cancellation?.id}:{})}
}

export type BatchStatus = 'DRAFT' | 'READY' | 'LOCKED' | 'CLOSED'
export type LoadStatus = 'UNCONFIRMED' | 'CONFIRMED'
export type ReviewDecision = 'ACCEPT' | 'REJECT'
export type CancellationDecision = 'APPROVE' | 'REJECT'

export interface BatchOrder extends Order {
  batchId: string | null
  vehicleId: string | null
  compartmentIds: string[]
  dispatchPendingReview: boolean
}

export interface BatchAssignment { orderId: string; compartmentIds: string[] }

export interface Batch {
  id: string
  number: string
  version: number
  warehouseId: string
  warehouseName?: string
  stopId: string
  stopName?: string
  slotId: string
  slotStart?: string
  slotEnd?: string
  orderIds: string[]
  orders?: BatchOrder[]
  status: BatchStatus
  loadStatus: LoadStatus
  vehicleId: string | null
  assignments: BatchAssignment[]
  dispatchRequestId: string | null
  taskId: string | null
  allowedActions: string[]
}

const batchStatuses: Record<BatchStatus, string> = {
  DRAFT: '待装货', READY: '待发车', LOCKED: '已锁定', CLOSED: '已关闭',
}

export function batchStatusText(status: string) {
  return batchStatuses[status as BatchStatus] || status
}

export function loadStatusText(status: string) {
  return status === 'CONFIRMED' ? '装货已确认' : status === 'UNCONFIRMED' ? '装货待确认' : status
}

export function sameBatchGroup(orders: Pick<BatchOrder, 'warehouseId' | 'stopId' | 'slotId'>[]) {
  if (orders.length < 2) return true
  const first = orders[0]
  return orders.every(order => order.warehouseId === first.warehouseId && order.stopId === first.stopId && order.slotId === first.slotId)
}

export function canAddToBatch(order: Pick<BatchOrder, 'status' | 'batchId'>) {
  return order.status === 'ACCEPTED' && order.batchId === null
}

export function validateReview(decision: ReviewDecision, reason: string) {
  return decision === 'REJECT' && !reason.trim() ? '请填写驳回原因' : ''
}

export function isConflict(cause: unknown) {
  const code = (cause as { code?: number } | null)?.code
  return code === 40902 || code === 40903
}

export function canEditBatch(batch: Pick<Batch, 'status' | 'allowedActions'>) {
  return (batch.status === 'DRAFT' || batch.status === 'READY') && batch.allowedActions.includes('BATCH_EDIT')
}
