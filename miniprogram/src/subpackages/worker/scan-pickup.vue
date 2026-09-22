<script setup lang="ts">
import { ref } from 'vue'
import BusinessPage from '@/components/BusinessPage.vue'
import BaseButton from '@/components/BaseButton.vue'
import StatePanel from '@/components/StatePanel.vue'
import { scanVehicle, openPickup } from '@/features/pickup/api'
import { newIdempotencyKey } from '@/features/common/idempotency'
import type { PickupScan } from '@/features/pickup/model'
const result=ref<PickupScan|null>(null); const busy=ref(false); const error=ref('')
async function scan(){ busy.value=true; error.value=''; try { const r=await uni.scanCode({onlyFromCamera:true}); result.value=await scanVehicle(String(r.result)) } catch(e){ error.value=e instanceof Error?e.message:'扫码失败' } finally{busy.value=false} }
async function open(order:PickupScan['orders'][number]){ busy.value=true; error.value=''; try { const result=await openPickup(order.id,order.version,newIdempotencyKey()); uni.navigateTo({url:`/subpackages/worker/pickup-confirm?orderId=${encodeURIComponent(order.id)}&number=${encodeURIComponent(order.number)}&receiver=${encodeURIComponent(order.receiverName)}&version=${order.version}&request=${encodeURIComponent(result.status)}`}) } catch(e){ error.value=e instanceof Error?e.message:'操作失败' } finally{busy.value=false} }
</script>
<template><BusinessPage title="扫码取货" subtitle="仅显示分配给你的订单"><BaseButton :loading="busy" @click="scan">扫描车辆二维码</BaseButton><StatePanel v-if="error" title="操作失败" :description="error"/><view v-if="result" class="result"><text class="vehicle">{{ result.vehicle.name || result.vehicle.id }}</text><text v-if="!result.orders.length" class="empty">当前车辆没有分配给你的待取货订单</text><view v-for="order in result.orders" :key="order.id" class="order"><view><text>{{ order.number }}</text><text class="muted">{{ order.receiverName }}</text></view><BaseButton :disabled="busy" @click="open(order)">开门并确认取货</BaseButton></view></view></BusinessPage></template>
<style scoped>.result{margin-top:20px}.vehicle{display:block;font-size:20px;font-weight:600}.order{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:16px 0;border-bottom:1px solid #e6eaf0}.order :deep(button){width:150px;min-height:40px;font-size:13px}.muted,.empty{display:block;color:#667387;margin-top:8px}</style>
