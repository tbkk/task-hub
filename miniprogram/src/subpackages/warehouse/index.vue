<script setup lang="ts">
import {reactive,ref} from 'vue'
import {onShow} from '@dcloudio/uni-app'
import AppPage from '@/components/AppPage.vue'
import BaseButton from '@/components/BaseButton.vue'
import PagedListState from '@/components/PagedListState.vue'
import {PagedQuery} from '@/features/common/paged-query'
import {refreshAccess} from '@/features/workspace/navigation'
import {listWarehouseOrders} from '@/features/batch/api'
import type {BatchOrder} from '@/features/batch/model'
import {errorText,orderStatusText} from '@/features/order/model'
import {slotText} from '@/features/catalog/model'
const state=reactive(new PagedQuery<BatchOrder>()),status=ref('PENDING'),keyword=ref('')
const filters=[['PENDING','待受理'],['ACCEPTED','已受理'],['','全部']]
let generation=0
async function load(more=false){if(more&&(state.loading||state.loadingMore||!state.hasMore))return;const current=++generation;if(more)state.loadingMore=true;else{state.reset();state.loading=true}try{if(!await refreshAccess(false,'warehouse'))return;const result=await listWarehouseOrders({status:status.value,keyword:keyword.value.trim(),page:more?state.page+1:1,pageSize:20});if(current!==generation)return;more?state.append(result):state.replace(result)}catch(e){if(current===generation)state.fail(errorText(e),more)}}
function choose(next:string){status.value=next;void load()}
function open(id:string){uni.navigateTo({url:`/subpackages/warehouse/order-review?id=${encodeURIComponent(id)}`})}
function batches(){uni.navigateTo({url:'/subpackages/warehouse/batches'})}
onShow(()=>void load())
</script>
<template><AppPage title="仓库工作台" subtitle="审批申请并组织配送批次" :tab="0"><view class="warehouse-stack"><view class="warehouse-summary"><text class="warehouse-heading">当前待办</text><text class="warehouse-muted">{{filters.find(item=>item[0]===status)?.[1]}} {{state.total}} 单</text><BaseButton variant="secondary" @click="batches">查看配送批次</BaseButton></view><input v-model="keyword" class="warehouse-input-box" placeholder="搜索订单或物料" @confirm="load()"/><view class="warehouse-tabs"><button v-for="item in filters" :key="item[0]" :class="{active:status===item[0]}" @click="choose(item[0])">{{item[1]}}</button></view><PagedListState :loading="state.loading" :loading-more="state.loadingMore" :error="state.error" :has-content="!!state.items.length" :has-more="state.hasMore" empty-text="暂无符合条件的订单" @retry="load()" @more="load(true)"><view v-for="order in state.items" :key="order.id" class="warehouse-card" @click="open(order.id)"><view class="warehouse-row"><text class="warehouse-heading">{{order.description}}</text><text class="warehouse-badge">{{orderStatusText(order.status)}}</text></view><text class="warehouse-muted">订单 {{order.number}} · {{order.stopName||order.stopId}}</text><text class="warehouse-muted">{{slotText({start:order.slotStart,end:order.slotEnd})}}</text><text class="warehouse-muted">{{order.size}} · 接收人 {{order.receiverName}}</text><text v-if="order.cancellation?.status==='PENDING'" class="warehouse-warning">有待处理取消申请</text></view></PagedListState></view></AppPage></template>
<style lang="scss">@import '@/styles/warehouse.scss';</style>
