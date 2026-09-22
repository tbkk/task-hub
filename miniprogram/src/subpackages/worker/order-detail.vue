<script setup lang="ts">
import {computed,reactive,ref} from 'vue'
import {onLoad,onShow} from '@dcloudio/uni-app'
import BusinessPage from '@/components/BusinessPage.vue'
import StatePanel from '@/components/StatePanel.vue'
import BaseButton from '@/components/BaseButton.vue'
import TimelineList from '@/components/TimelineList.vue'
import PagedListState from '@/components/PagedListState.vue'
import {refreshAccess} from '@/features/workspace/navigation'
import {currentUser} from '@/features/auth/session'
import {getOrder,getOrderHistory} from '@/features/order/api'
import {errorText,orderStatusText,cancellationText,canCancel,type Order,type History} from '@/features/order/model'
import {chinaTime,slotText} from '@/features/catalog/model'
import {PagedQuery} from '@/features/common/paged-query'
const id=ref(''),order=ref<Order|null>(null),loading=ref(true),error=ref(''),history=reactive(new PagedQuery<History>())
const timeline=computed(()=>history.items.map(event=>({id:event.id,title:event.summary || event.action,description:[event.actorName,event.reason].filter(Boolean).join(' · '),time:chinaTime(event.occurredAt)})))
async function loadHistory(more=false){if(history.loading || history.loadingMore)return;if(more)history.loadingMore=true;else history.loading=true;try{const result=await getOrderHistory(id.value,more?history.page+1:1);more?history.append(result):history.replace(result)}catch(e){history.fail(errorText(e),more)}}
async function load(){loading.value=true;error.value='';order.value=null;history.reset();try{if(!await refreshAccess(false,'worker'))return;order.value=await getOrder(id.value);await loadHistory()}catch(e){error.value=errorText(e)}finally{loading.value=false}}
onLoad(q=>{id.value=String(q?.id||'')})
onShow(()=>void load())
function cancel(){uni.navigateTo({url:`/subpackages/worker/cancellation?id=${encodeURIComponent(id.value)}`})}
function report(){uni.navigateTo({url:`/subpackages/worker/ticket-create?orderId=${encodeURIComponent(id.value)}`})}
</script>
<template><BusinessPage title="订单详情" subtitle="配送安排与处理进度"><view class="worker-stack"><StatePanel v-if="loading" title="" state="loading"/><StatePanel v-else-if="error" title="订单加载失败" :description="error" state="error" retry @retry="load"/><template v-else-if="order"><view class="worker-card"><view class="worker-row"><text class="worker-heading">{{order.description}}</text><text class="worker-badge">{{orderStatusText(order.status)}}</text></view><text class="worker-muted">订单 #{{order.number}}</text><view class="worker-divider"/><text class="worker-muted">货物大小：{{order.size}}</text><text class="worker-muted">所属仓库：{{order.warehouseName || order.warehouseId}}</text><text class="worker-muted">目的地：{{order.stopName || order.stopId}}</text><text class="worker-muted">预约时段：{{slotText({start:order.slotStart,end:order.slotEnd})}}</text><text class="worker-muted">申请人：{{order.applicantName || '本人申请/接收关联'}}</text><text class="worker-muted">接收人：{{order.receiverName}}　{{order.receiverPhone}}</text><text class="worker-muted">备注：{{order.remark || '无'}}</text></view><text v-if="!order.receiverBound" class="worker-note">接收人手机号尚未验证绑定，完成本人验证后才可取货。</text><view v-if="order.cancellation" class="worker-card"><text class="worker-heading">{{cancellationText(order.cancellation.status)}}</text><text class="worker-muted">申请原因：{{order.cancellation.reason}}</text><text class="worker-muted">{{order.cancellation.result || '由仓库处理；确认结果前保留原订单状态。'}}</text></view><BaseButton v-if="canCancel(order,currentUser?.id)" variant="secondary" @click="cancel">申请取消</BaseButton><BaseButton variant="secondary" @click="report">提交异常反馈</BaseButton><BaseButton variant="plain" @click="load">刷新订单</BaseButton><text class="worker-label">配送进度与业务履历</text><view class="worker-card"><PagedListState :loading="history.loading" :loading-more="history.loadingMore" :error="history.error" :has-content="!!history.items.length" :has-more="history.hasMore" empty-text="暂无业务履历" @retry="loadHistory()" @more="loadHistory(true)"><TimelineList :items="timeline"/></PagedListState></view></template></view></BusinessPage></template>
<style lang="scss">@import '@/styles/worker.scss';</style>
