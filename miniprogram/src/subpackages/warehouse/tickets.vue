<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import BusinessPage from '@/components/BusinessPage.vue'
import PagedListState from '@/components/PagedListState.vue'
import { PagedQuery } from '@/features/common/paged-query'
import { listTickets } from '@/features/ticket/api'
import { ticketStateText, type Ticket } from '@/features/ticket/model'
import { errorText } from '@/features/order/model'
const state=reactive(new PagedQuery<Ticket>());const filter=ref('');let serial=0
async function load(more=false){if(more&&(state.loading||state.loadingMore||!state.hasMore))return;const current=++serial;if(more)state.loadingMore=true;else{state.reset();state.loading=true}try{const result=await listTickets({state:filter.value,page:more?state.page+1:1,pageSize:20});if(current===serial)(more?state.append(result):state.replace(result))}catch(e){if(current===serial)state.fail(errorText(e),more)}}
function changeFilter(event:{detail:{value:unknown}}){filter.value=['','OPEN','PROCESSING','CLOSED'][Number(event.detail.value)]||'';void load()}
function open(id:string){uni.navigateTo({url:`/subpackages/warehouse/ticket-detail?id=${encodeURIComponent(id)}`})}
onShow(()=>void load())
</script>
<template><BusinessPage title="工单处理列表" subtitle="受理并跟进授权仓库范围内的异常反馈"><view class="warehouse-stack"><picker :range="['全部','待受理','处理中','已关闭']" @change="changeFilter"><view class="warehouse-input-box">{{ticketStateText(filter||'全部')}}　▾</view></picker><PagedListState :loading="state.loading" :loading-more="state.loadingMore" :error="state.error" :has-content="!!state.items.length" :has-more="state.hasMore" empty-text="暂无工单" @retry="load()" @more="load(true)"><view v-for="item in state.items" :key="item.id" class="warehouse-card" @click="open(item.id)"><view class="warehouse-row"><text class="warehouse-heading">{{item.number}}</text><text class="warehouse-badge">{{ticketStateText(item.state)}}</text></view><text class="warehouse-muted">订单 {{item.orderId}}</text><text class="warehouse-muted">{{item.description}}</text></view></PagedListState></view></BusinessPage></template>
<style lang="scss">@import '@/styles/warehouse.scss';</style>
