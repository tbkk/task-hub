<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import BusinessPage from '@/components/BusinessPage.vue'
import BaseButton from '@/components/BaseButton.vue'
import StatePanel from '@/components/StatePanel.vue'
import { confirmPickup } from '@/features/pickup/api'
import { newIdempotencyKey } from '@/features/common/idempotency'
import { controlStatusText } from '@/features/pickup/model'
const orderId=ref(''),number=ref(''),receiver=ref(''),version=ref(0),requestStatus=ref(''),busy=ref(false),error=ref(''),done=ref(false)
function back(){uni.navigateBack()}
onLoad(q=>{orderId.value=String(q?.orderId||'');number.value=String(q?.number||'');receiver.value=String(q?.receiver||'');version.value=Number(q?.version||0);requestStatus.value=String(q?.request||'')})
async function confirm(){if(busy.value||done.value)return;busy.value=true;error.value='';try{await confirmPickup(orderId.value,version.value,newIdempotencyKey());done.value=true;uni.showToast({title:'取货已确认',icon:'success'})}catch(e){error.value=e instanceof Error?e.message:'确认失败，请刷新后重试'}finally{busy.value=false}}
</script>
<template><BusinessPage title="确认取货" subtitle="确认后仅完成当前订单，不影响同车其他订单"><view class="worker-stack"><view class="worker-card"><text class="worker-heading">订单 {{number}}</text><text class="worker-muted">接收人：{{receiver}}</text><text v-if="requestStatus" class="worker-note">开门状态：{{controlStatusText(requestStatus)}}</text></view><StatePanel v-if="done" title="取货已确认" description="可以返回扫码页继续处理其他订单。"/><StatePanel v-else-if="error" state="error" title="确认失败" :description="error" retry @retry="confirm"/><template v-else><text class="worker-muted">请确认已取出当前订单货物，再提交取货事实。</text><BaseButton :loading="busy" @click="confirm">确认已取货</BaseButton></template><BaseButton variant="plain" @click="back">返回扫码页</BaseButton></view></BusinessPage></template>
<style lang="scss">@import '@/styles/worker.scss';</style>
