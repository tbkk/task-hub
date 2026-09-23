<script setup lang="ts">
import AppPage from './AppPage.vue'
withDefaults(defineProps<{ title: string; subtitle?: string; back?: boolean }>(), { subtitle: '', back: true })
function goBack() { getCurrentPages().length > 1 ? uni.navigateBack() : uni.reLaunch({ url: '/pages/index/index' }) }
</script>
<template><AppPage :title="title" :subtitle="subtitle" :navigation="false"><button v-if="back" class="back" aria-label="返回" @click="goBack">‹</button><slot /></AppPage></template>
<style scoped>.back{position:absolute;top:calc(env(safe-area-inset-top) + 12px);left:8px;width:40px;height:40px;margin:0;padding:0;color:#1c2433;background:transparent;font-size:32px;line-height:36px}.back::after{border:0}:deep(.greeting){padding-left:32px}</style>
