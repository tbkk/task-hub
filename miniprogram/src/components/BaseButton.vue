<script setup lang="ts">
withDefaults(defineProps<{ variant?: 'primary' | 'secondary' | 'plain'; loading?: boolean; disabled?: boolean }>(), { variant: 'primary', loading: false, disabled: false })
defineEmits<{ click: [] }>()
</script>
<template><button class="base-button" :class="variant" :loading="loading" :disabled="disabled || loading" @click="$emit('click')"><slot /></button></template>
<style scoped>
.base-button { box-sizing: border-box; display: flex; align-items: center; justify-content: center; width: 100%; min-height: 48px; margin: 0; padding: 10px 16px; border: 1px solid #1677ff; border-radius: 8px; background: #1677ff; color: white; font-size: 16px; font-weight: 600; line-height: 26px; }
.base-button::after { border: 0; }
.secondary { color: #1677ff; background: white; font-size: 15px; font-weight: 500; }
.plain { background: white; border-color: transparent; color: #1c2433; font-size: 14px; font-weight: 400; }
.base-button[disabled] { background: #e0e5f0; border-color: #e0e5f0; color: #667387; }
</style>
