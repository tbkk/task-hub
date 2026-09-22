<script setup lang="ts">
import {reactive,ref} from 'vue'
import {onShow} from '@dcloudio/uni-app'
import AppPage from '@/components/AppPage.vue'
import BaseButton from '@/components/BaseButton.vue'
import PagedListState from '@/components/PagedListState.vue'
import {refreshAccess} from '@/features/workspace/navigation'
import {PagedQuery} from '@/features/common/paged-query'
import {listOrders} from '@/features/order/api'
import {orderStatuses,orderStatusText,cancellationText,errorText,type Order} from '@/features/order/model'
import {slotText} from '@/features/catalog/model'
const state=reactive(new PagedQuery<Order>()),keyword=ref(''),status=ref(''),filters=[['','全部'],...Object.entries(orderStatuses)]
let generation=0
async function load(more=false){if(more && (state.loading || state.loadingMore || !state.hasMore))return;const sequence=++generation;if(more)state.loadingMore=true;else{state.reset();state.loading=true}try{if(!await refreshAccess(false,'worker'))return;const result=await listOrders({status:status.value,keyword:keyword.value.trim(),page:more?state.page+1:1,pageSize:20});if(sequence!==generation)return;more?state.append(result):state.replace(result)}catch(e){if(sequence===generation)state.fail(errorText(e),more)}}
function change(event:{detail:{value:unknown}}){status.value=filters[Number(event.detail.value)][0];void load()}
function open(id:string){uni.navigateTo({url:`/subpackages/worker/order-detail?id=${encodeURIComponent(id)}`})}
onShow(()=>void load())
</script>
<template><AppPage title="我的订单" subtitle="仅展示本人申请或接收的订单" :tab="1"><view class="worker-stack"><input v-model="keyword" class="worker-select" placeholder="搜索订单或物料" aria-label="搜索订单或物料" @confirm="load()"/><view class="worker-controls"><picker :range="filters.map(f=>f[1])" @change="change"><view class="worker-select">{{filters.find(f=>f[0]===status)?.[1]}}　▾</view></picker><BaseButton variant="secondary" :loading="state.loading" @click="load()">查询 / 刷新</BaseButton></view><text v-if="!state.loading" class="worker-muted">共 {{state.total}} 个订单</text><PagedListState :loading="state.loading" :loading-more="state.loadingMore" :error="state.error" :has-content="!!state.items.length" :has-more="state.hasMore" empty-text="暂无符合条件的订单" @retry="load()" @more="load(true)"><view v-for="order in state.items" :key="order.id" class="worker-card" @click="open(order.id)"><view class="worker-row"><text class="worker-heading">{{order.description}}</text><text class="worker-badge">{{orderStatusText(order.status)}}</text></view><text class="worker-muted">{{order.stopName || order.stopId}} · {{slotText({start:order.slotStart,end:order.slotEnd})}}</text><text class="worker-muted">订单 #{{order.number}} · {{order.relation==='RECEIVER'?'接收人':order.relation==='BOTH'?'申请人 / 接收人':'申请人'}}</text><text v-if="order.cancellation" class="worker-note">{{cancellationText(order.cancellation.status)}}</text><view class="worker-divider"/><text class="worker-link">查看详情　›</text></view></PagedListState></view></AppPage></template>
<style lang="scss">@import '@/styles/worker.scss';</style>
