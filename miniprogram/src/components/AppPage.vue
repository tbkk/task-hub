<script setup lang="ts">
import { computed } from 'vue'
import { currentUser } from '@/features/auth/session'
import { workspace } from '@/features/workspace/store'
import { workspaces } from '@/features/workspace/model'
import { landingUrl, navigate } from '@/features/workspace/navigation'
withDefaults(defineProps<{ title: string; subtitle?: string; tab?: number; navigation?: boolean }>(), { subtitle: '', tab: -1, navigation: true })
const roleName = computed(() => workspace.activeRole ? workspaces[workspace.activeRole].name : '选择工作区')
const tabs = computed(() => [...(workspace.activeRole ? workspaces[workspace.activeRole].tabs : ['首页', '业务']), '消息', '我的'])
const symbols = ['⌂', '▣', '●', '○']
function go(index: number) {
  const urls = [landingUrl(), `${landingUrl()}?tab=secondary`, '/pages/messages/index', '/pages/profile/index']
  void navigate(urls[index])
}
function selectWorkspace() { uni.navigateTo({ url: '/pages/workspace/index' }) }
</script>
<template>
  <view class="app-page" :class="{ 'has-nav': navigation }">
    <view class="top-bar"><text class="greeting">你好，{{ currentUser?.name || '员工' }}</text><button class="workspace-link" @click="selectWorkspace">{{ roleName }}<text v-if="workspace.grants.length > 1"> ▾</text></button></view>
    <view class="page-body"><text class="page-title">{{ title }}</text><text v-if="subtitle" class="page-subtitle">{{ subtitle }}</text><view class="page-content"><slot /></view></view>
    <view v-if="navigation && workspace.activeRole" class="bottom-nav" role="navigation" aria-label="主导航"><button v-for="(label, index) in tabs" :key="index" :class="{ active: tab === index }" :aria-label="label" @click="go(index)"><text class="nav-icon">{{ symbols[index] }}</text><text>{{ label }}</text></button></view>
  </view>
</template>
<style scoped>
.app-page { min-height: 100vh; padding-bottom: calc(24px + env(safe-area-inset-bottom)); background: #f6f8fb; }
.has-nav { padding-bottom: calc(88px + env(safe-area-inset-bottom)); }
.top-bar { box-sizing: content-box; min-height: 64px; padding: env(safe-area-inset-top) 20px 0; display: flex; align-items: center; gap: 12px; background: white; }
.greeting { font-weight: 700; font-size: 18px; }
.workspace-link { margin: 0 0 0 auto; padding: 0; color: #1677ff; background: transparent; font-size: 13px; line-height: 32px; }
button::after { border: 0; }
.page-body { padding: 24px 16px 0; }.page-title { display: block; font-size: 24px; line-height: 34px; font-weight: 700; }.page-subtitle { display: block; color: #667387; font-size: 13px; line-height: 20px; }.page-content { margin-top: 22px; }
.bottom-nav { position: fixed; bottom: 0; left: 0; right: 0; z-index: 10; display: flex; padding: 8px 8px calc(8px + env(safe-area-inset-bottom)); background: white; border-radius: 10px 10px 0 0; }
.bottom-nav button { flex: 1; min-width: 0; display: flex; flex-direction: column; align-items: center; gap: 3px; margin: 0; padding: 0; border-radius: 0; color: #667387; background: transparent; font-size: 12px; line-height: 20px; }
.bottom-nav .active { color: #1677ff; }.nav-icon { font-size: 17px; line-height: 25px; }
</style>
