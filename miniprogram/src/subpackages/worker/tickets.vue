<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import BusinessPage from '@/components/BusinessPage.vue'
import BaseButton from '@/components/BaseButton.vue'
import PagedListState from '@/components/PagedListState.vue'
import { PagedQuery } from '@/features/common/paged-query'
import { listTickets } from '@/features/ticket/api'
import { ticketStateText, type Ticket } from '@/features/ticket/model'
import { errorText } from '@/features/order/model'
const state=reactive(new PagedQuery<Ticket>()); const filter=ref(''); let serial=0
async function load(more=false){if(more&&(state.loading||state.loadingMore||!state.hasMore))return;const current=++serial;if(more)state.loadingMore=true;else{state.reset();state.loading=true}try{const r=await listTickets({state:filter.value,page:more?state.page+1:1,pageSize:20});if(current===serial)(more?state.append(r):state.replace(r))}catch(e){if(current===serial)state.fail(errorText(e),more)}}
function changeFilter(event:{detail:{value:unknown}}){filter.value=['','OPEN','PROCESSING','CLOSED'][Number(event.detail.value)]||'';void load()}
function open(id:string){uni.navigateTo({url:`/subpackages/worker/ticket-detail?id=${encodeURIComponent(id)}`})}
onShow(()=>void load())
</script>
<template><BusinessPage title="我的工单" subtitle="仅显示本人提交的反馈"><view class="worker-stack"><picker :range="['全部','待受理','处理中','已关闭']" @change="changeFilter"><view class="worker-select">{{ticketStateText(filter||'全部')}}　▾</view></picker><PagedListState :loading="state.loading" :loading-more="state.loadingMore" :error="state.error" :has-content="!!state.items.length" :has-more="state.hasMore" empty-text="暂无工单" @retry="load()" @more="load(true)"><view v-for="item in state.items" :key="item.id" class="worker-card" @click="open(item.id)"><view class="worker-row"><text class="worker-heading">{{item.number}}</text><text class="worker-badge">{{ticketStateText(item.state)}}</text></view><text class="worker-muted">{{item.description}}</text><text class="worker-muted">订单 {{item.orderId}}</text></view></PagedListState></view></BusinessPage></template>
<style lang="scss">@import '@/styles/worker.scss';</style>
