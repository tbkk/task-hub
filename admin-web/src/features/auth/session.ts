import { shallowRef } from 'vue'
import { request } from '../../api/request'
import { clearAccessToken, onSessionExpired, setAccessToken } from '../../api/auth-state'
export type Capability = 'EMPLOYEE_MANAGE' | 'MASTERDATA_MANAGE' | 'RULE_MANAGE' | 'INTEGRATION_MANAGE' | 'REPORT_VIEW' | 'REPORT_EXPORT' | 'AUDIT_VIEW'
export interface PlatformGrant { capability: Capability; scope: 'WAREHOUSES' | 'ALL'; warehouseIds: string[] }
export interface Identity {
  id: string; name: string; verifiedPhone: string | null
  grants: { role: 'worker' | 'warehouse' | 'dispatch' | 'overview'; scope: 'SELF' | 'WAREHOUSES' | 'ALL'; warehouseIds: string[] }[]
  platformGrants: PlatformGrant[]; admin: boolean; mustChangePassword: boolean
}
export interface Session { token: string; expiresAt: string; user: Identity }
export const session = shallowRef<Session | null>(null)
let generation = 0
export function resetSession() { generation++; session.value = null; clearAccessToken() }
onSessionExpired(resetSession)
export function can(capability: Capability) {
  return !!session.value?.user.platformGrants.some(g => g.capability === capability && (g.scope === 'ALL' || g.warehouseIds.length > 0))
}
export const moduleEntries: { path: string; title: string; capability: Capability }[] = [
  { path: '/people', title: '人员与权限', capability: 'EMPLOYEE_MANAGE' },
  { path: '/master-data/warehouses', title: '仓库资料', capability: 'MASTERDATA_MANAGE' },
  { path: '/master-data/stops', title: '停靠点资料', capability: 'MASTERDATA_MANAGE' },
  { path: '/master-data/vehicles', title: '车辆资料', capability: 'MASTERDATA_MANAGE' },
  { path: '/master-data/compartments', title: '硬件格口', capability: 'MASTERDATA_MANAGE' },
  { path: '/rules', title: '营业与业务规则', capability: 'RULE_MANAGE' },
  { path: '/integrations', title: '既有服务接入', capability: 'INTEGRATION_MANAGE' },
  { path: '/reports', title: '基础报表', capability: 'REPORT_VIEW' },
  { path: '/audit', title: '审计日志', capability: 'AUDIT_VIEW' },
]
export function firstAllowedPath() {
  if (session.value?.user.mustChangePassword) return '/account'
  return moduleEntries.find(entry => can(entry.capability))?.path || '/account'
}
export async function login(username: string, password: string) {
  const attempt = ++generation
  const result = await request<Session>('/admin/auth/login', { method: 'POST', body: JSON.stringify({ username: username.trim(), password }) })
  if (generation !== attempt) throw new Error('登录已取消')
  if (!result.token || !result.user?.platformGrants?.length) throw new Error('当前账号未开通管理平台权限')
  session.value = result
  setAccessToken(result.token)
}
export async function refreshIdentity() {
  const existing = session.value
  if (!existing) return
  const user = await request<Identity>('/identity/me')
  if (session.value?.token !== existing.token) return
  session.value = { ...existing, user }
  if (!user.platformGrants.length) resetSession()
}
export async function logout() {
  const token = session.value?.token
  await request<null>('/identity/logout', { method: 'POST' })
  if (session.value?.token === token) resetSession()
}
export async function changePassword(currentPassword: string, newPassword: string) {
  await request<null>('/identity/password', { method: 'POST', body: JSON.stringify({ currentPassword, newPassword }) })
  resetSession()
}
