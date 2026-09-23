<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import BaseButton from '@/components/BaseButton.vue'
import FormField from '@/components/FormField.vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { validPhone } from '@/features/auth/mock'
import { mockCode, mockEnabled, pendingBinding, sendCode, signIn, getCodeRetryAt, failNextMockRequest } from '@/features/auth/session'

const bind = ref(false)
let active = true
onUnload(() => { active = false })
const phone = ref('')
const code = ref('')
const phoneError = ref('')
const error = ref('')
const notice = ref('')
const sending = ref(false)
const submitting = ref(false)
const now = ref(Date.now())
const remaining = computed(() => Math.max(0, Math.ceil((getCodeRetryAt(phone.value) - now.value) / 1000)))
const busy = computed(() => sending.value || submitting.value)
const timer = setInterval(() => { now.value = Date.now() }, 500)
onUnmounted(() => clearInterval(timer))
onLoad((query) => {
  bind.value = query?.mode === 'bind'
  if (bind.value && !pendingBinding.value) error.value = '请返回重新进行微信登录'
})
function checkPhone() {
  phoneError.value = validPhone(phone.value) ? '' : '请输入正确的 11 位手机号'
  return !phoneError.value
}
function changedPhone() { phoneError.value = ''; error.value = ''; notice.value = ''; code.value = '' }
async function getCode() {
  if (busy.value || remaining.value || !checkPhone()) return
  sending.value = true
  error.value = ''
  notice.value = ''
  try {
    await sendCode(phone.value, bind.value ? 'BIND' : 'LOGIN')
    now.value = Date.now()
    notice.value = mockEnabled ? `模拟验证码已生成：${mockCode}（5 分钟内有效）` : '验证码已发送'
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '发送失败，请重试' }
  finally { sending.value = false }
}
async function submit() {
  if (busy.value || !checkPhone()) return
  error.value = ''
  if (!/^\d{6}$/.test(code.value)) { error.value = '请输入 6 位验证码'; return }
  submitting.value = true
  try {
    await signIn(phone.value, code.value, bind.value ? 'BIND' : 'LOGIN', () => active)
    if (!active) return
    await uni.reLaunch({ url: '/pages/index/index' })
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '验证失败，请重试' }
  finally { submitting.value = false }
}
function back() { active = false; uni.reLaunch({ url: '/pages/login/index' }) }
function simulateSmsFailure() { if (busy.value || remaining.value) return; failNextMockRequest('network'); void getCode() }
</script>

<template>
  <view class="auth-page">
    <view class="top-bar"><button class="back" aria-label="返回登录" @click="back">‹</button><text>手机号验证</text></view>
    <view class="content">
      <text class="auth-title">验证手机号</text>
      <text class="auth-subtitle">{{ bind ? '微信登录后需要验证已登记的内部手机号' : '使用已登记的内部手机号登录' }}</text>
      <view class="fields">
        <FormField label="手机号" input-id="phone">
          <input id="phone" v-model="phone" type="number" :maxlength="11" :disabled="busy" placeholder="请输入 11 位手机号" aria-label="手机号" @input="changedPhone" />
        </FormField>
        <text v-if="phoneError" class="auth-error" role="alert">{{ phoneError }}</text>
        <FormField label="验证码" input-id="code">
          <view class="code-row">
            <input id="code" v-model="code" type="number" :maxlength="6" :disabled="busy" placeholder="请输入验证码" aria-label="验证码" @input="error = ''" @confirm="submit" />
            <button class="send-code" :disabled="busy || remaining > 0" @click="getCode">{{ sending ? '发送中…' : remaining > 0 ? `${remaining}s 后重发` : '获取验证码' }}</button>
          </view>
        </FormField>
      </view>
      <view class="feedback" aria-live="polite"><text v-if="error" class="auth-error" role="alert">{{ error }}</text><text v-else class="notice">{{ notice }}</text></view>
      <BaseButton :loading="submitting" :disabled="busy" @click="submit">{{ submitting ? '验证中…' : bind ? '完成验证' : '登录' }}</BaseButton>
      <text v-if="mockEnabled" class="mock-notice">演示账号：13800000000 · 验证码：{{ mockCode }}</text>
      <button v-if="mockEnabled" class="mock-failure" :disabled="busy || remaining > 0" @click="simulateSmsFailure">模拟验证码发送失败</button>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@use '@/styles/auth.scss';
.top-bar { box-sizing: content-box; height: 64px; padding-top: env(safe-area-inset-top); display: flex; align-items: center; gap: 8px; background: white; font-size: 18px; font-weight: 700; }
.back { width: 40px; margin: 0 0 0 8px; padding: 0; color: #1c2433; font-size: 32px; line-height: 44px; background: transparent; }
.back::after, .send-code::after { border: 0; }
.content { padding: 24px 16px 0; }
.fields { margin-top: 22px; display: flex; flex-direction: column; gap: 8px; }
input { flex: 1; min-width: 0; width: 100%; height: 36px; font-size: 14px; color: #1c2433; }
.code-row { display: flex; align-items: center; gap: 8px; }
.send-code { flex-shrink: 0; margin: 0; padding: 0; color: #1677ff; background: transparent; font-size: 13px; line-height: 36px; }
.send-code[disabled] { color: #667387; }
.feedback { min-height: 70px; padding-top: 12px; }
.notice { display: block; color: #667387; font-size: 13px; line-height: 20px; }
.mock-failure { background: transparent; color: #667387; font-size: 12px; }.mock-failure::after { border: 0; }
</style>
