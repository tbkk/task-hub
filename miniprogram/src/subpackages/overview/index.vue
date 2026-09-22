<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import AppPage from '@/components/AppPage.vue'
import BaseButton from '@/components/BaseButton.vue'
import StatePanel from '@/components/StatePanel.vue'
import { getOverview, type Overview } from '@/features/overview/api'
import { errorText } from '@/features/order/model'
import { useAccess } from '@/features/workspace/navigation'
useAccess(false, 'overview')
const overview=ref<Overview|null>(null),loading=ref(true),error=ref(''),date=ref('')
async function load(){loading.value=true;error.value='';try{overview.value=await getOverview(date.value||undefined)}catch(e){error.value=errorText(e)}finally{loading.value=false}}
onShow(()=>void load())
</script>
<template><AppPage title="业务概览" subtitle="只读查看授权范围内的当日业务" :tab="0"><view class="overview-stack"><view class="overview-filter"><input v-model="date" class="overview-date" type="date" aria-label="统计日期"/><BaseButton variant="secondary" :loading="loading" @click="load">刷新</BaseButton></view><StatePanel v-if="loading" state="loading" title=""/><StatePanel v-else-if="error" state="error" title="加载失败" :description="error" retry @retry="load"/><template v-else-if="overview"><text class="overview-date-label">统计日期：{{overview.date}}</text><view class="overview-grid"><view class="overview-card"><text class="overview-title">订单</text><view v-for="(value,key) in overview.orderCounts" :key="key" class="overview-row"><text>{{key}}</text><text>{{value}}</text></view></view><view class="overview-card"><text class="overview-title">批次</text><view v-for="(value,key) in overview.batchCounts" :key="key" class="overview-row"><text>{{key}}</text><text>{{value}}</text></view></view><view class="overview-card"><text class="overview-title">任务</text><view v-for="(value,key) in overview.taskCounts" :key="key" class="overview-row"><text>{{key}}</text><text>{{value}}</text></view></view><view class="overview-card"><text class="overview-title">活动工单</text><text class="overview-number">{{overview.openTickets}}</text></view></view></template></view></AppPage></template>
<style scoped>.overview-stack{display:flex;flex-direction:column;gap:16px}.overview-filter{display:flex;gap:8px;align-items:center}.overview-date{flex:1;min-width:0;padding:12px;background:#fff;border-radius:8px;color:#1c2433}.overview-date-label{color:#667387;font-size:13px}.overview-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.overview-card{padding:16px;background:#fff;border-radius:8px;min-width:0}.overview-title{display:block;font-weight:700;margin-bottom:10px}.overview-row{display:flex;justify-content:space-between;gap:8px;color:#667387;font-size:13px;line-height:24px}.overview-number{display:block;font-size:32px;font-weight:700;color:#1677ff}</style>
