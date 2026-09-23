<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import BusinessPage from '@/components/BusinessPage.vue'
import BaseButton from '@/components/BaseButton.vue'
import { createTicket } from '@/features/ticket/api'
import { newIdempotencyKey } from '@/features/common/idempotency'
const orderId=ref(''),description=ref(''),busy=ref(false),error=ref('')
onLoad(q=>{orderId.value=String(q?.orderId||'')})
async function submit(){if(!description.value.trim()){error.value='请填写反馈说明';return}if(busy.value)return;busy.value=true;error.value='';try{const ticket=await createTicket(orderId.value,description.value.trim(),newIdempotencyKey());uni.redirectTo({url:`/subpackages/worker/ticket-detail?id=${encodeURIComponent(ticket.id)}`})}catch(e){error.value=e instanceof Error?e.message:'提交失败，请重试'}finally{busy.value=false}}
</script>
<template><BusinessPage title="提交反馈" subtitle="工单不会修改订单或车辆状态"><view class="worker-stack"><text class="worker-muted">关联订单：{{orderId}}</text><textarea v-model="description" class="worker-input" maxlength="2000" placeholder="请描述异常或需要协助的事项"/><text v-if="error" class="worker-error">{{error}}</text><BaseButton :loading="busy" @click="submit">提交反馈</BaseButton></view></BusinessPage></template>
<style lang="scss">@import '@/styles/worker.scss';</style>
