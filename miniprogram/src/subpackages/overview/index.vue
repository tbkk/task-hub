<script setup lang="ts">
import WorkspaceHome from '@/components/WorkspaceHome.vue'
import { useAccess } from '@/features/workspace/navigation'
useAccess(false, 'overview')
</script>
<template><WorkspaceHome role="overview" /></template>
