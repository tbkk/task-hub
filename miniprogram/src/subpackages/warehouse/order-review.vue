<script setup lang="ts">
import {reactive,ref} from 'vue'
import {onLoad,onShow} from '@dcloudio/uni-app'
import BusinessPage from '@/components/BusinessPage.vue'
import BaseButton from '@/components/BaseButton.vue'
import StatePanel from '@/components/StatePanel.vue'
import {refreshAccess} from '@/features/workspace/navigation'
import {getWarehouseOrder,reviewOrder,reviewCancellation} from '@/features/batch/api'
import {reviewIntent,isConflict,validateReview,type BatchOrder,type ReviewDecision,type CancellationDecision} from '@/features/batch/model'
import {Submission} from '@/features/order/submission'
import {errorText,orderStatusText,cancellationText} from '@/features/order/model'
import {slotText} from '@/features/catalog/model'
const id=ref(''),order=ref<BatchOrder|null>(null),loading=ref(true),error=ref(''),notice=ref(''),reason=ref('')
const mode=ref<'review'|'cancel'>('review'),decision=ref<ReviewDecision|CancellationDecision>('ACCEPT'),confirm=ref(false)
const submission=reactive(new Submission<{expectedVersion:number;decision:string;reason:string;cancellationId?:string}>())
const pendingReview=ref<ReturnType<typeof reviewIntent>|null>(null)
async function load(){if(submission.pending||submission.busy)return;loading.value=true;error.value='';try{if(!await refreshAccess(false,'warehouse'))return;order.value=await getWarehouseOrder(id.value) as BatchOrder}catch(e){error.value=errorText(e)}finally{loading.value=false}}
function ask(nextMode:'review'|'cancel',nextDecision:ReviewDecision|CancellationDecision){if(submission.pending||submission.busy)return;mode.value=nextMode;decision.value=nextDecision;reason.value='';confirm.value=true}
async function submit(){
 if(!order.value||submission.busy)return
 if(!pendingReview.value){
  const invalid=mode.value==='review'?validateReview(decision.value as ReviewDecision,reason.value):(!reason.value.trim()?'请填写处理原因':'')
  if(invalid){error.value=invalid;return}
  if(mode.value==='cancel'&&!order.value.cancellation){error.value='取消申请已变化，请刷新订单';return}
  pendingReview.value=reviewIntent(order.value,mode.value,decision.value,reason.value)
 }
 confirm.value=false;error.value='';notice.value=''
 try{
  order.value=await submission.run(pendingReview.value,(value,key)=>value.cancellationId?reviewCancellation(id.value,value.cancellationId,value.expectedVersion,value.decision as CancellationDecision,value.reason,key):reviewOrder(id.value,value.expectedVersion,value.decision as ReviewDecision,value.reason,key)) as BatchOrder
  notice.value=mode.value==='cancel'&&decision.value==='APPROVE'?'取消已批准。若订单曾进入批次，请重新核对批次成员和装货状态。':'处理成功';reason.value=''
 }catch(e){error.value=errorText(e);if(isConflict(e)){notice.value='订单或批次已被其他人更新，已刷新最新状态，请重新核对后确认。';await load()}}
 finally{if(!submission.pending)pendingReview.value=null}
}

onLoad(q=>{id.value=String(q?.id||'')});onShow(()=>void load())
</script>
<template><BusinessPage title="申请审批" subtitle="以最新订单状态作为处理依据"><view class="warehouse-stack"><StatePanel v-if="loading" state="loading" title=""/><StatePanel v-else-if="error&&!order" state="error" title="订单加载失败" :description="error" retry @retry="load"/><template v-else-if="order"><view class="warehouse-card"><view class="warehouse-row"><text class="warehouse-heading">{{order.description}}</text><text class="warehouse-badge">{{orderStatusText(order.status)}}</text></view><text class="warehouse-muted">订单 {{order.number}}</text><view class="warehouse-divider"/><text class="warehouse-muted">货物大小：{{order.size}}</text><text class="warehouse-muted">申请人：{{order.applicantName||order.applicantId}}</text><text class="warehouse-muted">接收人：{{order.receiverName}}　{{order.receiverPhone}}</text><text class="warehouse-muted">目的地：{{order.stopName||order.stopId}}</text><text class="warehouse-muted">预约：{{slotText({start:order.slotStart,end:order.slotEnd})}}</text><text class="warehouse-muted">备注：{{order.remark||'无'}}</text></view><view v-if="order.cancellation" class="warehouse-card"><text class="warehouse-heading">{{cancellationText(order.cancellation.status)}}</text><text class="warehouse-muted">申请原因：{{order.cancellation.reason}}</text><text v-if="order.cancellation.result" class="warehouse-muted">处理结果：{{order.cancellation.result}}</text><template v-if="order.allowedActions.includes('CANCELLATION_REVIEW')"><BaseButton :disabled="submission.pending || submission.busy" @click="ask('cancel','APPROVE')">批准取消</BaseButton><BaseButton variant="secondary" :disabled="submission.pending || submission.busy" @click="ask('cancel','REJECT')">不批准取消</BaseButton></template></view><text v-if="notice" class="warehouse-note">{{notice}}</text><text v-if="error" class="warehouse-error">{{error}}</text><template v-if="order.allowedActions.includes('ORDER_REVIEW')"><BaseButton :loading="submission.busy" :disabled="submission.pending" @click="ask('review','ACCEPT')">受理申请</BaseButton><BaseButton variant="secondary" :disabled="submission.pending || submission.busy" @click="ask('review','REJECT')">驳回并填写原因</BaseButton></template><view v-if="submission.pending" class="warehouse-note">上次提交结果尚未确认，请重试同一次操作。</view><BaseButton v-if="submission.pending" :loading="submission.busy" @click="submit">重试原操作</BaseButton><BaseButton variant="plain" :disabled="submission.pending || submission.busy" @click="load">刷新订单</BaseButton></template></view><view v-if="confirm" class="warehouse-overlay" @click.self="confirm=false"><view class="warehouse-reason"><text class="warehouse-heading">{{decision==='ACCEPT'?'确认受理申请？':decision==='APPROVE'?'确认批准取消？':mode==='cancel'?'确认不批准取消？':'确认驳回申请？'}}</text><text class="warehouse-muted">提交时将使用当前页面显示的最新版本。</text><textarea v-if="decision!=='ACCEPT'" v-model="reason" maxlength="1000" placeholder="填写处理原因"/><BaseButton @click="submit">确认提交</BaseButton><BaseButton variant="plain" @click="confirm=false">返回核对</BaseButton></view></view></BusinessPage></template>
<style lang="scss">@import '@/styles/warehouse.scss';</style>
