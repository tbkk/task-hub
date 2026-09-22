<script setup lang="ts">
import { ref } from 'vue'
import AppPage from '@/components/AppPage.vue'
import BaseButton from '@/components/BaseButton.vue'
import StatePanel from '@/components/StatePanel.vue'
import { currentUser, signOut } from '@/features/auth/session'
import { workspace } from '@/features/workspace/store'
import { scopeText, workspaces, type Role } from '@/features/workspace/model'
import { useAccess, switchWorkspace } from '@/features/workspace/navigation'
useAccess(true)
const error = ref('')
const switching = ref(false)
function backToProfile() { uni.reLaunch({ url: '/pages/profile/index' }) }
async function select(role: Role) {
  if (switching.value) return
  switching.value = true
  error.value = ''
  try { await switchWorkspace(role) }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '切换失败，请重试' }
  finally { switching.value = false }
}
</script>
<template>
  <AppPage v-if="currentUser" title="切换工作区" subtitle="只显示当前账号已授权的角色" :navigation="false">
    <view v-if="workspace.grants.length" class="choices"><button v-for="grant in workspace.grants" :key="grant.role" class="choice" :class="{ selected: workspace.activeRole === grant.role }" :disabled="switching" @click="select(grant.role)"><view><text class="role-name">{{ workspaces[grant.role].name }}</text><text class="role-description">{{ workspaces[grant.role].description }}</text><text v-if="grant.role !== 'worker'" class="scope">{{ scopeText(grant.scope) }}</text></view><text v-if="workspace.activeRole === grant.role" class="current">当前</text></button></view>
    <StatePanel v-else title="暂无可用工作区" description="当前账号的权限已撤销，请联系管理员。" />
    <text v-if="error" class="error" role="alert">{{ error }}</text>
    <text class="hint">角色切换不会扩大数据权限</text>
    <view class="footer"><BaseButton v-if="workspace.activeRole" variant="secondary" @click="backToProfile">返回个人中心</BaseButton><BaseButton v-else variant="secondary" @click="void signOut()">退出登录</BaseButton></view>
  </AppPage>
</template>
<style scoped>
.choices { display: flex; flex-direction: column; gap: 14px; }.choice { display: flex; align-items: center; justify-content: space-between; width: 100%; margin: 0; padding: 10px 16px; min-height: 56px; background: white; border-radius: 10px; text-align: left; line-height: 20px; }.choice::after { border: 0; }.selected { background: #e8f2ff; }.role-name { display: block; font-size: 15px; color: #1c2433; }.selected .role-name, .current { color: #1677ff; }.role-description, .scope { display: block; font-size: 12px; color: #667387; }.current { font-size: 12px; margin-left: 12px; }.hint { display: block; margin-top: 42px; font-size: 12px; color: #667387; }.error { display: block; margin-top: 16px; color: #c72b33; font-size: 13px; }.footer { margin-top: max(48px, calc(100vh - 610px)); }
</style>
