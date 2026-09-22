<script setup lang="ts">
import {computed,reactive,ref} from 'vue'
import {onLoad,onShow} from '@dcloudio/uni-app'
import BusinessPage from '@/components/BusinessPage.vue'
import BaseButton from '@/components/BaseButton.vue'
import StatePanel from '@/components/StatePanel.vue'
import {refreshAccess} from '@/features/workspace/navigation'
import {createBatch,getBatch,listWarehouseOrders,replaceBatchOrders} from '@/features/batch/api'
import {batchStatusText,canAddToBatch,canEditBatch,isConflict,sameBatchGroup,type Batch,type BatchOrder} from '@/features/batch/model'
import {Submission} from '@/features/order/submission'
import {errorText} from '@/features/order/model'
import {slotText} from '@/features/catalog/model'
const id=ref(''),batch=ref<Batch|null>(null),orders=ref<BatchOrder[]>([]),selected=ref<string[]>([]),loading=ref(true),error=ref(''),notice=ref(''),selecting=ref(true)
const submission=reactive(new Submission<{batchId?:string;expectedVersion?:number;orderIds:string[]}>())
const candidatesPage=ref(1),candidatesTotal=ref(0),loadingMore=ref(false)
const pendingBatch=ref<{batchId?:string;expectedVersion?:number;orderIds:string[]}|null>(null)
const selectedOrders=computed(()=>orders.value.filter(order=>selected.value.includes(order.id)))
const editable=computed(()=>!batch.value||canEditBatch(batch.value))
function available(order:BatchOrder){if(selected.value.includes(order.id))return true;if(!canAddToBatch(order))return false;return sameBatchGroup([...selectedOrders.value,order])}
function toggle(order:BatchOrder){if(submission.pending||submission.busy||!editable.value||!available(order))return;if(!selecting.value){selecting.value=true;notice.value='已重新进入批次成员编辑，请再次选择订单。';return}selected.value=selected.value.includes(order.id)?selected.value.filter(value=>value!==order.id):[...selected.value,order.id]}
async function load(){if(submission.pending||submission.busy)return;loading.value=true;error.value='';try{if(!await refreshAccess(false,'warehouse'))return;batch.value=id.value?await getBatch(id.value):null;const result=await listWarehouseOrders({status:'ACCEPTED',page:1,pageSize:100});orders.value=result.items;candidatesPage.value=1;candidatesTotal.value=result.total;selected.value=batch.value?[...batch.value.orderIds]:[];for(const member of batch.value?.orders||[])if(!orders.value.some(order=>order.id===member.id))orders.value.push(member)}catch(e){error.value=errorText(e)}finally{loading.value=false}}
async function more(){
 if(loadingMore.value||submission.pending||candidatesPage.value*100>=candidatesTotal.value)return
 loadingMore.value=true;error.value=''
 try{const result=await listWarehouseOrders({status:'ACCEPTED',page:candidatesPage.value+1,pageSize:100});for(const item of result.items)if(!orders.value.some(order=>order.id===item.id))orders.value.push(item);candidatesPage.value=result.page;candidatesTotal.value=result.total}catch(e){error.value=errorText(e)}finally{loadingMore.value=false}
}
async function save(){if(submission.busy)return;if(!selected.value.length){error.value='请至少选择一个订单';return}if(!sameBatchGroup(selectedOrders.value)){error.value='只能合并同仓库、同目的地、同预约时段的已受理订单';return}error.value='';notice.value='';pendingBatch.value??={batchId:batch.value?.id,expectedVersion:batch.value?.version,orderIds:[...selected.value]};try{const saved=await submission.run(pendingBatch.value,(value,key)=>value.batchId?replaceBatchOrders({id:value.batchId,version:value.expectedVersion!},value.orderIds,key):createBatch(value.orderIds,key));batch.value=saved;id.value=saved.id;selected.value=[...saved.orderIds];notice.value='批次已保存。成员变化会清空原装货分配，需要重新确认装货。'}catch(e){error.value=errorText(e);if(isConflict(e)){selecting.value=false;notice.value='订单或批次已被其他人更新，已退出选择并刷新最新状态。';await load()}}finally{if(!submission.pending)pendingBatch.value=null}}
onLoad(q=>{id.value=String(q?.id||'')});onShow(()=>void load())
</script>
<template><BusinessPage :title="batch?'批次编辑':'合单与批次编辑'" subtitle="仅可合并同仓、同点、同时段的已受理订单"><view class="warehouse-stack"><StatePanel v-if="loading" state="loading" title=""/><StatePanel v-else-if="error&&!orders.length" state="error" title="加载失败" :description="error" retry @retry="load"/><template v-else><view v-if="batch" class="warehouse-summary"><view class="warehouse-row"><text class="warehouse-heading">{{batch.number}}</text><text class="warehouse-badge">{{batchStatusText(batch.status)}}</text></view><text class="warehouse-muted">{{batch.stopName||batch.stopId}} · {{batch.orderIds.length}} 单</text></view><text class="warehouse-heading">已选 {{selected.length}} 单</text><view v-for="order in orders" :key="order.id" class="warehouse-card warehouse-choice" :class="{picked:selected.includes(order.id),disabled:!available(order)}" @click="toggle(order)"><text class="warehouse-heading">{{selected.includes(order.id)?'✓ ':''}}{{order.description}}</text><text class="warehouse-muted">订单 {{order.number}}</text><text class="warehouse-muted">{{order.stopName||order.stopId}} · {{slotText({start:order.slotStart,end:order.slotEnd})}}</text><text class="warehouse-muted">{{order.applicantName||order.applicantId}} · {{order.size}}</text><text v-if="!available(order)" class="warehouse-warning">不可选择：订单已占用或与已选订单不在同一组</text></view><BaseButton v-if="candidatesPage*100<candidatesTotal" variant="secondary" :loading="loadingMore" :disabled="submission.pending" @click="more">加载更多已受理订单</BaseButton><StatePanel v-if="!orders.length" title="暂无可组批订单" description="请先受理订单，再创建配送批次。"/><text v-if="notice" class="warehouse-note">{{notice}}</text><text v-if="error" class="warehouse-error">{{error}}</text><BaseButton v-if="editable" :loading="submission.busy" :disabled="!selected.length" @click="save">{{batch?'保存批次成员':'创建配送批次'}}</BaseButton><text v-else class="warehouse-warning">当前批次已锁定或关闭，只能查看成员。</text><text v-if="submission.pending" class="warehouse-note">提交结果尚未确认，请再次点击保存，重试原操作。</text><BaseButton variant="plain" :disabled="submission.pending || submission.busy" @click="load">刷新最新数据</BaseButton></template></view></BusinessPage></template>
<style lang="scss">@import '@/styles/warehouse.scss';</style>
