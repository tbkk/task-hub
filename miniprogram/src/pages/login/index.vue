<script setup lang="ts">
import { ref } from 'vue'
import BaseButton from '@/components/BaseButton.vue'
import { onShow, onUnload } from '@dcloudio/uni-app'
import { currentUser, mockCode, mockEnabled, pendingBinding, startWechatLogin, failNextMockRequest } from '@/features/auth/session'

const busy = ref(false)
const error = ref('')
const showDemo = ref(false)
let active = true
onUnload(() => { active = false })
function simulate(failure: 'network' | 'cancel') { failNextMockRequest(failure); void wechatLogin() }
onShow(() => {
  if (currentUser.value) uni.reLaunch({ url: '/pages/index/index' })
})
async function wechatLogin() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    const status = await startWechatLogin(() => active)
    if (status === 'AUTHENTICATED') await uni.reLaunch({ url: '/pages/index/index' })
    else await uni.navigateTo({ url: '/pages/phone/index?mode=bind' })
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '登录失败，请重试'
  } finally { busy.value = false }
}
function phoneLogin() {
  pendingBinding.value = null
  uni.navigateTo({ url: '/pages/phone/index' })
}
</script>

<template>
  <view class="auth-page login-page">
    <view class="intro">
      <text class="auth-title">中集内部调度系统</text>
      <text class="auth-subtitle">工厂内部物料配送</text>
    </view>
    <view class="admission-card">
      <text class="admission-title">仅限已开通权限的内部员工使用</text>
      <text class="admission-description">使用内部账号登录后，可提交申请并查看本人订单。</text>
    </view>
    <view class="actions">
      <BaseButton :loading="busy" @click="wechatLogin">{{ busy ? '登录中…' : '微信登录' }}</BaseButton>
      <BaseButton variant="secondary" :disabled="busy" @click="phoneLogin">手机号验证码登录</BaseButton>
      <text v-if="error" class="auth-error" role="alert">{{ error }}</text>
    </view>
    <text class="contact">未开通权限？请联系管理员</text>
    <text v-if="mockEnabled" class="mock-notice">演示模式 · 微信与短信均为模拟</text>
    <view v-if="mockEnabled" class="demo"><button :disabled="busy" @click="showDemo = !showDemo">{{ showDemo ? '收起演示场景' : '演示账号与失败场景' }}</button><view v-if="showDemo"><text>工人：13800000000；多角色：13800000003</text><text>仓库：13800000004；调度：13800000005</text><text>概览：13800000006；验证码：{{ mockCode }}</text><button :disabled="busy" @click="simulate('cancel')">模拟取消微信授权</button><button :disabled="busy" @click="simulate('network')">模拟微信网络失败</button></view></view>
  </view>
</template>

<style lang="scss" scoped>
@use '@/styles/auth.scss';
.login-page { padding-top: max(116px, calc(env(safe-area-inset-top) + 72px)); }
.intro { margin: 0 24px; }
.intro .auth-subtitle { margin-top: 4px; font-size: 14px; }
.admission-card { margin: 42px 24px 0; min-height: 118px; padding: 18px 16px; border-radius: 12px; background: white; }
.admission-title { display: block; font-size: 15px; font-weight: 500; line-height: 22px; }
.admission-description { display: block; margin-top: 12px; font-size: 13px; line-height: 22px; color: #667387; }
.actions { margin: 36px 16px 0; display: flex; flex-direction: column; gap: 12px; }
.contact { display: block; margin-top: 28px; text-align: center; color: #667387; font-size: 13px; line-height: 20px; }
.demo { margin: 10px 16px; text-align: center; color: #667387; font-size: 12px; line-height: 22px; }.demo text { display: block; }.demo button { margin: 0; background: transparent; color: #667387; font-size: 12px; }.demo button::after { border: 0; }
</style>
