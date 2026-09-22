<script setup lang="ts">
import {ref} from 'vue'
import {onLoad} from '@dcloudio/uni-app'
import BusinessPage from '@/components/BusinessPage.vue'
import StatePanel from '@/components/StatePanel.vue'
import BaseButton from '@/components/BaseButton.vue'
import {refreshAccess} from '@/features/workspace/navigation'
import {getBookingRules,listSlots} from '@/features/catalog/api'
import {bookingDates,canSelectSlot,slotText,type BookingSlot} from '@/features/catalog/model'
import {useSelectionResult} from '@/features/catalog/selection'
import {errorText} from '@/features/order/model'
const finish=useSelectionResult(),warehouse=ref(''),stop=ref(''),dates=ref<string[]>([]),date=ref(''),items=ref<BookingSlot[]>([]),selected=ref<BookingSlot|null>(null),loading=ref(true),error=ref('')
let generation=0
async function load(){const sequence=++generation;loading.value=true;error.value='';selected.value=null;try{const result=await listSlots(warehouse.value,stop.value,date.value);if(sequence===generation)items.value=result}catch(e){if(sequence===generation)error.value=errorText(e)}finally{if(sequence===generation)loading.value=false}}
async function start(){try{if(!await refreshAccess(false,'worker'))return;dates.value=bookingDates((await getBookingRules(warehouse.value)).bookingDays);date.value=dates.value[0]||'';if(date.value)await load();else loading.value=false}catch(e){error.value=errorText(e);loading.value=false}}
function change(event:{detail:{value:unknown}}){date.value=dates.value[Number(event.detail.value)]||'';void load()}
function confirm(){if(selected.value && canSelectSlot(selected.value))finish(selected.value);else{error.value='该时段已过期，请重新选择';void load()}}
onLoad(q=>{warehouse.value=String(q?.warehouseId||'');stop.value=String(q?.stopId||'');void start()})
</script>
<template><BusinessPage title="预约配送时段" subtitle="选择未来日期和半小时配送时段"><view class="worker-stack"><picker :range="dates" @change="change"><view class="worker-select">{{date || '暂无可预约日期'}}　›</view></picker><StatePanel v-if="loading" state="loading" title="" /><StatePanel v-else-if="error" state="error" title="时段加载失败" :description="error" retry @retry="start"/><template v-else><StatePanel v-if="!items.length" title="当天暂无配送时段" description="请尝试其他日期"/><view class="worker-grid"><button v-for="item in items" :key="item.id" class="worker-select" :class="{'worker-picked':selected?.id===item.id}" :disabled="!canSelectSlot(item)" @click="selected=item"><text>{{slotText(item).slice(11)}}</text><text class="worker-muted">{{canSelectSlot(item)?`剩余 ${item.remaining} 单`:'不可预约'}}</text></button></view></template><text class="worker-muted">预约时段为配送计划，实际配送以仓库安排和车辆进度为准</text><BaseButton :disabled="!selected || loading" @click="confirm">确定时段</BaseButton></view></BusinessPage></template>
<style lang="scss">@import '@/styles/worker.scss';</style>
