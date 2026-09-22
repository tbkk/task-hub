<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppPage from './AppPage.vue'
import StatePanel from './StatePanel.vue'
import StatusTag from './StatusTag.vue'
import { workspaces, type Role } from '@/features/workspace/model'
import { workspace } from '@/features/workspace/store'
const props = defineProps<{ role: Role }>()
const secondary = ref(false)
onLoad(query => { secondary.value = query?.tab === 'secondary' })
const title = computed(() => workspaces[props.role].tabs[secondary.value ? 1 : 0])
</script>
<template><AppPage v-if="workspace.activeRole === role" :title="title" :subtitle="workspace.activeGrant?.scope" :tab="secondary ? 1 : 0"><view class="scope"><StatusTag>{{ workspaces[role].name }}</StatusTag></view><StatePanel :title="`${title}功能建设中`" description="业务功能开放后，可在这里查看和处理。" /></AppPage></template>
<style scoped>.scope { margin-bottom: 16px; }</style>
