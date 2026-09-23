<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import BusinessPage from '@/components/BusinessPage.vue'
import BaseButton from '@/components/BaseButton.vue'
import StatePanel from '@/components/StatePanel.vue'
import { getTicket } from '@/features/ticket/api'
import { ticketStateText } from '@/features/ticket/model'
import { errorText } from '@/features/order/model'
const id=ref(''),ticket=ref<Awaited<ReturnType<typeof getTicket>>|null>(null),loading=ref(true),error=ref('')
const notes=computed(()=>ticket.value?.notes||[])
async function load(){loading.value=true;error.value='';try{ticket.value=await getTicket(id.value)}catch(e){error.value=errorText(e)}finally{loading.value=false}}
onLoad(q=>{id.value=String(q?.id||'')});onShow(()=>{if(id.value)void load()})
</script>
<template><BusinessPage title="工单详情" subtitle="反馈处理进度与说明"><view class="worker-stack"><StatePanel v-if="loading" state="loading" title=""/><StatePanel v-else-if="error" state="error" title="加载失败" :description="error" retry @retry="load"/><template v-else-if="ticket"><view class="worker-card"><view class="worker-row"><text class="worker-heading">{{ticket.number}}</text><text class="worker-badge">{{ticketStateText(ticket.state)}}</text></view><text class="worker-muted">关联订单：{{ticket.orderId}}</text><text class="worker-muted">{{ticket.description}}</text><text v-if="ticket.result" class="worker-note">处理结果：{{ticket.result}}</text></view><view v-for="note in notes" :key="note.id" class="worker-card"><text class="worker-muted">{{note.authorName}} · {{note.createdAt}}</text><text class="worker-body">{{note.text}}</text></view><BaseButton variant="plain" @click="load">刷新工单</BaseButton></template></view></BusinessPage></template>
<style lang="scss">@import '@/styles/worker.scss';</style>
