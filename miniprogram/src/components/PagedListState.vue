<script setup lang="ts">
import BaseButton from './BaseButton.vue'
import StatePanel from './StatePanel.vue'
withDefaults(defineProps<{ loading: boolean; loadingMore?: boolean; error?: string; hasContent: boolean; hasMore?: boolean; emptyText?: string }>(), { loadingMore: false, error: '', hasMore: false, emptyText: '暂无数据' })
defineEmits<{ retry: []; more: [] }>()
</script>
<template><StatePanel v-if="loading && !hasContent" state="loading" title="" /><StatePanel v-else-if="error && !hasContent" state="error" title="加载失败" :description="error" retry @retry="$emit('retry')" /><StatePanel v-else-if="!hasContent" :title="emptyText" /><template v-else><slot /><text v-if="error" class="more-error" role="alert">{{ error }}</text><BaseButton v-if="hasMore" variant="plain" :loading="loadingMore" :disabled="loadingMore" @click="$emit('more')">{{ loadingMore ? '加载中…' : '加载更多' }}</BaseButton></template></template>
<style scoped>.more-error{display:block;margin:12px 0;color:#c72b33;font-size:13px;text-align:center}</style>
