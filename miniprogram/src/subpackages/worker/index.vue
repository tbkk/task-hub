<script setup lang="ts">
import {reactive,ref,watch} from 'vue'
import {onLoad,onShow,onUnload} from '@dcloudio/uni-app'
import AppPage from '@/components/AppPage.vue'
import BaseButton from '@/components/BaseButton.vue'
import FormField from '@/components/FormField.vue'
import StatePanel from '@/components/StatePanel.vue'
import {refreshAccess} from '@/features/workspace/navigation'
import {workspace} from '@/features/workspace/store'
import {listWarehouses,getBookingRules} from '@/features/catalog/api'
import {canSelectSlot,slotText,type CatalogStop,type BookingSlot,type BookingRules} from '@/features/catalog/model'
import {emptyDraft,validateOrderDraft,errorText} from '@/features/order/model'
import {Submission} from '@/features/order/submission'
import {createOrder} from '@/features/order/api'
import {ApiRequestError} from '@/services/request'
const draft=reactive(emptyDraft()),rules=ref<BookingRules|null>(null),warehouseName=ref(''),stop=ref<CatalogStop|null>(null),slot=ref<BookingSlot|null>(null),loading=ref(true),loadError=ref(''),error=ref(''),fields=ref<Record<string,string>>({}),submission=reactive(new Submission<typeof draft>())
let secondary=false,loaded=false
watch(draft,()=>{workspace.dirty=Object.entries(draft).some(([k,v])=>k!=='warehouseId' && !!v)},{deep:true})
onUnload(()=>{workspace.dirty=false})
onLoad(q=>{secondary=q?.tab==='secondary';if(secondary)uni.redirectTo({url:'/subpackages/worker/orders'})})
async function load(){loading.value=true;loadError.value='';try{if(!await refreshAccess(false,'worker'))return;const warehouses=await listWarehouses();if(warehouses.length!==1){draft.warehouseId='';rules.value=null;return}draft.warehouseId=warehouses[0].id;warehouseName.value=warehouses[0].name;rules.value=await getBookingRules(draft.warehouseId);loaded=true}catch(e){loadError.value=errorText(e)}finally{loading.value=false}}
onShow(()=>{if(!secondary){if(!loaded)void load();else void refreshAccess(false,'worker')}})
function destination(){if(submission.pending)return;uni.navigateTo({url:`/subpackages/worker/destination?warehouseId=${encodeURIComponent(draft.warehouseId)}`,events:{selected:(value:CatalogStop)=>{if(stop.value?.id!==value.id){slot.value=null;draft.slotId=''}stop.value=value;draft.stopId=value.id;delete fields.value.stopId}}})}
function chooseSlot(){if(!draft.stopId){uni.showToast({title:'请先选择目的地',icon:'none'});return}uni.navigateTo({url:`/subpackages/worker/slot?warehouseId=${encodeURIComponent(draft.warehouseId)}&stopId=${encodeURIComponent(draft.stopId)}`,events:{selected:(value:BookingSlot)=>{slot.value=value;draft.slotId=value.id;delete fields.value.slotId}}})}
function tickets(){if(submission.pending)return;uni.navigateTo({url:'/subpackages/worker/tickets'})}
async function submit(){if(submission.busy || !rules.value)return;error.value='';fields.value=validateOrderDraft(draft,rules.value);if(Object.keys(fields.value).length)return;if(!submission.pending && (!slot.value || !canSelectSlot(slot.value))){draft.slotId='';slot.value=null;fields.value.slotId='请重新选择未来可用时段';return}try{const order=await submission.run({...draft},createOrder);workspace.dirty=false;uni.redirectTo({url:`/subpackages/worker/result?id=${encodeURIComponent(order.id)}`})}catch(e){error.value=errorText(e);if(e instanceof ApiRequestError){fields.value=e.data?.fieldErrors||{};if(e.code===42201){draft.slotId='';slot.value=null}}}}
const inputs=[{key:'description',label:'货物名称及描述',placeholder:'如：维修用轴承 2 箱'},{key:'size',label:'货物大小',placeholder:'如：每箱约 40 × 30 × 25 cm'},{key:'receiverName',label:'接收人姓名',placeholder:'请输入姓名'},{key:'receiverPhone',label:'接收人手机号',placeholder:'请输入接收人的 11 位手机号'},{key:'remark',label:'备注（选填）',placeholder:'补充配送说明'}] as const
</script>
<template><AppPage title="快速申请" subtitle="填写物料与接收信息，仓库受理后安排配送" :tab="0"><view class="worker-stack"><StatePanel v-if="loading" state="loading" title=""/><StatePanel v-else-if="loadError" state="error" title="加载失败" :description="loadError" retry @retry="load"/><StatePanel v-else-if="!draft.warehouseId" title="未配置所属仓库" description="请联系管理员配置所属仓库后申请配送" retry @retry="load"/><template v-else-if="rules"><text class="worker-muted">所属仓库：{{warehouseName}}</text><text class="worker-label">物料信息</text><view v-for="field in inputs" :key="field.key"><FormField :label="field.label" :input-id="field.key"><input :id="field.key" v-model="draft[field.key]" class="worker-input" :placeholder="field.placeholder" :aria-label="field.label" :disabled="submission.pending" :maxlength="-1" @input="delete fields[field.key]" :type="field.key==='receiverPhone'?'number':'text'"/></FormField><text v-if="fields[field.key]" class="worker-error">{{fields[field.key]}}</text></view><text class="worker-muted">接收手机号未匹配账号时仍可提交；本人验证绑定后才能取货。</text><text class="worker-label">配送安排</text><button class="worker-select" :disabled="submission.pending" @click="destination"><view class="worker-row"><text>目的地</text><text class="worker-muted">{{stop?.name || '选择停靠点'}}　›</text></view></button><text v-if="fields.stopId" class="worker-error">{{fields.stopId}}</text><button class="worker-select" :disabled="submission.pending" @click="chooseSlot"><text>预约时段</text><text class="worker-muted">{{slot?slotText(slot):'选择日期和时段'}}　›</text></button><text v-if="fields.slotId" class="worker-error">{{fields.slotId}}</text><text class="worker-muted">预约时段为配送计划，实际配送以仓库安排和车辆进度为准</text><text v-if="error" class="worker-error" role="alert">{{error}}</text><text v-if="submission.pending && !submission.busy" class="worker-note">提交结果未确认。请保持原申请重试，避免重复下单。</text><BaseButton :loading="submission.busy" @click="submit">{{submission.pending?'重试原申请':'提交申请'}}</BaseButton><BaseButton variant="secondary" :disabled="submission.pending" @click="tickets">查看我的工单</BaseButton></template></view></AppPage></template>
<style lang="scss">@import '@/styles/worker.scss';</style>
