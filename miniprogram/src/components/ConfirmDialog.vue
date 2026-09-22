<script setup lang="ts">
import BaseButton from './BaseButton.vue'
withDefaults(defineProps<{ open: boolean; title: string; message: string; confirmText?: string; cancelText?: string }>(), { confirmText: '确认', cancelText: '取消' })
defineEmits<{ confirm: []; cancel: [] }>()
</script>
<template><view v-if="open" class="overlay" @click.self="$emit('cancel')"><view class="dialog" role="dialog" aria-modal="true" :aria-label="title"><text class="title">{{ title }}</text><text class="message">{{ message }}</text><BaseButton @click="$emit('confirm')">{{ confirmText }}</BaseButton><BaseButton variant="plain" @click="$emit('cancel')">{{ cancelText }}</BaseButton></view></view></template>
<style scoped>.overlay { position: fixed; inset: 0; z-index: 100; display: flex; align-items: center; justify-content: center; padding: 24px; background: rgba(28,36,51,.35); }.dialog { width: 100%; max-width: 360px; display: flex; flex-direction: column; gap: 12px; padding: 24px; background: #f6f8fb; border-radius: 12px; }.title { font-size: 17px; font-weight: 600; }.message { margin: 4px 0 16px; font-size: 14px; line-height: 24px; color: #667387; }</style>
