<script setup lang="ts">
import WorkspaceHome from '@/components/WorkspaceHome.vue'
import { useAccess } from '@/features/workspace/navigation'
useAccess(false, 'dispatch')
</script>
<template><WorkspaceHome role="dispatch" /></template>
