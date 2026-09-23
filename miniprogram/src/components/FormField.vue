<script setup lang="ts">defineProps<{ label: string; inputId: string }>()</script>
<template><view class="form-field"><label class="field-label" :for="inputId">{{ label }}</label><view class="field-content"><slot /></view></view></template>
<style scoped>
.form-field { min-height: 72px; padding: 0 16px; border-radius: 10px; background: white; }.field-label { box-sizing: border-box; display: block; height: 36px; padding-top: 9px; border-bottom: 1px solid #e0e5f0; color: #667387; font-size: 13px; line-height: 20px; }.field-content { min-height: 36px; }
</style>
