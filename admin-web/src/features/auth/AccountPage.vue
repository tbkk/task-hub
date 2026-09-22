<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { changePassword, logout, session } from './session'
const router = useRouter()
const busy = ref(false)
const error = ref('')
const currentPassword = ref('')
const newPassword = ref('')
const confirmation = ref('')
const user = computed(() => session.value?.user)
const phone = computed(() => user.value?.verifiedPhone?.replace(/^(\d{3})\d{4}(\d{4})$/, '$1 **** $2') || '尚未验证')
const roles = { worker: '工人', warehouse: '仓库人员', dispatch: '调度人员', overview: '业务概览' }
const capabilities = { EMPLOYEE_MANAGE: '人员与权限', MASTERDATA_MANAGE: '基础资料', RULE_MANAGE: '业务规则', INTEGRATION_MANAGE: '服务接入', REPORT_VIEW: '报表查看', REPORT_EXPORT: '报表导出', AUDIT_VIEW: '审计查询' }
async function exit() {
  try { await ElMessageBox.confirm('退出后需要重新验证身份，是否退出？', '退出登录', { confirmButtonText: '确认退出', cancelButtonText: '继续使用' }) } catch { return }
  busy.value = true; error.value = ''
  try { await logout(); await router.replace('/login') }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '退出结果待核对，请重试查询' }
  finally { busy.value = false }
}
async function updatePassword() {
  if (busy.value) return
  error.value = ''
  if (!currentPassword.value || newPassword.value.length < 12) { error.value = '请填写当前密码，新密码至少 12 个字符'; return }
  if (confirmation.value !== newPassword.value) { error.value = '两次新密码输入不一致'; return }
  if (newPassword.value === currentPassword.value) { error.value = '新密码不能与当前密码相同'; return }
  busy.value = true
  try { await changePassword(currentPassword.value, newPassword.value); currentPassword.value = ''; newPassword.value = ''; confirmation.value = ''; ElMessage.success('密码已更新，请重新登录'); await router.replace('/login') }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '修改失败，请重试' }
  finally { busy.value = false }
}
</script>
<template>
  <section class="module-page account-page"><header class="page-heading"><p class="page-kicker">ACCOUNT / 平台账户</p><h1>账户与会话</h1><p>查看当前身份和授权范围，权限由管理员维护。</p></header>
    <div class="account-content"><el-alert v-if="user?.mustChangePassword" type="warning" title="首次使用临时密码，请先修改密码后继续操作。" :closable="false" show-icon />
      <el-descriptions :column="1" border><el-descriptions-item label="姓名">{{ user?.name }}</el-descriptions-item><el-descriptions-item label="已验证手机">{{ phone }}</el-descriptions-item><el-descriptions-item label="内部标识">{{ user?.id }}</el-descriptions-item></el-descriptions>
      <h2>日常工作区</h2><p v-if="!user?.grants.length" class="muted">暂无日常业务角色</p><div v-for="grant in user?.grants" :key="grant.role" class="grant"><el-tag>{{ roles[grant.role] }}</el-tag><span>{{ grant.scope === 'ALL' ? '全部授权仓库' : grant.scope === 'SELF' ? '本人业务' : grant.warehouseIds.join('、') }}</span></div>
      <h2>管理平台能力</h2><div v-for="grant in user?.platformGrants" :key="grant.capability" class="grant"><el-tag type="info">{{ capabilities[grant.capability] }}</el-tag><span>{{ grant.scope === 'ALL' ? '全厂' : grant.warehouseIds.join('、') }}</span></div>
      <h2>修改密码</h2><form class="password-form" @submit.prevent="updatePassword"><label for="current-password">当前密码</label><el-input id="current-password" v-model="currentPassword" type="password" show-password autocomplete="current-password" :disabled="busy" /><label for="new-password">新密码（至少 12 个字符）</label><el-input id="new-password" v-model="newPassword" type="password" show-password autocomplete="new-password" :disabled="busy" /><label for="confirm-password">确认新密码</label><el-input id="confirm-password" v-model="confirmation" type="password" show-password autocomplete="new-password" :disabled="busy" /><el-button type="primary" native-type="submit" :loading="busy">保存新密码并重新登录</el-button></form>
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon role="alert" /><el-button class="logout-button" :disabled="busy" @click="exit">退出登录</el-button>
    </div>
  </section>
</template>
<style scoped>
.account-content{padding:28px 32px;max-width:900px}.account-content>.el-alert{margin-bottom:20px}h2{font-size:16px;margin:28px 0 16px}.grant{display:flex;align-items:center;gap:12px;margin:10px 0;font-size:13px;overflow-wrap:anywhere}.muted{font-size:14px;color:#66727f}.password-form{display:flex;flex-direction:column;gap:10px;max-width:420px;font-size:14px}.password-form button{margin-top:10px}.logout-button{margin-top:28px}@media(max-width:720px){.account-content{padding:20px}.grant{align-items:flex-start;flex-direction:column;gap:6px}}
</style>
