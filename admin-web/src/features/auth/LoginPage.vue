<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import { firstAllowedPath, login, resetSession } from './session'
const router = useRouter()
const username = ref('')
const password = ref('')
const busy = ref(false)
const error = ref('')
onBeforeUnmount(() => { if (busy.value) resetSession() })
async function submit() {
  if (busy.value) return
  error.value = ''
  if (!username.value.trim() || !password.value) { error.value = '请输入内部账号和密码'; return }
  busy.value = true
  try {
    await login(username.value, password.value)
    password.value = ''
    busy.value = false
    await router.replace(firstAllowedPath())
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '登录失败，请重试' }
  finally { busy.value = false }
}
</script>
<template>
  <main class="login-screen">
    <section class="login-intro"><span class="login-mark">TASK HUB / 管理平台</span><h1>让每一次配送<br>都有序可循。</h1><p>人员、配置、报表与系统管理</p><span class="login-note">日常业务请使用小程序工作区</span></section>
    <section class="login-card"><h2>内部账号登录</h2><p>仅限已开通管理平台权限的内部人员</p>
      <form @submit.prevent="submit"><label for="username">内部账号</label><el-input id="username" v-model="username" autocomplete="username" :disabled="busy" maxlength="64" placeholder="请输入内部账号" />
      <label for="password">密码</label><el-input id="password" v-model="password" type="password" autocomplete="current-password" :disabled="busy" show-password placeholder="请输入密码" />
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon role="alert" />
      <el-button type="primary" native-type="submit" :loading="busy" class="login-submit">{{ busy ? '正在登录' : '登录管理平台' }}</el-button></form>
      <p class="login-help">账号开通或密码重设，请联系系统管理员。</p>
    </section>
  </main>
</template>
<style scoped>
.login-screen{min-height:100vh;display:grid;grid-template-columns:1fr 1fr;background:#f3f5f7}.login-intro{background:#162a2a;color:white;display:flex;flex-direction:column;justify-content:center;padding:clamp(32px,7vw,112px)}.login-mark{color:#81d5b9;letter-spacing:2px;font-size:13px}.login-intro h1{font-family:"Songti SC",serif;font-size:clamp(32px,3.5vw,54px);line-height:1.5;font-weight:600;margin:38px 0 18px}.login-intro p{color:#c0d2ce;font-size:16px}.login-note{margin-top:70px;font-size:13px;color:#92afa7}.login-card{align-self:center;margin:48px auto;width:min(420px,calc(100% - 48px));padding:36px;background:white;border:1px solid #dce2e7;border-radius:8px}.login-card h2{margin:0 0 10px;font-size:25px}.login-card p{font-size:13px;color:#66727f;line-height:1.7}.login-card form{display:flex;flex-direction:column;gap:12px;margin-top:28px}.login-card label{font-size:14px;margin-top:8px}.login-submit{width:100%;margin-top:16px;height:44px}.login-help{margin-top:24px!important}@media(max-width:768px){.login-screen{grid-template-columns:1fr}.login-intro{padding:32px}.login-intro h1{margin:18px 0 8px;font-size:28px}.login-note{margin-top:16px}.login-card{margin:24px auto}}
</style>
