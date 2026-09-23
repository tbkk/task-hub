export type Role = 'worker' | 'warehouse' | 'dispatch' | 'overview'
export interface Grant { role: Role; scope: string; warehouseIds: string[] }
export const workspaces: Record<Role, { name: string; description: string; tabs: [string, string] }> = {
  worker: { name: '工人', description: '申请与订单', tabs: ['申请', '订单'] },
  warehouse: { name: '仓库人员', description: '申请受理与批次', tabs: ['工作台', '配送批次'] },
  dispatch: { name: '调度人员', description: '车辆与任务', tabs: ['调度台', '车辆'] },
  overview: { name: '业务概览', description: '授权范围内的业务与记录', tabs: ['概览', '业务记录'] },
}
export function scopeText(scope: string) {
  return { SELF: '本人申请与接收的订单', WAREHOUSES: '指定仓库', ALL: '全厂' }[scope] ?? scope
}

/** 客户端工作区状态；真实数据权限必须由后端独立校验。 */
export class WorkspaceState {
  userId: string | null = null
  grants: Grant[] = []
  activeRole: Role | null = null
  dirty = false
  cache: Record<string, unknown> = {}
  get activeGrant() { return this.grants.find(grant => grant.role === this.activeRole) }
  refresh(userId: string, grants: Grant[], preferred?: string) {
    const changedUser = this.userId !== userId
    const changedGrants = JSON.stringify(this.grants) !== JSON.stringify(grants)
    const candidate = changedUser ? preferred : this.activeRole ?? preferred
    if (changedUser || changedGrants) this.clearData()
    this.userId = userId
    this.grants = grants.map(grant => ({ ...grant, warehouseIds: [...grant.warehouseIds] }))
    this.activeRole = grants.find(grant => grant.role === candidate)?.role
      ?? (grants.length === 1 ? grants[0].role : null)
  }
  switchTo(role: Role, discard = false) {
    if (!this.grants.some(grant => grant.role === role)) throw new Error('当前账号未授权该工作区')
    if (this.activeRole === role && !discard) return
    if (this.dirty && !discard) throw new Error('存在未提交内容，请先确认是否放弃')
    this.clearData()
    this.activeRole = role
  }
  clearData() { this.dirty = false; this.cache = {} }
  reset() { this.userId = null; this.grants = []; this.activeRole = null; this.clearData() }
}
