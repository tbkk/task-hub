<script setup lang="ts">
import AppPage from '@/components/AppPage.vue'
import StatePanel from '@/components/StatePanel.vue'
import { currentUser } from '@/features/auth/session'
import { workspace } from '@/features/workspace/store'
import { useAccess } from '@/features/workspace/navigation'
useAccess()
</script>
<template><AppPage v-if="currentUser && workspace.activeRole" title="消息" subtitle="与本人业务有关的通知" :tab="2"><StatePanel title="消息功能建设中" description="订单与工单通知将在功能开放后显示。" /></AppPage></template>
