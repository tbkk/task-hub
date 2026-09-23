<script setup lang="ts">withDefaults(defineProps<{ muted?: boolean }>(), { muted: false })</script>
<template><text class="status-tag" :class="{ muted }"><slot /></text></template>
<style scoped>.status-tag { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 12px; line-height: 20px; color: #1677ff; background: #e8f2ff; }.muted { color: #667387; background: #f6f8fb; }</style>
