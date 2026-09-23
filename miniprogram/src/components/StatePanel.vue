<script setup lang="ts">
withDefaults(defineProps<{ title: string; description?: string; state?: 'empty' | 'loading' | 'error'; retry?: boolean }>(), { state: 'empty', description: '', retry: false })
defineEmits<{ retry: [] }>()
</script>
<template><view class="state-panel" :role="state === 'error' ? 'alert' : 'status'"><text class="state-title">{{ state === 'loading' ? '正在加载…' : title }}</text><text class="state-description">{{ description }}</text><button v-if="retry" @click="$emit('retry')">重新加载</button></view></template>
<style scoped>
.state-panel { padding: 36px 20px; border-radius: 12px; background: white; text-align: center; }
.state-title { display: block; font-size: 16px; color: #1c2433; font-weight: 500; }
.state-description { display: block; margin-top: 12px; font-size: 13px; line-height: 22px; color: #667387; }
button { margin: 20px auto 0; color: #1677ff; font-size: 14px; background: #e8f2ff; }
button::after { border: 0; }
</style>
