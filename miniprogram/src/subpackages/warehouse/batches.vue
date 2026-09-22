<script setup lang="ts">
import {reactive,ref} from 'vue'
import {onShow} from '@dcloudio/uni-app'
import BusinessPage from '@/components/BusinessPage.vue'
import BaseButton from '@/components/BaseButton.vue'
import PagedListState from '@/components/PagedListState.vue'
import {PagedQuery} from '@/features/common/paged-query'
import {refreshAccess} from '@/features/workspace/navigation'
import {listBatches} from '@/features/batch/api'
import {batchStatusText,loadStatusText,type Batch} from '@/features/batch/model'
import {errorText} from '@/features/order/model'
import {slotText} from '@/features/catalog/model'
const state=reactive(new PagedQuery<Batch>()),status=ref(''),filters=[['','全部'],['DRAFT','待装货'],['READY','待发车'],['LOCKED','已锁定'],['CLOSED','已关闭']]
let generation=0
async function load(more=false){if(more&&(state.loading||state.loadingMore||!state.hasMore))return;const current=++generation;if(more)state.loadingMore=true;else{state.reset();state.loading=true}try{if(!await refreshAccess(false,'warehouse'))return;const result=await listBatches({status:status.value,page:more?state.page+1:1,pageSize:20});if(current!==generation)return;more?state.append(result):state.replace(result)}catch(e){if(current===generation)state.fail(errorText(e),more)}}
function change(e:{detail:{value:unknown}}){status.value=filters[Number(e.detail.value)][0];void load()}
function edit(id=''){uni.navigateTo({url:`/subpackages/warehouse/batch-edit${id?`?id=${encodeURIComponent(id)}`:''}`})}
onShow(()=>void load())
</script>
<template><BusinessPage title="配送批次列表" subtitle="批次状态与装货确认分别展示"><view class="warehouse-stack"><picker :range="filters.map(item=>item[1])" @change="change"><view class="warehouse-input-box">{{filters.find(item=>item[0]===status)?.[1]}}　▾</view></picker><BaseButton @click="edit()">创建配送批次</BaseButton><PagedListState :loading="state.loading" :loading-more="state.loadingMore" :error="state.error" :has-content="!!state.items.length" :has-more="state.hasMore" empty-text="暂无配送批次" @retry="load()" @more="load(true)"><view v-for="batch in state.items" :key="batch.id" class="warehouse-card" @click="edit(batch.id)"><view class="warehouse-row"><text class="warehouse-heading">{{batch.number}}</text><text class="warehouse-badge">{{batchStatusText(batch.status)}}</text></view><text class="warehouse-muted">{{batch.stopName||batch.stopId}}<template v-if="(batch.slotStart&&batch.slotEnd)||batch.orders?.[0]"> · {{slotText({start:batch.slotStart||batch.orders![0].slotStart,end:batch.slotEnd||batch.orders![0].slotEnd})}}</template></text><text class="warehouse-muted">{{batch.orderIds.length}} 单 · {{loadStatusText(batch.loadStatus)}}</text><text class="warehouse-link">查看批次　›</text></view></PagedListState></view></BusinessPage></template>
<style lang="scss">@import '@/styles/warehouse.scss';</style>
