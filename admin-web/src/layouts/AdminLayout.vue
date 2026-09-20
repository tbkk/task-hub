<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Connection, DataAnalysis, DocumentChecked, Menu, Operation, Setting, User, UserFilled } from '@element-plus/icons-vue'

const route = useRoute()
const collapsed = ref(false)
const activePath = computed(() => route.path)
</script>

<template>
  <div class="admin-shell" :class="{ 'is-collapsed': collapsed }">
    <aside class="admin-sidebar">
      <div class="brand">
        <div class="brand-mark">TH</div>
        <div v-if="!collapsed" class="brand-copy"><strong>Task Hub</strong><span>管理平台</span></div>
      </div>
      <el-menu :default-active="activePath" router :collapse="collapsed" class="admin-menu">
        <el-menu-item index="/account"><el-icon><User /></el-icon><template #title>平台账户</template></el-menu-item>
        <el-menu-item index="/people"><el-icon><UserFilled /></el-icon><template #title>人员与权限</template></el-menu-item>
        <el-sub-menu index="master-data">
          <template #title><el-icon><DocumentChecked /></el-icon><span>基础资料</span></template>
          <el-menu-item index="/master-data/warehouses">仓库</el-menu-item>
          <el-menu-item index="/master-data/stops">停靠点</el-menu-item>
          <el-menu-item index="/master-data/vehicles">车辆</el-menu-item>
          <el-menu-item index="/master-data/compartments">硬件格口</el-menu-item>
        </el-sub-menu>
        <el-menu-item index="/rules"><el-icon><Operation /></el-icon><template #title>规则配置</template></el-menu-item>
        <el-menu-item index="/integrations"><el-icon><Connection /></el-icon><template #title>既有第三方接入</template></el-menu-item>
        <el-menu-item index="/reports"><el-icon><DataAnalysis /></el-icon><template #title>管理报表</template></el-menu-item>
        <el-menu-item index="/audit"><el-icon><Setting /></el-icon><template #title>审计与运行维护</template></el-menu-item>
      </el-menu>
    </aside>
    <section class="admin-workspace">
      <header class="admin-header">
        <el-button text circle aria-label="切换侧栏" @click="collapsed = !collapsed"><el-icon :size="20"><Menu /></el-icon></el-button>
        <span class="header-scope">系统管理与业务支撑</span>
      </header>
      <main class="admin-main"><RouterView /></main>
    </section>
  </div>
</template>
