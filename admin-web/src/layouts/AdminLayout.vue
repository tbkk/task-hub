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
