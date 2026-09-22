<script setup lang="ts">
import { ref } from 'vue'
import AppPage from '@/components/AppPage.vue'
import BaseButton from '@/components/BaseButton.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { currentUser, mockEnabled, signOut, revokeMockRole } from '@/features/auth/session'
import { workspace } from '@/features/workspace/store'
import { scopeText, workspaces } from '@/features/workspace/model'
import { useAccess, navigate, landingUrl } from '@/features/workspace/navigation'
useAccess()
const logout = ref(false)
function openWorkspace() { uni.navigateTo({ url: '/pages/workspace/index' }) }
function favorites() { uni.navigateTo({ url: '/subpackages/worker/favorites' }) }
function pendingFeature(title: string) { uni.showToast({ title: `${title}功能建设中`, icon: 'none' }) }
function orders() {
  if (workspace.activeRole === 'worker') void navigate(`${landingUrl()}?tab=secondary`)
  else pendingFeature('我的订单')
}
</script>
<template>
  <AppPage v-if="currentUser && workspace.activeRole" title="我的" subtitle="账号与常用设置" :tab="3">
    <view class="identity-card"><image class="avatar" src="/static/figma/profile-avatar.svg" mode="aspectFit" /><view class="identity"><text class="name">{{ currentUser.name }}</text><text class="roles">{{ currentUser.grants.map(grant => workspaces[grant.role].name).join(' · ') }}</text><text class="phone">已验证手机：{{ currentUser.verifiedPhone ? `${currentUser.verifiedPhone.slice(0, 3)} **** ${currentUser.verifiedPhone.slice(-4)}` : '未绑定' }}</text></view></view>
    <view class="grant-list"><text v-for="grant in currentUser.grants" :key="grant.role">{{ workspaces[grant.role].name }}：{{ scopeText(grant.scope) }}{{ grant.role === workspace.activeRole ? '（当前）' : '' }}</text></view>
    <text class="section-title">常用功能</text>
    <view class="menu"><BaseButton variant="plain" @click="orders">我的订单</BaseButton><BaseButton variant="plain" @click="pendingFeature('我的异常工单')">我的异常工单</BaseButton><BaseButton variant="plain" v-if="workspace.activeRole === 'worker'" @click="favorites">常用停靠点</BaseButton><BaseButton variant="plain" @click="openWorkspace">角色工作区</BaseButton></view>
    <view class="logout"><BaseButton variant="plain" @click="logout = true">退出登录</BaseButton></view>
    <view v-if="mockEnabled" class="demo-tools"><text>演示模式 · 权限变化测试</text><button @click="revokeMockRole()">模拟撤销当前角色</button><button @click="revokeMockRole(true)">模拟撤销全部权限</button></view>
    <ConfirmDialog :open="logout" title="确认退出当前账号？" message="退出后需要重新登录才能查看业务。" confirm-text="确认退出" cancel-text="继续使用" @confirm="void signOut()" @cancel="logout = false" />
  </AppPage>
</template>
<style scoped>
.identity-card { display: flex; align-items: flex-start; gap: 16px; min-height: 112px; padding: 20px 16px 14px; background: white; border-radius: 12px; }.avatar { flex-shrink: 0; width: 48px; height: 48px; }.identity { min-width: 0; }.name { display: block; font-size: 17px; font-weight: 700; line-height: 24px; }.roles { display: block; margin-top: 6px; color: #667387; font-size: 13px; line-height: 20px; }.phone { display: block; margin-top: 8px; font-size: 14px; line-height: 20px; }
.grant-list { display: flex; flex-direction: column; gap: 6px; padding: 14px 4px 0; font-size: 12px; line-height: 20px; color: #667387; }.section-title { display: block; margin-top: 16px; font-size: 14px; font-weight: 700; line-height: 18px; }.menu { display: flex; flex-direction: column; gap: 8px; }.logout { margin-top: 26px; }.demo-tools { margin-top: 28px; color: #667387; font-size: 12px; }.demo-tools button { background: transparent; color: #667387; font-size: 12px; margin: 4px 0 0; }.demo-tools button::after { border: 0; }
</style>
