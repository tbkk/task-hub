<script setup lang="ts">
import {reactive,ref} from 'vue'
import {onLoad} from '@dcloudio/uni-app'
import BusinessPage from '@/components/BusinessPage.vue'
import StatePanel from '@/components/StatePanel.vue'
import FormField from '@/components/FormField.vue'
import BaseButton from '@/components/BaseButton.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import {refreshAccess} from '@/features/workspace/navigation'
import {currentUser} from '@/features/auth/session'
import {getOrder,requestCancellation} from '@/features/order/api'
import {errorText,orderStatusText,cancellationText,canCancel,type Order} from '@/features/order/model'
import {Submission} from '@/features/order/submission'
import {ApiRequestError} from '@/services/request'
const id=ref(''),order=ref<Order|null>(null),reason=ref(''),loading=ref(true),loadError=ref(''),error=ref(''),confirm=ref(false),done=ref(false),submission=reactive(new Submission<{expectedVersion:number;reason:string}>())
async function load(){loading.value=true;loadError.value='';try{if(await refreshAccess(false,'worker'))order.value=await getOrder(id.value)}catch(e){loadError.value=errorText(e)}finally{loading.value=false}}
onLoad(q=>{id.value=String(q?.id||'');void load()})
function ask(){if(submission.busy)return;if(!reason.value.trim()){error.value='请填写取消原因';return}if(reason.value.length>500){error.value='取消原因最多 500 字';return}confirm.value=true}
async function submit(){confirm.value=false;if(!order.value || submission.busy)return;error.value='';try{order.value=await submission.run({expectedVersion:order.value.version,reason:reason.value.trim()},(body,key)=>requestCancellation(id.value,body.expectedVersion,body.reason,key));done.value=true}catch(e){error.value=errorText(e);if(e instanceof ApiRequestError && e.code===40902){await load();error.value='订单已更新，请核对最新状态后重新确认'}}}
function detail(){uni.redirectTo({url:`/subpackages/worker/order-detail?id=${encodeURIComponent(id.value)}`})}
</script>
<template><BusinessPage title="取消申请" subtitle="申请由仓库处理，结果确认前保留原订单状态"><view class="worker-stack"><StatePanel v-if="loading" title="" state="loading"/><StatePanel v-else-if="loadError" title="订单加载失败" :description="loadError" state="error" retry @retry="load"/><template v-else-if="order"><view class="worker-card"><text class="worker-heading">{{order.description}}</text><text class="worker-muted">订单 #{{order.number}}</text><text class="worker-badge">{{orderStatusText(order.status)}}</text></view><view v-if="done || !canCancel(order,currentUser?.id)" class="worker-note">{{order.cancellation?cancellationText(order.cancellation.status):'当前订单不可申请取消'}}<text class="worker-muted">{{order.cancellation?.result || '订单主状态以最新详情为准。'}}</text></view><template v-else><FormField label="取消原因" input-id="cancel-reason"><textarea id="cancel-reason" v-model="reason" class="worker-textarea" placeholder="请填写取消原因" :disabled="submission.pending" :maxlength="500"/></FormField><text v-if="error" class="worker-error" role="alert">{{error}}</text><text v-if="submission.pending && !submission.busy" class="worker-note">提交结果未确认，请重试同一次取消申请。</text><BaseButton :loading="submission.busy" @click="ask">{{submission.pending?'重试取消申请':'申请取消'}}</BaseButton></template><BaseButton variant="secondary" @click="detail">查看订单</BaseButton></template><ConfirmDialog :open="confirm" title="确认取消本次配送申请？" message="提交后由仓库处理，原订单状态会保留直到结果确认。不会自动取消车辆任务。" confirm-text="确认取消" cancel-text="再想想" @confirm="submit" @cancel="confirm=false"/></view></BusinessPage></template>
<style lang="scss">@import '@/styles/worker.scss';</style>
