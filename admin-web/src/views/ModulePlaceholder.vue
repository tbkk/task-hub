<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Clock } from '@element-plus/icons-vue'

const route = useRoute()
const title = computed(() => String(route.meta.title || '功能模块'))
const description = computed(() => String(route.meta.description || ''))
</script>

<template>
  <section class="module-page">
    <div class="page-heading">
      <p class="page-kicker">ADMINISTRATION</p>
      <h1>{{ title }}</h1>
      <p>{{ description }}</p>
    </div>
    <el-empty description="尚未实现" :image-size="88">
      <template #image><el-icon class="empty-icon"><Clock /></el-icon></template>
      <p class="empty-note">当前工程仅初始化模块边界，后续按确认需求接入服务端能力。</p>
    </el-empty>
  </section>
</template>
