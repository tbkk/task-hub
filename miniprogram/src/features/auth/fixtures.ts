import type { Identity } from './model'
import type { Grant } from '../workspace/model'
export type AuthUser = Identity
const worker: Grant = { role: 'worker', scope: '本人申请与接收的订单', warehouseIds: ['wh-1'] }
const warehouse: Grant = { role: 'warehouse', scope: '一号仓库', warehouseIds: ['wh-1'] }
const dispatch: Grant = { role: 'dispatch', scope: '二号仓库', warehouseIds: ['wh-2'] }
const overview: Grant = { role: 'overview', scope: '全厂日常业务（只读）', warehouseIds: ['wh-1', 'wh-2'] }
export const demoAccounts = [
  { verifiedPhone: '13800000000', name: '张三', grants: [worker] },
  { verifiedPhone: '13800000003', name: '李明', grants: [worker, warehouse, dispatch, overview] },
  { verifiedPhone: '13800000004', name: '王芳', grants: [warehouse] },
  { verifiedPhone: '13800000005', name: '赵磊', grants: [dispatch] },
  { verifiedPhone: '13800000006', name: '陈晨', grants: [overview] },
]
export function findDemoAccount(phone: string): AuthUser | undefined {
  const index = demoAccounts.findIndex(user => user.verifiedPhone === phone)
  if (index < 0) return undefined
  return JSON.parse(JSON.stringify({ ...demoAccounts[index], id: `mock-user-${index + 1}`, platformGrants: [], admin: false, mustChangePassword: false })) as AuthUser
}
