import { request } from '../../services/request'
import type { Page } from '../../services/types'
import { query } from '../catalog/api'
import type { Batch, BatchOrder, CancellationDecision, ReviewDecision } from './model'

export function listWarehouseOrders(params: { status?: string; warehouseId?: string; from?: string; to?: string; keyword?: string; page: number; pageSize: number }) {
  return request<Page<BatchOrder>>({ path: `/orders?${query(params)}`, workspace: 'warehouse' })
}

export function getWarehouseOrder(id: string) {
  return request<BatchOrder>({ path: `/orders/${encodeURIComponent(id)}`, workspace: 'warehouse' })
}

export function reviewOrder(id: string, expectedVersion: number, decision: ReviewDecision, reason: string, key: string) {
  return request<BatchOrder>({ path: `/orders/${encodeURIComponent(id)}/review`, method: 'POST', workspace: 'warehouse', data: { expectedVersion, decision, reason }, idempotencyKey: key })
}

export function reviewCancellation(id: string, cancellationId: string, expectedVersion: number, decision: CancellationDecision, reason: string, key: string) {
  return request<BatchOrder>({ path: `/orders/${encodeURIComponent(id)}/cancellations/${encodeURIComponent(cancellationId)}/review`, method: 'POST', workspace: 'warehouse', data: { expectedVersion, decision, reason }, idempotencyKey: key })
}

export function listBatches(params: { warehouseId?: string; status?: string; page: number; pageSize: number }) {
  return request<Page<Batch>>({ path: `/batches?${query(params)}`, workspace: 'warehouse' })
}

export function getBatch(id: string) {
  return request<Batch>({ path: `/batches/${encodeURIComponent(id)}`, workspace: 'warehouse' })
}

export function createBatch(orderIds: string[], key: string) {
  return request<Batch>({ path: '/batches', method: 'POST', workspace: 'warehouse', data: { orderIds }, idempotencyKey: key })
}

export function replaceBatchOrders(batch: Pick<Batch, 'id' | 'version'>, orderIds: string[], key: string) {
  return request<Batch>({ path: `/batches/${encodeURIComponent(batch.id)}/orders`, method: 'PUT', workspace: 'warehouse', data: { expectedVersion: batch.version, orderIds }, idempotencyKey: key })
}
