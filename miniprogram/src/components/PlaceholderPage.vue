<script setup lang="ts">
defineProps<{ title: string; description: string }>()
</script>

<template>
  <view class="page"><view class="brand-mark" /><view class="card"><text class="title">{{ title }}</text><text class="status">功能建设中</text><text class="description">{{ description }}</text></view></view>
</template>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;
.page { min-height: 100vh; padding: 72px 20px 32px; background: $color-background; }
.brand-mark { width: 36px; height: 6px; margin-bottom: 20px; border-radius: $radius-control; background: $color-primary; }
.card { display: flex; flex-direction: column; padding: 24px; border-radius: $radius-card; background: $color-card; }
.title { color: $color-text; font-size: 22px; font-weight: 600; }
.status { align-self: flex-start; margin-top: 18px; padding: 6px 10px; border-radius: $radius-control; background: rgba(22, 119, 255, 0.1); color: $color-primary; font-size: 14px; }
.description { margin-top: 14px; color: $color-text-secondary; font-size: 15px; line-height: 1.7; }
</style>
