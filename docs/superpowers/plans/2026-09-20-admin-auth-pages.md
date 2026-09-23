# M01 管理登录与账户页面 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 提供真实登录、首次改密、退出确认和权限菜单。

**Architecture:** 复用 Element Plus 和会话模块；路由守卫每次核对 me；菜单共用 capability 元数据。页面只编排服务调用。

**Tech Stack:** Vue 3、TypeScript、Element Plus、Vue Router。

## Global Constraints

- 继续 feature/base；不保存密码或 token 到磁盘。
- 平台能力独立，无业务控制入口。
- 缺少后端不显示假登录成功。

---

### Task 1: 登录、账户与路由

**Files:** 见各完整代码块标题。

**Interfaces:** 消费 session.ts 的 login、logout、changePassword、can、refreshIdentity、firstAllowedPath；产出 /login、/account 和能力限制路由。

- [ ] **Step 1: 确认服务层测试通过**

`cd admin-web && npm test`；已有会话 tests 验证迟到登录、失败退出、独立报表能力。

- [ ] **Step 2: 写入页面与路由**

`admin-web/src/features/auth/LoginPage.vue`

```vue
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
```

`admin-web/src/features/auth/AccountPage.vue`

```vue
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
```

`admin-web/src/router/index.ts`

```ts
import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { ElMessage } from 'element-plus'
import { can, firstAllowedPath, moduleEntries, refreshIdentity, session, type Capability } from '@/features/auth/session'

const moduleRoutes: RouteRecordRaw[] = [
  { path: '', redirect: () => firstAllowedPath() },
  { path: 'account', name: 'account', component: () => import('@/features/auth/AccountPage.vue'), meta: { title: '平台账户' } },
  ...moduleEntries.map(entry => ({ path: entry.path.slice(1), component: () => import('@/views/ModulePlaceholder.vue'), meta: { title: entry.title, capability: entry.capability, description: '模块接入中' } })),
]
const router = createRouter({ history: createWebHistory(import.meta.env.BASE_URL), routes: [
  { path: '/login', component: () => import('@/features/auth/LoginPage.vue'), meta: { title: '内部账号登录', public: true } },
  { path: '/', component: () => import('@/layouts/AdminLayout.vue'), children: moduleRoutes },
  { path: '/:pathMatch(.*)*', redirect: '/account' },
] })
router.beforeEach(async to => {
  if (to.meta.public) return session.value ? firstAllowedPath() : true
  if (!session.value) return '/login'
  try { await refreshIdentity() } catch (cause) {
    if (!session.value) return '/login'
    ElMessage.error(cause instanceof Error ? cause.message : '身份校验失败，请重试')
    return false
  }
  if (!session.value) return '/login'
  if (session.value.user.mustChangePassword && to.path !== '/account') return '/account'
  if (to.meta.capability && !can(to.meta.capability as Capability)) return firstAllowedPath()
  return true
})
router.afterEach(to => { document.title = `${String(to.meta.title || '管理平台')} | Task Hub` })
export default router
```

`admin-web/src/layouts/AdminLayout.vue`

```vue
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Menu, User } from '@element-plus/icons-vue'
import { can, moduleEntries, session } from '@/features/auth/session'
const route = useRoute()
const router = useRouter()
const collapsed = ref(false)
const activePath = computed(() => route.path)
const entries = computed(() => session.value?.user.mustChangePassword ? [] : moduleEntries.filter(entry => can(entry.capability)))
watch(session, value => { if (!value) void router.replace('/login') })
</script>
<template>
  <div class="admin-shell" :class="{ 'is-collapsed': collapsed }">
    <aside class="admin-sidebar"><div class="brand"><div class="brand-mark">TH</div><div v-if="!collapsed" class="brand-copy"><strong>Task Hub</strong><span>管理平台</span></div></div>
      <el-menu :default-active="activePath" router :collapse="collapsed" class="admin-menu"><el-menu-item v-for="entry in entries" :key="entry.path" :index="entry.path"><el-icon><Menu /></el-icon><template #title>{{ entry.title }}</template></el-menu-item><el-menu-item index="/account"><el-icon><User /></el-icon><template #title>平台账户</template></el-menu-item></el-menu>
    </aside>
    <section class="admin-workspace"><header class="admin-header"><el-button text circle aria-label="切换侧栏" @click="collapsed = !collapsed"><el-icon :size="20"><Menu /></el-icon></el-button><span class="header-scope">系统管理与业务支撑</span><el-button text class="account-link" @click="router.push('/account')">{{ session?.user.name }}</el-button></header><main class="admin-main"><RouterView /></main></section>
  </div>
</template>
<style scoped>.account-link{margin-left:auto;max-width:180px;overflow:hidden;text-overflow:ellipsis}</style>
```

- [ ] **Step 3: 验证构建与浏览器交互**

运行 `cd admin-web && npm test && npm run build`。浏览器核验匿名深链接返回登录、必填错误、登录失败保留账号、只有 REPORT_VIEW 的登录首屏报表且无人员菜单、账户退出取消保留/确认退出返回登录、临时密码仅账户可用。接口先用测试响应覆盖组件交互，后端完成后真实联调，不将响应拦截当真实认证验收。

- [ ] **Step 4: Review**

检查密码输入无默认值、会话只在内存、route 403不无限循环、无越权导航入口，交付状态仍标其余模块占位。
