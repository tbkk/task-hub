import type { Role } from '../workspace/model'
export interface MessageTarget { type: 'ORDER' | 'BATCH' | 'TASK' | 'TICKET'; id: string; workspace: Role }
export interface Message { id: string; title: string; body: string; createdAt: string; readAt: string | null; target: MessageTarget | null }
export function messageTargetUrl(target: MessageTarget | null) {
  if (!target) return null
  const id = encodeURIComponent(target.id)
  if (target.type === 'ORDER' && target.workspace === 'worker') return `/subpackages/worker/order-detail?id=${id}`
  if (target.type === 'BATCH' && target.workspace === 'warehouse') return `/subpackages/warehouse/batch-edit?id=${id}`
  if (target.type === 'TASK' && target.workspace === 'dispatch') return `/subpackages/dispatch/task-detail?id=${id}`
  if (target.type === 'TICKET' && target.workspace === 'worker') return `/subpackages/worker/ticket-detail?id=${id}`
  if (target.type === 'TICKET' && target.workspace === 'warehouse') return `/subpackages/warehouse/ticket-detail?id=${id}`
  return null
}
