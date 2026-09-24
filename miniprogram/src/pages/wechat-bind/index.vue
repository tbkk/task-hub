<script setup lang="ts">
import { ref } from 'vue'
import { bindWechat } from '@/features/auth/session'

const username = ref('')
const password = ref('')
const busy = ref(false)
const error = ref('')

async function submit() {
  if (busy.value) return
  if (!username.value.trim() || !password.value) {
    error.value = '请输入内部账号和密码'
    return
  }
  busy.value = true
  error.value = ''
  try {
    await bindWechat(username.value, password.value)
    uni.reLaunch({ url: '/pages/index/index' })
  } catch (e) {
    error.value = e instanceof Error ? e.message : '绑定失败，请重试'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <view class="auth-page bind-page">
    <view class="intro">
      <text class="auth-title">绑定微信</text>
      <text class="auth-subtitle">请输入管理员开通的内部账号和密码</text>
    </view>
    <view class="notice-card">
      <text>首次使用微信登录需要完成一次账号绑定，绑定成功后可直接使用微信登录。</text>
    </view>
    <view class="actions">
      <input v-model="username" placeholder="内部账号" autocomplete="username" :disabled="busy" />
      <input v-model="password" type="password" password placeholder="密码" autocomplete="current-password" :disabled="busy" @confirm="submit" />
      <BaseButton :loading="busy" @click="submit">绑定并登录</BaseButton>
      <text v-if="error" class="auth-error">{{ error }}</text>
    </view>
    <text class="contact">临时密码账号请先使用账号密码登录并修改密码</text>
  </view>
</template>

<style lang="scss" scoped>
@use '@/styles/auth.scss';
.bind-page { padding-top: max(116px, calc(env(safe-area-inset-top) + 72px)); }
.intro { margin: 0 24px; }
.intro .auth-subtitle { margin-top: 4px; font-size: 14px; }
.notice-card { margin: 42px 24px 0; padding: 18px 16px; border-radius: 12px; background: #fff; color: #667387; font-size: 13px; line-height: 22px; }
.actions { margin: 36px 16px 0; display: flex; flex-direction: column; gap: 12px; }
.actions input { height: 44px; padding: 0 14px; background: #fff; border-radius: 8px; font-size: 14px; }
.contact { display: block; margin: 28px 16px 0; text-align: center; color: #667387; font-size: 13px; line-height: 20px; }
</style>
