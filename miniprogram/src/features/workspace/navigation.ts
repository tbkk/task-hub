import { onShow } from '@dcloudio/uni-app'
import { currentUser, refreshIdentity } from '../auth/session'
import { workspace, rememberWorkspace } from './store'
import type { Role } from './model'

export async function refreshAccess(allowSelection = false, expectedRole?: Role) {
  const user = currentUser.value
  if (!user) { uni.reLaunch({ url: '/pages/login/index' }); return false }
  try { await refreshIdentity() }
  catch (cause) {
    if (!currentUser.value) return false
    uni.showToast({ title: cause instanceof Error ? cause.message : '身份刷新失败', icon: 'none' })
  }
  const refreshed = currentUser.value
  if (!refreshed) return false
  if (refreshed.mustChangePassword) { uni.reLaunch({ url: '/pages/password/index' }); return false }
  workspace.refresh(refreshed.id, refreshed.grants, uni.getStorageSync(`workspace:${refreshed.id}`))
  if (!allowSelection && !workspace.activeRole) {
    uni.reLaunch({ url: '/pages/workspace/index' }); return false
  }
  if (expectedRole && workspace.activeRole !== expectedRole) {
    uni.reLaunch({ url: landingUrl() }); return false
  }
  return true
}
export function useAccess(allowSelection = false, expectedRole?: Role) {
  onShow(() => { void refreshAccess(allowSelection, expectedRole) })
}
export function landingUrl() {
  return workspace.activeRole ? `/subpackages/${workspace.activeRole}/index` : '/pages/workspace/index'
}
export async function confirmDiscard() {
  if (!workspace.dirty) return true
  const result = await uni.showModal({ title: '放弃未提交内容？', content: '切换后当前未提交内容将被清除。', confirmText: '放弃并切换', cancelText: '继续编辑' })
  return result.confirm
}
export async function switchWorkspace(role: Role) {
  if (!await refreshAccess(true) || !await confirmDiscard()) return
  // 权限可能在确认弹窗期间变化，再核验一次。
  if (!await refreshAccess(true)) return
  workspace.switchTo(role, true)
  rememberWorkspace()
  uni.reLaunch({ url: landingUrl() })
}
export async function navigate(url: string) {
  if (!await refreshAccess() || !await confirmDiscard()) return
  workspace.clearData()
  uni.reLaunch({ url })
}
