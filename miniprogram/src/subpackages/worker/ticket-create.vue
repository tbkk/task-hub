<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import BusinessPage from '@/components/BusinessPage.vue'
import BaseButton from '@/components/BaseButton.vue'
import { createTicket } from '@/features/ticket/api'
import { Submission } from '@/features/order/submission'
const orderId=ref(''),description=ref(''),error=ref('');const submission=reactive(new Submission<{orderId:string;description:string}>())
onLoad(q=>{orderId.value=String(q?.orderId||'')})
async function submit(){if(!description.value.trim()){error.value='请填写反馈说明';return}if(submission.busy)return;error.value='';try{const ticket=await submission.run({orderId:orderId.value,description:description.value.trim()},(value,key)=>createTicket(value.orderId,value.description,key));uni.redirectTo({url:`/subpackages/worker/ticket-detail?id=${encodeURIComponent(ticket.id)}`})}catch(e){error.value=e instanceof Error?e.message:'提交失败，请重试'}}
</script>
<template><BusinessPage title="提交反馈" subtitle="工单不会修改订单或车辆状态"><view class="worker-stack"><text class="worker-muted">关联订单：{{orderId}}</text><textarea v-model="description" class="worker-input" :disabled="submission.pending" maxlength="2000" placeholder="请描述异常或需要协助的事项"/><text v-if="error" class="worker-error">{{error}}</text><text v-if="submission.pending&&!submission.busy" class="worker-note">提交结果待核对，请重试同一工单请求。</text><BaseButton :loading="submission.busy" @click="submit">{{submission.pending?'重试同一请求':'提交反馈'}}</BaseButton></view></BusinessPage></template>
<style lang="scss">@import '@/styles/worker.scss';</style>
