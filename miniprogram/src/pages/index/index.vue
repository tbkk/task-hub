<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { refreshAccess, landingUrl } from '@/features/workspace/navigation'
onShow(async () => { if (await refreshAccess(true)) uni.reLaunch({ url: landingUrl() }) })
</script>
<template>
  <view />
</template>
